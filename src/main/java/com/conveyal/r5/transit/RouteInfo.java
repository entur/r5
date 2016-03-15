package com.conveyal.r5.transit;

import com.conveyal.gtfs.model.Route;

import java.io.Serializable;
import java.net.URL;

/**
 * Information about a route.
 */
public class RouteInfo implements Serializable {
    public static final long serialVersionUID = 1L;

    public String agency_id;
    public String route_id;
    public String route_short_name;
    public String route_long_name;
    public int route_type;
    public String color;
    public URL agency_url;

    public RouteInfo (Route route) {
        this.agency_id = route.agency.agency_id;
        this.route_id = route.route_id;
        this.route_short_name = route.route_short_name;
        this.route_long_name = route.route_long_name;
        this.route_type = route.route_type;
        this.color = route.route_color;
        this.agency_url = route.route_url;
    }
}
