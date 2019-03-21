package com.conveyal.r5.profile.entur.rangeraptor.path;

import com.conveyal.r5.profile.entur._shared.Egress;
import com.conveyal.r5.profile.entur._shared.StopArrivalsTestData;
import com.conveyal.r5.profile.entur.api.TestTripSchedule;
import com.conveyal.r5.profile.entur.api.path.Path;
import com.conveyal.r5.profile.entur.rangeraptor.transit.TransitCalculator;
import org.junit.Test;

import static com.conveyal.r5.profile.entur._shared.StopArrivalsTestData.BOARD_SLACK;
import static com.conveyal.r5.profile.entur._shared.StopArrivalsTestData.basicTripByReverseSearch;
import static com.conveyal.r5.profile.entur.rangeraptor.transit.TransitCalculator.testDummyCalculator;
import static com.conveyal.r5.profile.entur.util.TimeUtils.timeToStrLong;
import static org.junit.Assert.assertEquals;

public class ReversePathMapperTest {
    private static final TransitCalculator CALCULATOR = testDummyCalculator(BOARD_SLACK, false);

    @Test
    public void mapToPathReverseSearch() {
        // Given:
        Egress egress = basicTripByReverseSearch();
        DestinationArrival<TestTripSchedule> destArrival = new DestinationArrival<>(
                egress.previous(),
                egress.arrivalTime(),
                egress.additionalCost()
        );
        Path<TestTripSchedule> expected = StopArrivalsTestData.basicTripAsPath();
        PathMapper<TestTripSchedule> mapper = CALCULATOR.createPathMapper();


        //When:
        Path<TestTripSchedule> path = mapper.mapToPath(destArrival);

        // Then:
        assertTime("startTime", expected.startTime(), path.startTime());
        assertTime("endTime", expected.endTime(), path.endTime());
        assertTime("totalTravelDurationInSeconds", expected.totalTravelDurationInSeconds(), path.totalTravelDurationInSeconds());
        assertEquals("cost", expected.cost(), path.cost());
        assertEquals("numberOfTransfers",  expected.numberOfTransfers(), path.numberOfTransfers());
        assertEquals(expected.toString(), path.toString());
        assertEquals(expected, path);
    }


    private void assertTime(String msg, int expTime, int actualTime) {
        assertEquals(msg, timeToStrLong(expTime), timeToStrLong(actualTime));
    }
}