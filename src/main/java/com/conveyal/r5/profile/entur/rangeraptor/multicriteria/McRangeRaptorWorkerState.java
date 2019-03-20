package com.conveyal.r5.profile.entur.rangeraptor.multicriteria;

import com.conveyal.r5.profile.entur.api.path.Path;
import com.conveyal.r5.profile.entur.api.transit.IntIterator;
import com.conveyal.r5.profile.entur.api.transit.TransferLeg;
import com.conveyal.r5.profile.entur.api.transit.TripScheduleInfo;
import com.conveyal.r5.profile.entur.rangeraptor.WorkerLifeCycle;
import com.conveyal.r5.profile.entur.rangeraptor.WorkerState;
import com.conveyal.r5.profile.entur.rangeraptor.multicriteria.arrivals.AbstractStopArrival;
import com.conveyal.r5.profile.entur.rangeraptor.multicriteria.arrivals.AccessStopArrival;
import com.conveyal.r5.profile.entur.rangeraptor.multicriteria.arrivals.TransferStopArrival;
import com.conveyal.r5.profile.entur.rangeraptor.multicriteria.arrivals.TransitStopArrival;
import com.conveyal.r5.profile.entur.rangeraptor.multicriteria.heuristic.HeuristicsProvider;
import com.conveyal.r5.profile.entur.rangeraptor.path.DestinationArrivalPaths;
import com.conveyal.r5.profile.entur.rangeraptor.transit.CostCalculator;
import com.conveyal.r5.profile.entur.rangeraptor.transit.TransitCalculator;
import com.conveyal.r5.profile.entur.util.AvgTimer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;


/**
 * Tracks the state of a RAPTOR search, specifically the best arrival times at each transit stop at the end of a
 * particular round, along with associated data to reconstruct paths etc.
 * <p/>
 * This is grouped into a separate class (rather than just having the fields in the raptor worker class) because we
 * want the Algorithm to be as clean as possible and to be able to swap the state implementation - try out and
 * experiment with different state implementations.
 *
 * @param <T> The TripSchedule type defined by the user of the range raptor API.
 */
final public class McRangeRaptorWorkerState<T extends TripScheduleInfo> implements WorkerState<T> {

    private final Stops<T> stops;
    private final DestinationArrivalPaths<T> paths;
    private final HeuristicsProvider<T> heuristics;
    private final CostCalculator costCalculator;
    private final TransitCalculator transitCalculator;

    private final ExecutorService threadPool;

    private final List<List<AbstractStopArrival<T>>> arrivalsCaches = new ArrayList<>(
            Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>())
    );
    private final List<List<AbstractStopArrival<T>>> arrivalsTemps = new ArrayList<>(
            Arrays.asList(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>())
    );



    /**
     * create a RaptorState for a network with a particular number of stops, and a given maximum duration
     */
    public McRangeRaptorWorkerState(
            Stops<T> stops,
            DestinationArrivalPaths<T> paths,
            HeuristicsProvider<T> heuristics,
            CostCalculator costCalculator,
            TransitCalculator transitCalculator,
            ExecutorService threadPool,
            WorkerLifeCycle lifeCycle

    ) {
        this.stops = stops;
        this.paths = paths;
        this.heuristics = heuristics;
        this.costCalculator = costCalculator;
        this.transitCalculator = transitCalculator;
        this.threadPool = threadPool;

        // Attach to the RR life cycle
        lifeCycle.onSetupIteration((ignore) -> setupIteration());
        lifeCycle.onTransitsForRoundComplete(this::transitsForRoundComplete);
        lifeCycle.onTransfersForRoundComplete(this::transfersForRoundComplete);
    }

    /*
     The below methods are ordered after the sequence they naturally appear in the algorithm, also
     private life-cycle callbacks are listed here (not in the private method section).
    */

    // This method is private, but is part of Worker life cycle
    private void setupIteration() {
        arrivalsCaches.forEach(List::clear);
        // clear all touched stops to avoid constant rexploration
        stops.clearTouchedStopsAndSetStopMarkers();
    }

    @Override
    public void setInitialTimeForIteration(TransferLeg accessLeg, int iterationDepartureTime) {
        addStopArrival(
                new AccessStopArrival<>(
                        accessLeg.stop(),
                        iterationDepartureTime,
                        accessLeg.durationInSeconds(),
                        costCalculator.walkCost(accessLeg.durationInSeconds()),
                        transitCalculator
                )
        );
    }

    @Override
    public boolean isNewRoundAvailable() {
        return stops.updateExist();
    }

    @Override
    public IntIterator stopsTouchedPreviousRound() {
        return stops.stopsTouchedIterator();
    }

    @Override
    public IntIterator stopsTouchedByTransitCurrentRound() {
        return stops.stopsTouchedIterator();
    }

    Iterable<? extends AbstractStopArrival<T>> listStopArrivalsPreviousRound(int stop) {
        return stops.listArrivalsAfterMarker(stop);
    }

    /**
     * Set the time at a transit stop iff it is optimal.
     */
    void transitToStop(AbstractStopArrival<T> previousStopArrival, int stop, int alightTime, int boardTime, T trip, int thread) {
        if (exceedsTimeLimit(alightTime)) {
            return;
        }
        int cost = costCalculator.transitArrivalCost(previousStopArrival.arrivalTime(), boardTime, alightTime);
        int duration = travelDuration(previousStopArrival, boardTime, alightTime);
        arrivalsCaches.get(thread).add(new TransitStopArrival<>(previousStopArrival, stop, alightTime, boardTime, trip, duration, cost));
    }

    /**
     * Set the time at a transit stops iff it is optimal.
     */
    @Override
    public void transferToStops(int fromStop, Iterator<? extends TransferLeg> transfers) {
        Iterable<? extends AbstractStopArrival<T>> fromArrivals = stops.listArrivalsAfterMarker(fromStop);

        while (transfers.hasNext()) {
            transferToStop(fromArrivals, transfers.next());
        }
    }

    private static final AvgTimer TIMER_1 = AvgTimer.timerMicroSec("McS:stops.clearTouchedStopsAndSetStopMarkers");
    private static final AvgTimer TIMER_2 = AvgTimer.timerMicroSec("McS:commitCachedArrivals");

    // This method is private, but is part of Worker life cycle
    private void transitsForRoundComplete() {
        TIMER_1.start();
        stops.clearTouchedStopsAndSetStopMarkers();
        TIMER_1.stop();
        TIMER_2.start();
        commitCachedArrivals();
        TIMER_2.stop();
    }

    // This method is private, but is part of Worker life cycle
    private void transfersForRoundComplete() {
        commitCachedArrivals();
    }

    @Override
    public Collection<Path<T>> extractPaths() {
        stops.debugStateInfo();
        return paths.listPaths();
    }

    @Override
    public boolean isDestinationReachedInCurrentRound() {
        return paths.isReachedCurrentRound();
    }


    /* private methods */


    private void transferToStop(Iterable<? extends AbstractStopArrival<T>> fromArrivals, TransferLeg transfer) {
        final int transferTimeInSeconds = transfer.durationInSeconds();

        for (AbstractStopArrival<T> it : fromArrivals) {
            int arrivalTime = it.arrivalTime() + transferTimeInSeconds;

            if (!exceedsTimeLimit(arrivalTime)) {
                int cost = costCalculator.walkCost(transferTimeInSeconds);
                arrivalsCaches.get(0).add(new TransferStopArrival<>(it, transfer, arrivalTime, cost));
            }
        }
    }

    private void commitCachedArrivals() {
        if(threadPool == null) {
            arrivalsCaches.forEach(c -> c.forEach(stops::addStopArrival));
        }
        else {
            arrivalsCaches.forEach(c -> c.forEach(it -> arrivalsTemps.get(it.stop() % 4).add(it)));

            List<Future<?>> res = new ArrayList<>();
            for (List<AbstractStopArrival<T>> it : arrivalsTemps) {
                if(!it.isEmpty()) {
                    res.add(threadPool.submit(addArrivals(it)));
                }
            }
            res.forEach(it -> {
                try {
                    if(it != null) {
                        it.get();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            arrivalsTemps.forEach(List::clear);
        }
        arrivalsCaches.forEach(List::clear);
    }

    private Runnable addArrivals(List<AbstractStopArrival<T>> list) {
        return () -> {
            try {
                for (AbstractStopArrival<T> arrival : list) {
                    stops.addStopArrival(arrival);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        };
    }

    private void addStopArrival(AbstractStopArrival<T> arrival) {
        if (heuristics.rejectDestinationArrivalBasedOnHeuristic(arrival)) {
            return;
        }
        stops.addStopArrival(arrival);
    }

    private boolean exceedsTimeLimit(int time) {
        return transitCalculator.exceedsTimeLimit(time);
    }

    private int travelDuration(AbstractStopArrival<T> prev, int boardTime, int alightTime) {
        if (prev.arrivedByAccessLeg()) {
            return transitCalculator.addBoardSlack(prev.travelDuration()) + alightTime - boardTime;
        } else {
            return prev.travelDuration() + alightTime - prev.arrivalTime();
        }
    }
}
