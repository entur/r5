package com.conveyal.r5.speed_test;

import com.conveyal.r5.speed_test.api.model.Itinerary;
import com.conveyal.r5.speed_test.api.model.Leg;
import com.conveyal.r5.util.ParetoDominateFunction;
import com.conveyal.r5.util.ParetoSortable;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static com.conveyal.r5.util.ParetoDominateFunction.createParetoDominanceFunctionArray;

public class SpeedTestItinerary extends Itinerary implements ParetoSortable {
    private static final Map<String, String> AGENCY_NAMES_SHORT = new HashMap<>();

    static {
        AGENCY_NAMES_SHORT.put("Hedmark Trafikk FKF", "Hedmark");
        AGENCY_NAMES_SHORT.put("Indre Namdal Trafikk A/S","I.Namdal");
        AGENCY_NAMES_SHORT.put("Møre og Romsdal fylkeskommune", "M&R FK");
        AGENCY_NAMES_SHORT.put("Nettbuss Travel AS","Nettbuss");
        AGENCY_NAMES_SHORT.put("Nord-Trøndelag fylkeskommune","N-Trøndelag");
        AGENCY_NAMES_SHORT.put("Norgesbuss Ekspress AS","Norgesbuss");
        AGENCY_NAMES_SHORT.put("NOR-WAY Bussekspress", "NOR-WAY");
        AGENCY_NAMES_SHORT.put("Nordland fylkeskommune", "Nordland");
        AGENCY_NAMES_SHORT.put("Opplandstrafikk", "Oppland");
        AGENCY_NAMES_SHORT.put("Troms fylkestrafikk", "Troms");
        AGENCY_NAMES_SHORT.put("Vestfold Kollektivtrafikk as", "Vestfold");
        AGENCY_NAMES_SHORT.put("Østfold fylkeskommune", "Østfold");
    }



    private final int[] paretoValues = new int[4];

    void initParetoVector() {
        int i = 0;
        paretoValues[i++] = this.transfers;
        paretoValues[i++] = this.duration.intValue();
        paretoValues[i++] = this.walkDistance.intValue();

        //Set<String> modes = new HashSet<>();
        Set<String> agencies = new HashSet<>();

        double durationLimit = 0;

        for (Leg leg : legs) {
            if (leg.isTransitLeg()) {
                durationLimit += leg.distance;
            }
        }

        durationLimit /= 3;

        for (Leg leg : legs) {
            if(leg.isTransitLeg()) {
                if (leg.distance > durationLimit) {
                    //modes.add(leg.mode);
                    agencies.add(leg.agencyId);
                }
            }
        }
        //paretoValues[i++] = modes.hashCode();
        paretoValues[i] = agencies.hashCode();
    }

    static ParetoDominateFunction.Builder paretoDominanceFunctions() {
        return createParetoDominanceFunctionArray()
                .lessThen()
                .lessThen()
                .lessThen()
                //.different()
                .different();
    }

    @Override
    public int[] paretoValues() {
        return paretoValues;
    }

    @Override
    public String toString() {
        StringBuilder routesBuf = new StringBuilder();
        Set<String> modes = new TreeSet<>();
        Set<String> agencies = new TreeSet<>();
        List<String> stops = new ArrayList<>();
        boolean append = false;

        for (Leg it : legs) {
            if(it.isTransitLeg()) {
                modes.add(it.mode);
                agencies.add(AGENCY_NAMES_SHORT.getOrDefault(it.agencyName, it.agencyName));
                stops.add(toStr(it.startTime) + " " + it.from.stopIndex + "->" +it.to.stopIndex + " " + toStr(it.endTime));

                if(append) routesBuf.append(" > ");
                append = true;
                routesBuf.append(it.routeShortName);
            }
        }
        return String.format(
                "%2d %5d %5.0f  %5s %5s  %-16s %-30s %-28s %s",
                transfers,
                duration/60,
                walkDistance,
                toStr(startTime),
                toStr(endTime),
                modes,
                agencies,
                routesBuf,
                stops
        );
    }


    public static String toStringHeader() {
        return String.format("%2s %5s %5s  %-5s %-5s  %-16s %-30s %-28s %s", "TF", "Time", "Walk", "Start", "End", "Modes", "Agencies", "Routes", "Stops");
    }

    /**
     * Create a compact representation of an itinerary.
     * Example:
     * <pre>
     * 09:29 > 09:30-37358-NW180-18:20-86727 > 19:30-3551-NW130-22:40-4917 > 22:40
     * </pre>
     */
    public String toStringCompact() {
        StringBuilder buf = new StringBuilder();
        buf.append(toStr(startTime));
        buf.append(" > ");

        for (Leg it : legs) {
            if(it.isTransitLeg()) {
                buf.append(toStr(it.startTime));
                buf.append("_");
                buf.append(it.routeShortName);
                buf.append("_");
                buf.append(it.from.stopIndex);
                buf.append("_");
                buf.append(it.to.stopIndex);
                buf.append(" > ");
            }
        }
        buf.append(toStr(endTime));
        return buf.toString();
    }

    private String toStr(Calendar c) {
        return c==null ? "X" : String.format("%02d:%02d", c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE));
    }
}