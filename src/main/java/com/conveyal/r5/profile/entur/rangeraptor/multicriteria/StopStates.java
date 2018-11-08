package com.conveyal.r5.profile.entur.rangeraptor.multicriteria;


import com.conveyal.r5.profile.entur.api.StopArrival;
import com.conveyal.r5.profile.entur.api.TripScheduleInfo;
import com.conveyal.r5.profile.entur.rangeraptor.multicriteria.arrivals.McAccessStopArrivalState;
import com.conveyal.r5.profile.entur.rangeraptor.multicriteria.arrivals.McStopArrivalState;
import com.conveyal.r5.profile.entur.rangeraptor.multicriteria.arrivals.McTransferStopArrivalState;
import com.conveyal.r5.profile.entur.rangeraptor.multicriteria.arrivals.McTransitStopArrivalState;


import static java.util.Collections.emptyList;



final class StopStates<T extends TripScheduleInfo> {

    private final StopStateParetoSet<T>[] stops;

        /**
         * Set the time at a transit index iff it is optimal. This sets both the best time and the transfer time
         */
    StopStates(int stops) {
        this.stops = (StopStateParetoSet<T>[]) new StopStateParetoSet[stops];
    }

    void setInitialTime(StopArrival stopArrival, int fromTime, int boardSlackInSeconds) {
        final int stop = stopArrival.stop();
        findOrCreateSet(stop).add(
                new McAccessStopArrivalState<T>(stopArrival, fromTime, boardSlackInSeconds)
        );
    }

    boolean transitToStop(McStopArrivalState<T> previous, int round, int stop, int time, T trip, int boardTime) {
        McStopArrivalState<T> state = new McTransitStopArrivalState<>(previous, round, stop, time, boardTime, trip);
        return findOrCreateSet(stop).add(state);
    }

    boolean transferToStop(McStopArrivalState<T> previous, int round, StopArrival stopArrival, int arrivalTime) {
        return findOrCreateSet(stopArrival.stop()).add(new McTransferStopArrivalState<>(previous, round, stopArrival, arrivalTime));
    }

    Iterable<? extends McStopArrivalState<T>> listArrivedByTransit(int round, int stop) {
        StopStateParetoSet<T> it = stops[stop];
        return it == null ? emptyList() : it.list(s -> s.round() == round && s.arrivedByTransit());
    }

    Iterable<? extends McStopArrivalState<T>> list(int round, int stop) {
        StopStateParetoSet it = stops[stop];
        return it == null ? emptyList() : it.listRound(round);
    }

    Iterable<? extends McStopArrivalState<T>> listAll(int stop) {
        StopStateParetoSet it = stops[stop];
        return it == null ? emptyList() : it;
    }

    private StopStateParetoSet<T> findOrCreateSet(final int stop) {
        if(stops[stop] == null) {
            stops[stop] = createState();
        }
        return stops[stop];
    }

    static <T extends TripScheduleInfo> StopStateParetoSet<T> createState() {
        return new StopStateParetoSet<>(McStopArrivalState.PARETO_FUNCTION);
    }

    void debugStateInfo() {
        long total = 0;
        long totalMemUsed = 0;
        long numOfStops = 0;
        int max = 0;

        for (StopStateParetoSet stop : stops) {
            if(stop != null) {
                ++numOfStops;
                total += stop.size();
                max = Math.max(stop.size(), max);
                totalMemUsed += stop.memUsed();
            }
        }
        double avg = ((double)total) / numOfStops;
        double avgMem = ((double)totalMemUsed) / numOfStops;

        System.out.printf(
                "%n  => Stop arrivals(McState obj): Avg: %.1f  max: %d  total: %d'  avg.mem: %.1f  tot.mem: %d'  #stops: %d'  tot#stops: %d' %n%n",
                avg, max, total/1000, avgMem, totalMemUsed/1000, numOfStops/1000, stops.length/1000
        );
    }
}
