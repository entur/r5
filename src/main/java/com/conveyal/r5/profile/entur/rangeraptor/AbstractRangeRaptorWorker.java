package com.conveyal.r5.profile.entur.rangeraptor;

import com.conveyal.r5.profile.entur.api.path.Path;
import com.conveyal.r5.profile.entur.api.transit.IntIterator;
import com.conveyal.r5.profile.entur.api.transit.TransferLeg;
import com.conveyal.r5.profile.entur.api.transit.TransitDataProvider;
import com.conveyal.r5.profile.entur.api.transit.TripPatternInfo;
import com.conveyal.r5.profile.entur.api.transit.TripScheduleInfo;
import com.conveyal.r5.profile.entur.api.view.Worker;
import com.conveyal.r5.profile.entur.rangeraptor.debug.WorkerPerformanceTimers;
import com.conveyal.r5.profile.entur.rangeraptor.multicriteria.McRangeRaptorWorkerState;
import com.conveyal.r5.profile.entur.rangeraptor.transit.RoundTracker;
import com.conveyal.r5.profile.entur.rangeraptor.transit.SearchContext;
import com.conveyal.r5.profile.entur.rangeraptor.transit.TransitCalculator;
import com.conveyal.r5.profile.entur.rangeraptor.transit.TripScheduleBoardSearch;
import com.conveyal.r5.profile.entur.rangeraptor.transit.TripScheduleSearch;
import com.conveyal.r5.profile.entur.rangeraptor.workerlifecycle.LifeCycleEventPublisher;
import com.conveyal.r5.profile.entur.util.AvgTimer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;


/**
 * The algorithm used herein is described in
 * <p>
 * Conway, Matthew Wigginton, Andrew Byrd, and Marco van der Linden. “Evidence-Based Transit and Land Use Sketch Planning
 * Using Interactive Accessibility Methods on Combined Schedule and Headway-Based Networks.” Transportation Research
 * Record 2653 (2017). doi:10.3141/2653-06.
 * <p>
 * Delling, Daniel, Thomas Pajor, and Renato Werneck. “Round-Based Public Transit Routing,” January 1, 2012.
 * http://research.microsoft.com/pubs/156567/raptor_alenex.pdf.
 * <p>
 * This version do support the following features:
 * <ul>
 *     <li>Raptor (R)
 *     <li>Range Raptor (RR)
 *     <li>Multi-criteria pareto optimal Range Raptor (McRR)
 *     <li>Reverse search in combination with R and RR
 * </ul>
 * This version do NOT support the following features:
 * <ul>
 *     <li>Frequency routes, supported by the original code using Monte Carlo methods (generating randomized schedules)
 * </ul>
 * <p>
 * This class originated as a rewrite of Conveyals RAPTOR code: https://github.com/conveyal/r5.
 *
 * @param <T> The TripSchedule type defined by the user of the range raptor API.
 */
@SuppressWarnings("Duplicates")
public abstract class AbstractRangeRaptorWorker<T extends TripScheduleInfo, S extends WorkerState<T>> implements Worker<T> {

    private static final AvgTimer STUFF1 = AvgTimer.timerMicroSec("AbRRW Stuff 1");
    private static final AvgTimer STUFF2 = AvgTimer.timerMicroSec("AbRRW Stuff 2");
    private static final AvgTimer STUFF3 = AvgTimer.timerMicroSec("AbRRW Stuff 3");

    /**
     * The RangeRaptor state - we delegate keeping track of state to the state object,
     * this allow the worker implementation to focus on the algorithm, while
     * the state keep track of the result.
     * <p/>
     * This also allow us to try out different strategies for storing the result in memory.
     * For a long time we had a state witch stored all data as int arrays in addition to the
     * current object-oriented approach. There were no performance differences(=> GC is not
     * the bottle neck), so we dropped the integer array implementation.
     */
    protected final S state;

    /**
     * The round tracker keep track for the current Raptor round, and abort the search if the
     * round max limit is reached.
     */
    private final RoundTracker roundTracker;

    private final TransitDataProvider<T> transit;

    protected final TransitCalculator calculator;

    private final WorkerPerformanceTimers timers;

    private final Collection<TransferLeg> accessLegs;

    private final BitSet stopsFilter;

    private boolean matchBoardingAlightExactInFirstRound;

    /**
     * The life cycle is used to publish life cycle events to everyone who
     * listen.
     */
    private final LifeCycleEventPublisher lifeCycle;

    boolean supportMultiThreads = false;


    public AbstractRangeRaptorWorker(
            SearchContext<T> context,
            S state
    ) {
        this.state = state;
        this.transit = context.transit();
        this.calculator = context.calculator();
        this.timers = context.timers();
        this.accessLegs = context.accessLegs();
        // We do a cast here to avoid exposing the round tracker  and the life cycle publisher to "everyone"
        // by providing access to it in the context.
        this.roundTracker = (RoundTracker) context.roundProvider();
        this.stopsFilter =  context.searchParams().stopFilter();
        this.lifeCycle = context.createLifeCyclePublisher();
        this.matchBoardingAlightExactInFirstRound = !context.searchParams().waitAtBeginningEnabled();

        supportMultiThreads = context.isMultiThreaded() && (state instanceof McRangeRaptorWorkerState);
    }

    /**
     * For each iteration (minute), calculate the minimum travel time to each transit stop in seconds.
     * <p/>
     * Run the scheduled search, round 0 is the street search
     * <p/>
     * We are using the Range-RAPTOR extension described in Delling, Daniel, Thomas Pajor, and Renato Werneck.
     * “Round-Based Public Transit Routing,” January 1, 2012. http://research.microsoft.com/pubs/156567/raptor_alenex.pdf.
     *
     * @return a unique set of paths
     */
    @Override
    final public Collection<Path<T>> route() {
        timerRoute().time(() -> {
            timerSetup(transit::setup);

            // The main outer loop iterates backward over all minutes in the departure times window.
            // Ergo, we re-use the arrival times found in searches that have already occurred that
            // depart later, because the arrival time given departure at time t is upper-bounded by
            // the arrival time given departure at minute t + 1.
            final IntIterator it = calculator.rangeRaptorMinutes();
            while (it.hasNext()) {
                // Run the raptor search for this particular iteration departure time
                timerRouteByMinute(() -> runRaptorForMinute(it.next()));
            }
        });
        return state.extractPaths();
    }

    protected TransitCalculator calculator() {
        return calculator;
    }

    protected abstract void prepareTransitForRoundAndPattern(TripPatternInfo<T> pattern, TripScheduleSearch<T> tripSearch);

    protected abstract void performTransitForRoundAndPatternAtStop(int stopPositionInPattern);

    protected boolean allowStopVisit(int stop) {
        return stopsFilter == null || stopsFilter.get(stop);
    }

    /**
     * Iterate over given pattern and calculate transit for each stop.
     * <p/>
     * This is protected to allow reverse search to override and step backwards.
     */
    private void performTransitForRoundAndEachStopInPattern(final TripPatternInfo<T> pattern) {
        IntIterator it = calculator.patternStopIterator(pattern.numberOfStopsInPattern());
        while (it.hasNext()) {
            performTransitForRoundAndPatternAtStop(it.next());
        }
    }

    /**
     * Create a trip search using {@link TripScheduleBoardSearch}.
     * <p/>
     * This is protected to allow reverse search to override and create a alight search instead.
     */
    protected TripScheduleSearch<T> createTripSearch(TripPatternInfo<T> pattern) {
        if(matchBoardingAlightExactInFirstRound && roundTracker.round() == 1) {
            return calculator.createExactTripSearch(pattern, this::skipTripSchedule);
        }
        else {
            return calculator.createTripSearch(pattern, this::skipTripSchedule);
        }
    }

    /**
     * Skip trips NOT running on the day of the search and skip frequency trips
     */
    private boolean skipTripSchedule(T trip) {
        return !transit.isTripScheduleInService(trip);
    }

    /**
     * Perform one minute of a RAPTOR search.
     *
     * @param iterationDepartureTime When this search departs.
     */
    private void runRaptorForMinute(int iterationDepartureTime) {
        lifeCycle.setupIteration(iterationDepartureTime);

        doTransfersForAccessLegs(iterationDepartureTime);

        while (hasMoreRounds()) {
            lifeCycle.prepareForNextRound();

            // NB since we have transfer limiting not bothering to cut off search when there are no more transfers
            // as that will be rare and complicates the code
            if(supportMultiThreads) {
                timerByMinuteScheduleSearch().time(this::findAllTransitForRoundMT);
            }
            else {
                timerByMinuteScheduleSearch().time(this::findAllTransitForRound);
            }

            timerByMinuteTransfers().time(this::transfersForRound);

            lifeCycle.roundComplete(state.isDestinationReachedInCurrentRound());
        }

        // This state is repeatedly modified as the outer loop progresses over departure minutes.
        // We have to be careful here, the next iteration will modify the state, so we need to make
        // protective copies of any information we want to retain.
        lifeCycle.iterationComplete();
    }


    /**
     * Check if the RangeRaptor should continue with a new round.
     */
    private boolean hasMoreRounds() {
        return roundTracker.hasMoreRounds() && state.isNewRoundAvailable();
    }

    /**
     * Set the departure time in the scheduled search to the given departure time,
     * and prepare for the scheduled search at the next-earlier minute.
     * <p/>
     * This method is protected to allow reverce search to override it.
     */
    private void doTransfersForAccessLegs(int iterationDepartureTime) {
        for (TransferLeg it : accessLegs) {
            state.setInitialTimeForIteration(it, iterationDepartureTime);
        }
    }

    /**
     * Perform a scheduled search
     */
    private void findAllTransitForRound() {
        IntIterator stops = state.stopsTouchedPreviousRound();
        Iterator<? extends TripPatternInfo<T>> patternIterator = transit.patternIterator(stops);

        while (patternIterator.hasNext()) {
            TripPatternInfo<T> pattern = patternIterator.next();
            TripScheduleSearch<T> tripSearch = createTripSearch(pattern);

            prepareTransitForRoundAndPattern(pattern, tripSearch);

            performTransitForRoundAndEachStopInPattern(pattern);
        }
        lifeCycle.transitsForRoundComplete();
    }

    private void findAllTransitForRoundMT() {
        STUFF1.start();

        IntIterator stops = state.stopsTouchedPreviousRound();
        Iterator<? extends TripPatternInfo<T>> patternIterator = transit.patternIterator(stops);
        List<List<TripPatternInfo<T>>> patterns = new ArrayList<>(
                Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>())
        );


        int i = 0;

        while (patternIterator.hasNext()) {
            patterns.get(i).add(patternIterator.next());
            i = i==3 ? 0 : i+1;
        }
        STUFF1.stop();
        STUFF2.start();
        performTransitMultiThreaded(patterns);
        STUFF2.stop();


        STUFF3.start();
        lifeCycle.transitsForRoundComplete();
        STUFF3.stop();
    }

    protected void performTransitMultiThreaded(List<List<TripPatternInfo<T>>> patterns) {

    }


    private void transfersForRound() {
        IntIterator it = state.stopsTouchedByTransitCurrentRound();

        while (it.hasNext()) {
            final int fromStop = it.next();
            // no need to consider loop transfers, since we don't mark patterns here any more
            // loop transfers are already included by virtue of those stops having been reached
            state.transferToStops(fromStop, transit.getTransfers(fromStop));
        }
        lifeCycle.transfersForRoundComplete();
    }

    // Track time spent, measure performance
    // TODO TGR - Replace by performance tests
    private void timerSetup(Runnable setup) { timers.timerSetup(setup); }
    private AvgTimer timerRoute() { return timers.timerRoute(); }
    private void timerRouteByMinute(Runnable routeByMinute) { timers.timerRouteByMinute(routeByMinute); }
    private AvgTimer timerByMinuteScheduleSearch() { return timers.timerByMinuteScheduleSearch(); }
    private AvgTimer timerByMinuteTransfers() { return timers.timerByMinuteTransfers(); }
}
