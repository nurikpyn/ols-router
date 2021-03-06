/**
 * Copyright 2008-2019, Province of British Columbia
 *  All rights reserved.
 */
package ca.bc.gov.ols.router.api;

import java.util.Collections;
import java.util.List;

import com.vividsolutions.jts.geom.LineString;

import ca.bc.gov.ols.router.data.enums.DistanceUnit;
import ca.bc.gov.ols.router.directions.AbstractTravelDirection;
import ca.bc.gov.ols.router.directions.Direction;
import ca.bc.gov.ols.router.directions.Notification;

public class RouterDirectionsResponse extends RouterRouteResponse {

	private List<Direction> directions;
	private List<Notification> notifications;
	
	public RouterDirectionsResponse(RoutingParameters params) {
		super(params);
		directions = Collections.emptyList();
	}
	
	public RouterDirectionsResponse(RoutingParameters params, double distance, double time, LineString path, 
			List<Direction> directions, List<Notification> notifications) {
		super(params, distance, time, path);
		this.directions = directions;
		this.notifications = notifications;
		for(Direction dir : directions) {
			if(dir instanceof AbstractTravelDirection)
				((AbstractTravelDirection)dir).setDistance(DistanceUnit.METRE.convertTo(((AbstractTravelDirection)dir).getDistance(), params.getDistanceUnit()));
		}
	}

	public List<Direction> getDirections() {
		return directions;
	}
	
	public List<Notification> getNotifications() {
		return notifications;
	}

	@Override
	public void reproject(GeometryReprojector gr) {
		super.reproject(gr);
		for(Direction dir : directions) {
			dir.setPoint(gr.reproject(dir.getPoint(),getSrsCode()));
		}
	}
}
