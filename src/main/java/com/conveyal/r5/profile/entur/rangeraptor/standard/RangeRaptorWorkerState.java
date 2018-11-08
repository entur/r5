package com.conveyal.r5.profile.entur.rangeraptor.standard;


import com.conveyal.r5.profile.entur.api.StopArrival;
import com.conveyal.r5.profile.entur.api.TripScheduleInfo;
import com.conveyal.r5.profile.entur.util.BitSetIterator;
import com.conveyal.r5.profile.entur.util.DebugState;

import static com.conveyal.r5.profile.entur.util.DebugState.Type.Access;
import static com.conveyal.r5.profile.entur.util.DebugState.Type.Transfer;
import static com.conveyal.r5.profile.entur.util.DebugState.Type.Transit;

/**
 * Tracks the state of a RAPTOR search, specifically the best arrival times at each transit stop at the end of a
 * particular round, along with associated data to reconstruct paths etc.
 * <p>
 * This is grouped into a separate class (rather than just having the fields in the raptor worker class) because we
 * need to make copies of it when doing Monte Carlo frequency searches. While performing the range-raptor search,
 * we keep performing raptor searches at different departure times, stepping back in time, but operating on the same
 * set of states (one for each round). But after each one of those departure time searches, we want to run sub-searches
 * with different randomly selected schedules (the Monte Carlo draws). We don't want those sub-searches to invalidate
 * the states for the ongoing range-raptor search, so we make a protective copy.
 * <p>
 * Note that this represents the entire state of the RAPTOR search for a single round, rather than the state at
 * a particular vertex (transit stop), as is the case with State objects in other search algorithms we have.
 *
 * @author mattwigway
 */
public final class RangeRaptorWorkerState<T extends TripScheduleInfo> implements WorkerState {

    /**
     * @deprecated TODO TGR - Replace with pareto destination check
     */
    @Deprecated
    private static final int MAX_TRIP_DURATION_SECONDS = 20 * 60 * 60; // 20 hours


    /**
     * To debug a particular journey set DEBUG to true and add all visited stops in the debugStops list.
     */
    private final StopStateCollection<T> stops;
    private final StopStateCursor<T> cursor;
    private final int nRounds;
    private int round = 0;
    private int roundMax = -1;

    /** Stop the search when the time excids the max time limit. */
    private int maxTimeLimit;


    /** The best times to reach each stop, whether via a transfer or via transit directly. */
    private final BestTimes bestOverall;

    /** Index to the best times for reaching stops via transit rather than via a transfer from another stop */
    private final BestTimes bestTransit;


    /** create a RaptorState for a network with a particular number of stops, and a given maximum duration */
    RangeRaptorWorkerState(int nRounds, int nStops, StopStateCollection<T> stops) {
        this.nRounds = nRounds;
        this.stops = stops;
        this.cursor = stops.newCursor();

        this.bestOverall = new BestTimes(nStops);
        this.bestTransit = new BestTimes(nStops);
    }

    @Override
    public void gotoNextRound() {
        bestOverall.gotoNextRound();
        bestTransit.gotoNextRound();
        ++round;
        roundMax = Math.max(roundMax, round);
    }

    @Override
    public boolean isNewRoundAvailable() {
        final boolean moreRoundsToGo = round < nRounds-1;
        return moreRoundsToGo && isCurrentRoundUpdated();
    }

    boolean isStopReachedInPreviousRound(int stop) {
        return bestOverall.isReachedLastRound(stop);
    }

    BitSetIterator bestStopsTouchedLastRoundIterator() {
        return bestOverall.stopsReachedLastRound();
    }

    int getMaxNumberOfRounds() {
        return roundMax;
    }

    boolean isStopReachedByTransit(int stop) {
        return bestTransit.isReached(stop);
    }

    @Override
    public BitSetIterator stopsTouchedByTransitCurrentRound() {
        return bestTransit.stopsReachedCurrentRound();
    }

    int bestTimePreviousRound(int stop) {
        return cursor.stop(round-1, stop).time();
    }

    @Override
    public void initNewDepartureForMinute(int departureTime) {
        // TODO TGR - Set max limit to 5 days for now, replace this with a pareto check against the
        // TODO TGR - destination location values.
        maxTimeLimit = departureTime + MAX_TRIP_DURATION_SECONDS;

        // clear all touched stops to avoid constant reëxploration
        bestOverall.clearCurrent();
        bestTransit.clearCurrent();
        round = 0;
    }

    @Override
    public void setInitialTime(StopArrival stopArrival, int fromTime, int boardSlackInSeconds) {
        final int accessDurationInSeconds = stopArrival.durationInSeconds();
        final int stop = stopArrival.stop();
        final int arrivalTime = fromTime + accessDurationInSeconds;

        stops.setInitialTime(round, stop, arrivalTime);
        bestOverall.setTime(stop, accessDurationInSeconds);
        debugStop(Access, round, stop);
    }

    /**
     * Set the time at a transit stop iff it is optimal. This sets both the bestTime and the transitTime
     */
    void transitToStop(int stop, int alightTime, T trip, int boardStop, int boardTime) {
        if (alightTime > maxTimeLimit) {
            return;
        }

        if (bestTransit.updateNewBestTime(stop, alightTime)) {

            // transitTimes upper bounds bestTimes
            final boolean newBestOverall = bestOverall.updateNewBestTime(stop, alightTime);

            stops.transitToStop(round, stop, alightTime, boardStop, boardTime, trip, newBestOverall);

            // skip: transferTimes
            debugStop(Transit, round, stop);
        }
    }

    /**
     * Set the time at a transit stop iff it is optimal. This sets both the bestTime and the nonTransferTime
     */
    @Override
    public void transferToStop(int fromStop, StopArrival toStopArrival) {
        final int toStop = toStopArrival.stop();

        int arrivalTime = bestTransit.time(fromStop) + toStopArrival.durationInSeconds();

        if (arrivalTime > maxTimeLimit) {
            return;
        }
        // transitTimes upper bounds bestTimes so we don't need to update wait time and in-vehicle time here, if we
        // enter this conditional it has already been updated.
        if (bestOverall.updateNewBestTime(toStop, arrivalTime)) {
            stops.transferToStop(round, fromStop, toStopArrival, arrivalTime);

            debugStop(Transfer, round, toStop);
        }
    }

    public void debugStopHeader(String title) {
        DebugState.debugStopHeader(title, "Best     C P | Transit  C P");
    }


    /* private methods */

    private boolean isCurrentRoundUpdated() {
        return !(bestOverall.isCurrentRoundEmpty() && bestTransit.isCurrentRoundEmpty());
    }

    private void debugStop(DebugState.Type type, int round, int stop) {
        if(DebugState.isDebug(stop)) {
            DebugState.debugStop(type, round, stop, cursor.stop(round, stop), bestOverall.toString(stop) + " | " + bestTransit.toString(stop));
        }
    }
}