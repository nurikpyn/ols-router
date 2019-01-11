/**
 * Copyright 2008-2019, Province of British Columbia
 *  All rights reserved.
 */
package ca.bc.gov.app.router.engine;

import java.io.IOException;

import org.onebusaway.gtfs.impl.GtfsDaoImpl;

import ca.bc.gov.app.router.data.StreetSegment;
import ca.bc.gov.app.router.data.TurnCost;
import ca.bc.gov.app.router.datasources.RowReader;
import ca.bc.gov.app.router.open511.EventResponse;

public interface GraphBuilder {

	public abstract void addEdge(StreetSegment seg);

	public abstract void addTurnCost(TurnCost cost);

	public abstract void addEvents(EventResponse eventResponse);

	public abstract void addTraffic(RowReader trafficReader);

	public abstract void addSchedules(GtfsDaoImpl gtfs, RowReader mappingReader) throws IOException;
	
}