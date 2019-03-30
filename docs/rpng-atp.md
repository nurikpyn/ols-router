# Route Planner NG Acceptance Test Plan

## Background
Route Planner Next Generation is a three year project to develop an open source route planner capable of time-dependent routing and commercial vehicle routing.

- Version 2.0, due for release in May, 2019 will support time-dependent routing. 
- Version 2.1 will support truck routing for use in height clearance applications.
- Version 3.0 will support truck routing as needed in core truck permitting.
- Version 4.0 will support ferry schedules, real-time road events, and truck routing as needed in advanced truck permitting.

## Purpose
This document defines the tools, methods, and approach to be used in acceptance testing the Route Planner Next Generation.

## Components to be Tested
1. Route Planner is an online API for route planning
2. Route Planner Administration Application
3. Routable BC Maker is an internal application used to take source data such as the ITN and enhanced turn-restrictions and automatically generate input datasets for the Route Planner.

## Test Tools

### Logging and tracking issues

GitHub and ZenHub are to be used for issue logging and tracking

### Defining, storing, and executing test cases and analysing results

- The *Route Planner Test Framework* (RPTF) can store and execute a suite of routing test cases and compare results to a reference dataset. 

- The *Brython Router Probe* will be used for additional testing as needed.

- The Location Services in Action application will be used for interactive router validation and bash testing.

## Test Planning Process

Route planning test cases will be stored in both the RPTF and BRP.

Define test cases for each feature of the router including:
 - correct-side routing
 - time-independent turn restrictions
 - time-dependent turn restrictions
 - truck-dimension based trun restrictions
 - start-time/date
 - turn costs
 - designated truck routes
 - scheduled road events
 - historic traffic congestion
 - hard road restrictions (e.g.,height,weight, width)
 - ferry schedules
 - multiple waypoints
 - route optimization
 

Define test cases for various geographic scenarios including:
 - urban areas (high road density)
 - rural areas (low road density)
 - by geographic region (e.g., lower mainland, Vancouver Island, NW BC, SE BC, NE BC, Prince George area)
 - short distances
 - long distances


## Testing Process

0. Preconditions:
-- route planner deployed on delivery and test platforms
-- test tools deployed and configured to access route planner on delivery and test platforms

1. Run test suite on developer platform and verify results (system test)

2. Run test suite on delivery platform and compare to developer platform (system test)

3. Run test suite on test platform and compare to delivery platform (acceptance test)

4. Bash test the route planner using the Location Services in Action application

4. The Ministry will accept or reject system based on acceptance test results.