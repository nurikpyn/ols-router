/**
 * Copyright 2008-2019, Province of British Columbia
 *  All rights reserved.
 */
package ca.bc.gov.ols.router.engine.basic;

import gnu.trove.map.hash.TIntObjectHashMap;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.function.BiFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.algorithm.Angle;
import com.vividsolutions.jts.geom.LineString;

import ca.bc.gov.ols.router.RouterConfig;
import ca.bc.gov.ols.router.api.RoutingParameters;
import ca.bc.gov.ols.router.data.enums.RouteOption;
import ca.bc.gov.ols.router.data.enums.RoutingCriteria;
import ca.bc.gov.ols.router.util.LineStringSplitter;

public class DijkstraShortestPath {
	private final static Logger logger = LoggerFactory.getLogger(DijkstraShortestPath.class.getCanonicalName());
	
	private BasicGraph graph;
	private RoutingParameters params;
	
	public DijkstraShortestPath(BasicGraph graph, RoutingParameters params) {
		this.graph = graph;
		this.params = params;
	}
	
	public EdgeList findShortestPath(SplitEdge startEdge, SplitEdge endEdge, double timeOffset) {	
		return findShortestPaths(startEdge, new SplitEdge[]{endEdge}, timeOffset)[0];
	}
	
	public EdgeList[] findShortestPaths(SplitEdge startEdge, SplitEdge[] toEdges, double timeOffset) {
		
		final CostFunction<Integer,Double,Double,Double> costFunction = params.getCriteria() == RoutingCriteria.SHORTEST 
				? (edgeId, time, dist) -> dist * (params.isFollowTruckRoute() && !graph.isTruckRoute(edgeId) ? params.getTruckRouteMultiplier() : 1) 
				: (edgeId, time, dist) -> time * (params.isFollowTruckRoute() && !graph.isTruckRoute(edgeId) ? params.getTruckRouteMultiplier() : 1);
		
		final BiFunction<Integer,LocalDateTime,Short> speedFunction = params.isEnabled(RouteOption.TRAFFIC) ? graph::getEffectiveSpeed : (edgeId,time) -> graph.getSpeedLimit(edgeId);
		
		// maps from endEdgeId to a list of indexes into the toEdgesArray
		TIntObjectHashMap<List<Integer>> endEdgesById = new TIntObjectHashMap<List<Integer>>();
		// and a map to best cost found so far
		DijkstraWalker[] costByToEdgeIdx = new DijkstraWalker[toEdges.length];
		
		// and populate them
		for(int toEdgeIdx = 0; toEdgeIdx < toEdges.length; toEdgeIdx++) {
			SplitEdge toEdge = toEdges[toEdgeIdx];
			for(int edgeId : toEdge.getEdgeIds()) {
				List<Integer> edges = endEdgesById.get(edgeId);
				if(edges == null) {
					edges = new ArrayList<Integer>();
					endEdgesById.put(edgeId, edges);
				}
				edges.add(toEdgeIdx);
			}
			costByToEdgeIdx[toEdgeIdx] = new DijkstraWalker(toEdge.getEdgeIds()[0], BasicGraph.NO_NODE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, null);
		}
		
		int minPaths = Math.min(params.getMaxPairs(), toEdges.length);
		EdgeList[] paths = new EdgeList[toEdges.length];
		int pathsFinished = 0;
		double worstPathCost = 0;
		LocalDateTime startTime = LocalDateTime.ofInstant(params.getDeparture().plusSeconds(Math.round(timeOffset)), RouterConfig.DEFAULT_TIME_ZONE);
		
		// check all of the to edges for the special case of being the same edge as the start edge
		nextEndEdge:
		for(int toEdgeIdx = 0; toEdgeIdx < toEdges.length; toEdgeIdx++) {
			// shortcut the case where start and end edge are the same, and can be traversed between
			// if the start and end edge are the same
			// and either the street is bidir or it is one-way and the end point is further along than the start point
			SplitEdge endEdge = toEdges[toEdgeIdx];
			for(int startEdgeId : startEdge.getEdgeIds()) {
				for(int endEdgeId : endEdge.getEdgeIds()) {
					if(startEdgeId == endEdgeId 
							&& (graph.getReversed(startEdgeId) 
									? startEdge.getToSplitLength() <= endEdge.getToSplitLength()
									: startEdge.getFromSplitLength() <= endEdge.getFromSplitLength())) {
						// then shortcut the graph walking
						int toNodeId;
						LineString[] splitString;
						double length;
						if(graph.getReversed(startEdgeId)) {
							toNodeId = graph.getFromNodeId(startEdgeId);
							splitString = LineStringSplitter.split(startEdge.getFromSplit(), endEdge.getPoint());
							splitString[0] = splitString[1]; 
							splitString[1] = startEdge.getToSplit();
							length = splitString[0].getLength();
						} else {
							toNodeId = graph.getToNodeId(startEdgeId);
							splitString = LineStringSplitter.split(startEdge.getToSplit(), endEdge.getPoint());
							splitString[1] = splitString[0]; 
							splitString[0] = startEdge.getFromSplit();
							length = splitString[1].getLength();
						}
						
			
						SplitEdge newStartEdge = new SplitEdge(new int[] {startEdgeId}, startEdge.getPoint(), splitString);
						double time = length * 3.6 / speedFunction.apply(endEdgeId, startTime);
						EdgeList edges = new EdgeList(1);
						edges.add(startEdgeId, time, length, 0);
						edges.setStartEdge(newStartEdge);
						edges.setEndEdge(endEdge);
						paths[toEdgeIdx] = edges;
						double cost = costFunction.apply(endEdgeId, time, length);
						costByToEdgeIdx[toEdgeIdx] = new DijkstraWalker(endEdgeId, toNodeId, cost, time, length, null);
						pathsFinished++;
						if(cost > worstPathCost) {
							worstPathCost = cost;
						}
						continue nextEndEdge;						
					}
				}
			} 
		}

		// setup the cost data structure and queue
		boolean[] edgeIdVisisted = new boolean[graph.numEdges()];
		Queue<DijkstraWalker> queue = new PriorityQueue<DijkstraWalker>();
		int checkedEdgeCount = 0;

		// add all the start edges to the Q and cost map
		for(int startEdgeId : startEdge.getEdgeIds()) {
			double length = graph.getReversed(startEdgeId) ? startEdge.getFromSplitLength() : startEdge.getToSplitLength();
			double time = length * 3.6 / speedFunction.apply(startEdgeId, startTime);
			DijkstraWalker startWalker = new DijkstraWalker(startEdgeId, graph.getToNodeId(startEdgeId), 
					costFunction.apply(startEdgeId, time, length), time, length, null);
			queue.add(startWalker);
			//costByEdgeId.put(startEdgeId, startWalker);
		}
		
		// traverse network looking for the cheapest paths
		DijkstraWalker walker;
		while((walker = queue.poll()) != null) {
			checkedEdgeCount++;

			int nodeId = walker.getNodeId();
			int fromEdgeId = walker.getEdgeId();
			for(int edgeId = graph.nextEdge(nodeId, BasicGraph.NO_EDGE); edgeId != BasicGraph.NO_EDGE; edgeId = graph.nextEdge(nodeId, edgeId)) {
				// if we've already been to this non-end, non-internal edge
				List<Integer> endEdges = endEdgesById.get(edgeId);
				if(endEdges == null && edgeIdVisisted[edgeId] && (!params.isEnabled(RouteOption.TURNRESTRICTIONS) || !graph.getTurnCostLookup().isInternalEdge(edgeId))) {
					// skip it
					continue;
				}
				// filter the edge based on constraints
				// the following comparisons take advantage of the fact that comparisons with NaN always return false  
				if(params.getHeight() != null && params.getHeight() > graph.getMaxHeight(edgeId)) {
					continue;
				}
				if(params.getWidth() != null && params.getWidth() > graph.getMaxWidth(edgeId)) {
					continue;
				}
				// TODO make the maximum standard vehicle length of 12.5 a config parameter
				// TODO make the minimum angle of 30 degrees a config parameter
				if(params.getLength() != null && params.getLength() > 12.5
						&& getAngle(fromEdgeId, edgeId) < 30) {
					continue;
				}
				if(params.getWeight() != null && graph.getMaxWeight(edgeId) != null && params.getWeight() > graph.getMaxWeight(edgeId)) {
					continue;
				}
				
				// TODO Handle alternate time zones
				LocalDateTime currentDateTime = null;
				int overrideTravelSeconds = -1;
				int waitTime = 0;
				if(params.isEnabled(RouteOption.TIMEDEPENDENCY)) {
					currentDateTime = LocalDateTime.ofInstant(params.getDeparture().plusSeconds(Math.round(timeOffset + walker.getTime())), RouterConfig.DEFAULT_TIME_ZONE);
					if(params.isEnabled(RouteOption.EVENTS)) {
						waitTime = graph.getEventLookup().lookup(edgeId, currentDateTime);
						if(waitTime < 0) {
							// this segment is inaccessible due to an event
							continue;
						}
						// TODO handle other types of events (slow-downs due to partial lane closures, etc.)
					}
					if(params.isEnabled(RouteOption.SCHEDULING)) {
						int[] waitAndTravelTime = graph.getScheduleLookup().lookup(edgeId, currentDateTime);
						
						if(waitAndTravelTime[1] > 0) {
							waitTime = waitAndTravelTime[0]; 
							overrideTravelSeconds = waitAndTravelTime[1];
							logger.info("Ferry schedule travel time: {}", overrideTravelSeconds);
						}
					}
				}
				if(!params.isEnabled(RouteOption.TIMEDEPENDENCY) || !params.isEnabled(RouteOption.SCHEDULING)) {
					FerryInfo info = graph.getScheduleLookup().getFerryInfo(edgeId);
					if(info != null && info.getMinWaitTime() > 0) {
						waitTime = info.getMinWaitTime();
					}
				}
				
				int turnCost = graph.getTurnCostLookup().lookup(edgeId, walker, currentDateTime);
				if(params.isEnabled(RouteOption.TURNRESTRICTIONS) && turnCost < 0) {
					continue;
				}
				if(!params.isEnabled(RouteOption.TURNCOSTS)) {
					turnCost = 0;
				}
				
				// TODO possibly allow U-turns in some situations/params
				if(edgeId == graph.getOtherEdgeId(fromEdgeId)) {
					// this is a U-turn
					continue;
				}

				// if this is an end edge
				if(endEdges != null) {
					// for each end point on this edge
					for(int endEdgeIdx : endEdges) {
						SplitEdge endEdge = toEdges[endEdgeIdx];
						double length;
						if(graph.getReversed(edgeId)) {
							length = endEdge.getToSplitLength();
						} else {
							length = endEdge.getFromSplitLength();
						}
						// calculate cost
						double edgeTime = waitTime + length * 3.6 / speedFunction.apply(edgeId, currentDateTime) + turnCost;
						double time = walker.getTime() + edgeTime;
						double dist = walker.getDist() + length;
						double cost = walker.getCost() + costFunction.apply(edgeId, edgeTime, length);
						DijkstraWalker newWalker = new DijkstraWalker(edgeId, graph.getOtherNodeId(edgeId, nodeId), cost, time, dist, walker, waitTime);
						
						// if it the best path to the end edge so far
						double prevCost = costByToEdgeIdx[endEdgeIdx].getCost();
						if (cost < prevCost) {
							costByToEdgeIdx[endEdgeIdx] = newWalker;
							if(prevCost == Double.MAX_VALUE) {
								pathsFinished++;
							}
							if(cost > worstPathCost) {
								worstPathCost = cost;
							}
						}
					}
				}
				
				// store the cost to get to the far side of this edge
				double length = graph.getLength(edgeId);
				double edgeTime = waitTime;
				if(overrideTravelSeconds > 0) {
					edgeTime += overrideTravelSeconds;
				} else {
					edgeTime += length * 3.6 / speedFunction.apply(edgeId, currentDateTime) + turnCost;
				}
				double time = walker.getTime() + edgeTime;
				double dist = walker.getDist() + length;
				double cost = walker.getCost() + costFunction.apply(edgeId, edgeTime, length);
				DijkstraWalker newWalker = new DijkstraWalker(edgeId, graph.getOtherNodeId(edgeId, nodeId), cost, time, dist, walker, waitTime);
				edgeIdVisisted[edgeId] = true;
				// if we haven't found all paths or this path is still shorter than the worst shortest found
				if(pathsFinished < minPaths || cost < worstPathCost) {
					queue.add(newWalker);
				}
			}
		}
		logger.debug("{} edges checked to find the the shortest path", checkedEdgeCount);
		
		// make a list of all the toEdgeIndexes
		Integer[] toEdgeIdxs = new Integer[toEdges.length];
		for(int i = 0; i < toEdgeIdxs.length; i++) {
			toEdgeIdxs[i] = i;
		}
		// if we don't need to calculate all paths, sort them by cost
		if(minPaths < toEdges.length) {
			Arrays.sort(toEdgeIdxs, Comparator.comparingDouble(idx -> costByToEdgeIdx[idx].getCost()));
		}
		int calcPaths = 0;
		for(Integer toEdgeIdx : toEdgeIdxs) {
			if(calcPaths < minPaths) {
				if(paths[toEdgeIdx] == null) {
					paths[toEdgeIdx] = walkback(startEdge, toEdges[toEdgeIdx], costByToEdgeIdx[toEdgeIdx]);
				}
				calcPaths++;
			} else {
				paths[toEdgeIdx] = null;
			}
		}
		return paths;
	}
	
	private double getAngle(int fromEdgeId, int toEdgeId) {
		return Angle.toDegrees(Angle.diff(graph.getToAngleRads(fromEdgeId), graph.getFromAngleRads(toEdgeId)));			
	}

	private EdgeList walkback(SplitEdge startEdge, SplitEdge endEdge, DijkstraWalker endWalker) {
		// store the edges in the cheapest path, in reverse order 
		EdgeList edges = new EdgeList(100);
		edges.setStartEdge(startEdge);
		edges.setEndEdge(endEdge);

		for(DijkstraWalker curWalker = endWalker; curWalker != null; curWalker = curWalker.getFrom()) {
			edges.add(curWalker.getEdgeId(), curWalker.getTime(), curWalker.getDist(), curWalker.getWaitTime());
		} 
		return edges;
	}

}
