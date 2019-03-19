package com.conveyal.r5.speed_test;

import com.conveyal.r5.api.util.LegMode;
import com.conveyal.r5.api.util.TransitModes;
import com.conveyal.r5.profile.ProfileRequest;
import com.conveyal.r5.profile.entur.api.request.Optimization;
import com.conveyal.r5.profile.entur.api.request.RangeRaptorProfile;
import com.conveyal.r5.profile.entur.api.request.RangeRaptorRequest;
import com.conveyal.r5.profile.entur.api.request.RequestBuilder;
import com.conveyal.r5.profile.entur.api.request.TuningParameters;
import com.conveyal.r5.speed_test.cli.CommandLineOpts;
import com.conveyal.r5.speed_test.cli.SpeedTestCmdLineOpts;
import com.conveyal.r5.speed_test.test.TestCase;
import com.conveyal.r5.speed_test.transit.AccessEgressLeg;
import com.conveyal.r5.speed_test.transit.EgressAccessRouter;
import com.conveyal.r5.transit.TripSchedule;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.map.TIntIntMap;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Consumer;


/**
 * Help SpeedTast with careting {@link ProfileRequest}, old r5 request, and mapping to new {@link RangeRaptorRequest}.
 */
class RequestSupport {

    /**
     * This is used to expand the seach window for all test cases to test the effect of long windows.
     * <p/>
     * REMEMBER TO CHANGE IT BACK TO 0 BEFORE VCS COMMIT.
     */
    private static final int EXPAND_SEARCH_WINDOW_HOURS = 0;

    static final TuningParameters TUNING_PARAMETERS = new TuningParameters() {
        @Override
        public int maxNumberOfTransfers() {
            return 12;
        }

        // We don´t want to relax the results in the test, because it make it much harder to verify the result
        @Override
        public double relaxCostAtDestinationArrival() {
            return 1.0;
        }

        @Override
        public int searchThreadPoolSize() {
            return 0;
        }
    };

    private RequestSupport() { }

    static ProfileRequest buildProfileRequest(TestCase testCase, SpeedTestCmdLineOpts opts) {
        ProfileRequest request = new ProfileRequest();

        request.accessModes = request.egressModes = request.directModes = EnumSet.of(LegMode.WALK);
        request.maxWalkTime = 20;
        request.maxTripDurationMinutes = 1200; // Not in use by the "new" RR or McRR
        request.transitModes = EnumSet.of(TransitModes.TRAM, TransitModes.SUBWAY, TransitModes.RAIL, TransitModes.BUS);
        request.description = testCase.origin + " -> " + testCase.destination;
        request.fromLat = testCase.fromLat;
        request.fromLon = testCase.fromLon;
        request.toLat = testCase.toLat;
        request.toLon = testCase.toLon;
        request.fromTime = testCase.departureTime;
        request.toTime = request.fromTime + testCase.window;
        request.date = LocalDate.of(2019, 1, 28);
        request.numberOfItineraries = opts.numOfItineraries();
        return request;
    }



    static RangeRaptorRequest<TripSchedule> createRangeRaptorRequest(
            CommandLineOpts opts,
            ProfileRequest request,
            SpeedTestProfile profile,
            int latestArrivalTime,
            int numOfExtraTransfers,
            boolean oneIterationOnly,
            EgressAccessRouter streetRouter
    ) {
        // Add half of the extra time to departure and half to the arrival
        int expandDeltaSeconds = EXPAND_SEARCH_WINDOW_HOURS * 3600/2;


        RequestBuilder<TripSchedule> builder = new RequestBuilder<TripSchedule>();
        builder.searchParams()
                .boardSlackInSeconds(120)
                .earliestDepartureTime(request.fromTime - expandDeltaSeconds)
                .latestArrivalTime(latestArrivalTime + expandDeltaSeconds)
                .searchWindowInSeconds(request.toTime - request.fromTime + 2 * expandDeltaSeconds)
                .numberOfAdditionalTransfers(numOfExtraTransfers);

        if(oneIterationOnly) {
            builder.searchParams().searchOneIterationOnly();
        }

        builder.enableOptimization(Optimization.PARALLEL);

        builder.profile(profile.raptorProfile);
        for (Optimization it : profile.optimizations) {
            builder.enableOptimization(it);
        }
        if(profile.raptorProfile.isOneOf(RangeRaptorProfile.NO_WAIT_STD, RangeRaptorProfile.NO_WAIT_BEST_TIME)) {
            builder.searchParams().searchOneIterationOnly();
        }

        builder.searchDirection(profile.forward);

        addAccessEgressStopArrivals(streetRouter.accessTimesToStopsInSeconds, builder.searchParams()::addAccessStop);
        addAccessEgressStopArrivals(streetRouter.egressTimesToStopsInSeconds, builder.searchParams()::addEgressStop);

        addDebugOptions(builder, opts);

        RangeRaptorRequest<TripSchedule> req = builder.build();

        if (opts.debugRequest()) {
            System.err.println("-> Request: " + req);
        }

        return req;
    }


    private static void addAccessEgressStopArrivals(TIntIntMap timesToStopsInSeconds, Consumer<AccessEgressLeg> addStop) {
        for(TIntIntIterator it = timesToStopsInSeconds.iterator(); it.hasNext(); ) {
            it.advance();
            addStop.accept(new AccessEgressLeg(it.key(), it.value()));
        }
    }

    private static void addDebugOptions(RequestBuilder<TripSchedule> builder, CommandLineOpts opts) {
        List<Integer> stops = opts.debugStops();
        List<Integer> path = opts.debugPath();

        boolean debugLoggerEnabled = opts.debugRequest();

        if(opts instanceof SpeedTestCmdLineOpts) {
            debugLoggerEnabled = debugLoggerEnabled || ((SpeedTestCmdLineOpts)opts).compareHeuristics();
        }

        if(!debugLoggerEnabled && stops.isEmpty() && path.isEmpty()) {
            return;
        }

        DebugLogger logger = new DebugLogger(debugLoggerEnabled);

        builder.debug()
                .stopArrivalListener(logger::stopArrivalLister)
                .pathFilteringListener(logger::pathFilteringListener)
                .logger(logger)
                .debugPathFromStopIndex(opts.debugPathFromStopIndex());
        builder.debug().path().addAll(path);
        builder.debug().stops().addAll(stops);
    }
}
