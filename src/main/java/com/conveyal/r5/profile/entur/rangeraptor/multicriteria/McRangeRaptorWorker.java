package com.conveyal.r5.profile.entur.rangeraptor.multicriteria;

import com.conveyal.r5.profile.entur.api.transit.IntIterator;
import com.conveyal.r5.profile.entur.api.transit.TripPatternInfo;
import com.conveyal.r5.profile.entur.api.transit.TripScheduleInfo;
import com.conveyal.r5.profile.entur.rangeraptor.AbstractRangeRaptorWorker;
import com.conveyal.r5.profile.entur.rangeraptor.multicriteria.arrivals.AbstractStopArrival;
import com.conveyal.r5.profile.entur.rangeraptor.transit.SearchContext;
import com.conveyal.r5.profile.entur.rangeraptor.transit.TripScheduleSearch;
import com.conveyal.r5.profile.entur.util.AvgTimer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * The purpose of this class is to implement the multi-criteria specific functonallity of
 * the worker.
 *
 * @param <T> The TripSchedule type defined by the user of the range raptor API.
 */
public final class McRangeRaptorWorker<T extends TripScheduleInfo> extends AbstractRangeRaptorWorker<T, McRangeRaptorWorkerState<T>> {

    private static final AvgTimer TRANS_TIMER = AvgTimer.timerMicroSec("Trans stuff");

    private final ExecutorService threadPool;
    private TripPatternInfo<T> pattern;
    private TripScheduleSearch<T> tripSearch;
    //private LinkedBlockingQueue<Integer> tokens = new LinkedBlockingQueue<>();


    public McRangeRaptorWorker(SearchContext<T> context, McRangeRaptorWorkerState<T> state) {
        super(context, state);
        this.threadPool = context.threadPool();
        //this.tokens.addAll(Arrays.asList(0, 1, 2, 3));
    }

    @Override
    protected void performTransitMultiThreaded(List<List<TripPatternInfo<T>>> patterns) {
        try {
            TRANS_TIMER.start();
            List<Future<?>> res = new ArrayList<>();
            int i = 0;
            for (List<TripPatternInfo<T>> it : patterns) {
                res.add(threadPool.submit(callablePerformTransit(it, i)));
                ++i;
            }
            for (Future<?> it : res) { it.get(); }
            TRANS_TIMER.stop();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private Runnable callablePerformTransit(final List<TripPatternInfo<T>> patterns, final int thread) {
        return () -> {
            for (TripPatternInfo<T> p : patterns) {
                TripScheduleSearch<T> tripSearch = createTripSearch(p);

                prepareTransitForRoundAndPattern(p, tripSearch);

                IntIterator it = calculator.patternStopIterator(p.numberOfStopsInPattern());
                while (it.hasNext()) {
                    performTransitForRoundAndPatternAtStop(p, tripSearch, it.next(), thread);
                }
            }
        };
    }

    @Override
    protected final void prepareTransitForRoundAndPattern(TripPatternInfo<T> pattern, TripScheduleSearch<T> tripSearch) {
        this.pattern = pattern;
        this.tripSearch = tripSearch;
    }

    /**
     * Perform a scheduled search
     */
    @Override
    protected final void performTransitForRoundAndPatternAtStop(int boardStopPos) {
        final int nPatternStops = pattern.numberOfStopsInPattern();
        int boardStopIndex = pattern.stopIndex(boardStopPos);

        for (AbstractStopArrival<T> boardStop : state.listStopArrivalsPreviousRound(boardStopIndex)) {

            int earliestBoardTime = calculator().earliestBoardTime(boardStop.arrivalTime());
            boolean found = tripSearch.search(earliestBoardTime, boardStopPos);

            if (found) {
                T trip = tripSearch.getCandidateTrip();
                IntIterator patternStops = calculator().patternStopIterator(boardStopPos, nPatternStops);

                while (patternStops.hasNext()) {
                    int alightStopPos = patternStops.next();
                    int alightStopIndex = pattern.stopIndex(alightStopPos);

                    if (allowStopVisit(alightStopIndex)) {
                        state.transitToStop(
                                boardStop,
                                alightStopIndex,
                                trip.arrival(alightStopPos),
                                trip.departure(boardStopPos),
                                trip,
                                0
                        );
                    }
                }
            }
        }
    }

    private void performTransitForRoundAndPatternAtStop(
            TripPatternInfo<T> pattern, TripScheduleSearch<T> tripSearch, int boardStopPos, int thread
    ) {

        final int nPatternStops = pattern.numberOfStopsInPattern();
        int boardStopIndex = pattern.stopIndex(boardStopPos);

        for (AbstractStopArrival<T> boardStop : state.listStopArrivalsPreviousRound(boardStopIndex)) {

            int earliestBoardTime = calculator().earliestBoardTime(boardStop.arrivalTime());
            boolean found = tripSearch.search(earliestBoardTime, boardStopPos);

            if (found) {
                T trip = tripSearch.getCandidateTrip();
                IntIterator patternStops = calculator().patternStopIterator(boardStopPos, nPatternStops);

                while (patternStops.hasNext()) {
                    int alightStopPos = patternStops.next();
                    int alightStopIndex = pattern.stopIndex(alightStopPos);

                    if (allowStopVisit(alightStopIndex)) {
                        state.transitToStop(
                                boardStop,
                                alightStopIndex,
                                trip.arrival(alightStopPos),
                                trip.departure(boardStopPos),
                                trip,
                                thread
                        );
                    }
                }
            }
        }
    }

}
