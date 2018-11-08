package com.conveyal.r5.profile.entur.rangeraptor.multicriteria.path;

import com.conveyal.r5.profile.entur.api.Path2;
import com.conveyal.r5.profile.entur.api.TripScheduleInfo;
import com.conveyal.r5.profile.entur.rangeraptor.DebugState;
import com.conveyal.r5.profile.entur.rangeraptor.multicriteria.arrivals.AbstractStopArrival;


/**
 * TODO TGR
 */
public class McPathBuilder<T extends TripScheduleInfo> {

    public Path2<T> extractPathsForStop(AbstractStopArrival<T> egressStop, int egressDurationInSeconds) {
        if (!egressStop.arrivedByTransit()) {
            return null;
        }
        debugPath(egressStop);
        return new McPath<T>(egressStop.path(), egressDurationInSeconds);
    }

    private void debugPath(AbstractStopArrival<T> egressStop) {
        DebugState.debugStopHeader("MC - CREATE PATH FOR EGRESS STOP: " + egressStop.stopIndex());

        if(DebugState.isDebug(egressStop.stopIndex())) {
            for (AbstractStopArrival p : egressStop.path()) {
                p.debug();
            }
        }
    }
}