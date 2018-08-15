package com.conveyal.r5.profile.mcrr;

import java.util.Arrays;
import java.util.List;

import static com.conveyal.r5.profile.mcrr.StopState.NOT_SET;

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
public final class McRaptorStateImpl implements RangeRaptorWorkerState {

    /**
     * To debug a particular journey set DEBUG to true and add all visited stops in the debugStops list.
     */
    private static final boolean DEBUG = StopStateFlyWeight.DEBUG;
    private static final List<Integer> debugStops = Arrays.asList(5757, 32489, 17270, 21469, 22102);

    private final StopStateFlyWeight state;
    private final StopStateFlyWeight.Cursor cursor;
    private final int nRounds;
    private int round = 0;
    private int roundMax = -1;


    /**
     * Earliest possible departure time for the search.
     * RangeRaptor iterate over departure times, but this is the first one.
     */
    private final int earliestDepartureTime;

    /** Maximum duration of trips stored by this RaptorState */
    private final int maxDurationSeconds;

    /** Stop the search when the time excids the max time limit. */
    private int maxTimeLimit;


    /** The best times to reach each stop, whether via a transfer or via transit directly. */
    private final BestTimes bestOveral;

    /** Index to the best times for reaching stops via transit rather than via a transfer from another stop */
    private final BestTimes bestTransit;


    /** create a RaptorState for a network with a particular number of stops, and a given maximum duration */
    public McRaptorStateImpl(int nStops, int nRounds, int maxDurationSeconds, int earliestDepartureTime, StopStateFlyWeight stopState) {
        this.nRounds = nRounds;
        this.state = stopState;
        this.cursor = state.newCursor();

        this.bestOveral = new BestTimes(nStops);
        this.bestTransit = new BestTimes(nStops);

        this.maxDurationSeconds = maxDurationSeconds;
        this.earliestDepartureTime = earliestDepartureTime;
    }

    @Override public void gotoNextRound () {
        bestOveral.gotoNextRound();
        bestTransit.gotoNextRound();
        ++round;
        roundMax = Math.max(roundMax, round);
    }
    @Override public boolean isNewRoundAvailable() {
        final boolean moreRoundsToGo = round < nRounds-1;
        return moreRoundsToGo && isCurrentRoundUpdated();
    }
    @Override public boolean isStopReachedInLastRound(int stop) {
        return bestOveral.isReachedLastRound(stop);
    }

    @Override public BitSetIterator bestStopsTouchedLastRoundIterator() {
        return bestOveral.stopsReachedLastRound();
    }

    @Override
    public int getMaxRound() {
        return roundMax;
    }

    @Override
    public boolean isStopReachedByTransit(int stop) {
        return bestTransit.isReached(stop);
    }

    @Override public BitSetIterator stopsTouchedByTransitCurrentRoundIterator() {
        return bestTransit.stopsReachedCurrentRound();
    }

    @Override public int bestTimePreviousRound(int stop) {
        // TODO TGR
        return state.time(round-1, stop);
        //return bestOveral.timeLastRound(stop);
    }

    @Override public int bestTransitTime(int stop) {
        return bestTransit.time(stop);
    }
    @Override public void initNewDepatureForMinute(int roundDepartureTime) {
        //this.departureTime = departureTime;
        maxTimeLimit = roundDepartureTime + maxDurationSeconds;
        // clear all touched stops to avoid constant reëxploration
        bestOveral.clearCurrent();
        bestTransit.clearCurrent();
        round = 0;
    }

    @Override public void setInitialTime(int stop, int time) {
        state.setInitalTime(round, stop, time);
        bestOveral.setTime(stop, time);
        debugListedStops("init", round, stop);
    }

    /**
     * Set the time at a transit stop iff it is optimal. This sets both the bestTime and the transitTime
     */
    @Override public void transitToStop(int stop, int alightTime, int pattern, int trip, int boardStop, int boardTime) {
        if (alightTime > maxTimeLimit) {
            return;
        }

        if (bestTransit.updateNewBestTime(stop, alightTime)) {

            // transitTimes upper bounds bestTimes
            final boolean newBestOveral = bestOveral.updateNewBestTime(stop, alightTime);

            state.transitToStop(round, stop, alightTime, pattern, boardStop, trip, boardTime, newBestOveral);

            // skip: transferTimes
            debugListedStops("transit to stop", round, stop);
        }
    }

    /**
     * Set the time at a transit stop iff it is optimal. This sets both the bestTime and the nonTransferTime
     */
    @Override public void transferToStop(int stop, int time, int fromStop, int transferTime) {

        if (time > maxTimeLimit) {
            return;
        }
        // transitTimes upper bounds bestTimes so we don't need to update wait time and in-vehicle time here, if we
        // enter this conditional it has already been updated.
        if (bestOveral.updateNewBestTime(stop, time)) {

            state.transferToStop(round, stop, time, fromStop, transferTime);

            debugListedStops("transfer to stop", round, stop);
        }
    }

    @Override public int getPatternIndexForPreviousRound(int stop) {
        StopState state = cursor.stop(round-1, stop);
        int boardStop = state.boardStop();
        return boardStop == NOT_SET
                ? state.previousPattern()
                : cursor.stop(round-1, boardStop).previousPattern();
    }


    /* private methods */


    static void debugStopHeader(String title) {
        if(!DEBUG) return;
        String[] headers = StopStateFlyWeight.stopToStringHeaders();
        System.err.printf("  S %-24s  -------- BEST OVERALL -------   %s%n", title, headers[0]);
        System.err.printf("  S %-24s  Rnd  Stop  Time C L Trans C L   %s%n", "", headers[1]);
    }

    private void debugStop(String descr, int round, int stop) {
        if(!DEBUG) return;

        if(round < 0 || stop < 1) {
            System.err.printf("  S %-24s  %2d %6d  STOP DOES NOT EXIST!%n", descr, round, stop);
            return;
        }

        System.err.printf("  S %-24s  %2d %6d %s %s   %s%n",
                descr,
                round,
                stop,
                bestOveral.toString(stop),
                bestTransit.toString(stop),
                state.stopToString(round, stop)
        );
    }

    private boolean isCurrentRoundUpdated() {
        return !(bestOveral.isCurrentRoundEmpty() && bestTransit.isCurrentRoundEmpty());
    }

    private void debugListedStops(String descr, int round, int stop) {
        if (DEBUG && debugStops.contains(stop)) debugStop(descr, round, stop);
    }
}