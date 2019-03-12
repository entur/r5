package com.conveyal.r5.profile.entur.rangeraptor.standard.heuristics;

import com.conveyal.r5.profile.entur.api.transit.TransferLeg;
import com.conveyal.r5.profile.entur.rangeraptor.WorkerLifeCycle;
import com.conveyal.r5.profile.entur.rangeraptor.standard.besttimes.BestTimes;
import com.conveyal.r5.profile.entur.rangeraptor.standard.transfers.BestNumberOfTransfers;
import com.conveyal.r5.profile.entur.rangeraptor.transit.TransitCalculator;
import com.conveyal.r5.profile.entur.rangeraptor.view.Heuristics;
import com.conveyal.r5.profile.entur.util.IntUtils;

import java.util.Collection;
import java.util.function.IntUnaryOperator;


/**
 * The responsibility of this class is to play the {@link Heuristics} role.
 * It wrap the internal state, and transform the internal model to
 * provide the needed functionality.
 */
public class HeuristicsAdapter implements Heuristics {
    private static final int NOT_SET = Integer.MAX_VALUE;

    private int originDepartureTime = -1;
    private final BestTimes bestTimes;
    private final BestNumberOfTransfers bestNumberOfTransfers;
    private final Collection<TransferLeg> transferLegs;
    private final TransitCalculator calculator;

    private int minJourneyTravelDuration = NOT_SET;
    private int minJourneyNumOfTransfers = NOT_SET;

    public HeuristicsAdapter(
            BestTimes bestTimes,
            BestNumberOfTransfers bestNumberOfTransfers,
            Collection<TransferLeg> transferLegs,
            TransitCalculator calculator,
            WorkerLifeCycle lifeCycle
    ) {
        this.bestTimes = bestTimes;
        this.bestNumberOfTransfers = bestNumberOfTransfers;
        this.transferLegs = transferLegs;
        this.calculator = calculator;
        lifeCycle.onSetupIteration(this::setUpIteration);
    }

    private void setUpIteration(int departureTime) {
        if (this.originDepartureTime > 0) {
            throw new IllegalStateException(
                    "You should only run one iteration to calculate heuristics, this is because we use " +
                    "the origin departure time to calculate the travel duration at the end of the search."
            );
        }
        this.originDepartureTime = departureTime;
    }

    @Override
    public boolean reached(int stop) {
        return bestTimes.isStopReached(stop);
    }

    @Override
    public int bestTravelDuration(int stop) {
        return calculator.duration(originDepartureTime, bestTimes.time(stop));
    }

    @Override
    public int[] bestTravelDurationToIntArray(int unreached) {
        return toIntArray(unreached, size(), this::bestTravelDuration);
    }

    @Override
    public int bestNumOfTransfers(int stop) {
        return bestNumberOfTransfers.minNumberOfTransfers(stop);
    }

    @Override
    public int[] bestNumOfTransfersToIntArray(int unreached) {
        return toIntArray(unreached, size(), this::bestNumOfTransfers);
    }

    @Override
    public int size() {
        return bestTimes.size();
    }

    @Override
    public int bestOverallJourneyTravelDuration() {
        if(minJourneyTravelDuration == NOT_SET) {
            for (TransferLeg it : transferLegs) {
                int v = bestTravelDuration(it.stop()) + it.durationInSeconds();
                minJourneyTravelDuration = Math.min(minJourneyTravelDuration, v);
            }
        }
        return minJourneyTravelDuration;
    }

    @Override
    public int bestOverallJourneyNumOfTransfers() {
        if(minJourneyNumOfTransfers == NOT_SET) {
            for (TransferLeg it : transferLegs) {
                int v = bestNumOfTransfers(it.stop());
                minJourneyNumOfTransfers = Math.min(minJourneyNumOfTransfers, v);
            }
        }
        return minJourneyNumOfTransfers;
    }

    /**
     * Convert one of heuristics to an int array.
     */
    private int[] toIntArray(int unreached, int size, IntUnaryOperator supplier) {
        int[] a = IntUtils.intArray(size, unreached);
        for (int i = 0; i < a.length; i++) {
            if(reached(i)) {
                a[i] = supplier.applyAsInt(i);
            }
        }
        return a;
    }
}
