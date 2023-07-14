package net.simforge.flight.processor.rangebased;

import net.simforge.atmosphere.Airspeed;
import net.simforge.commons.bm.BMC;
import net.simforge.commons.misc.Geo;
import net.simforge.commons.misc.JavaTime;
import net.simforge.flight.core.EllipseCriterion;
import net.simforge.flight.core.Flightplan;
import net.simforge.networkview.core.Position;
import net.simforge.networkview.core.report.ReportRange;
import net.simforge.networkview.core.report.ReportUtils;
import net.simforge.networkview.core.report.persistence.Report;
import net.simforge.networkview.core.report.persistence.ReportPilotPosition;
import net.simforge.refdata.aircrafts.apd.AircraftPerformance;
import net.simforge.refdata.aircrafts.apd.AircraftPerformanceDatabase;

import java.text.DecimalFormat;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Track1 {
    private int pilotNumber;
    private List<Position> trackData = new ArrayList<>();
    private TrackedEvent startOfTrack;
    private TrackedEvent endOfTrack;
    private List<TrackedFlight> flights = new ArrayList<>();

    private Track1() {}

    public static Track1 build(ReportRange currentRange, ReportTimeline timeline, List<ReportPilotPosition> reportPilotPositions) {
        try (BMC ignored = BMC.start("Track1.build")) {
            Track1 track = new Track1();

            Map<String, ReportPilotPosition> reportPilotPositionByReport = reportPilotPositions.stream().collect(Collectors.toMap(p -> p.getReport().getReport(), Function.identity()));

            List<Report> reports = timeline.getReportsInRange(currentRange);
            for (Report report : reports) {
                ReportPilotPosition reportPilotPosition = reportPilotPositionByReport.get(report.getReport());
                Position position = reportPilotPosition != null ? Position.create(reportPilotPosition) : Position.createOfflinePosition(report);
                track.trackData.add(position);

                if (track.pilotNumber == 0 && reportPilotPosition != null) {
                    track.pilotNumber = reportPilotPosition.getPilotNumber();
                }
            }

            track.buildRanges();
            track.printRanges("Stage 0 - ranges prepared");
            track.buildCompleteFlights();
            track.printRanges("Stage 1 - complete flights processed");
            track.buildIncompleteFlights();
            track.printRanges("Stage 2 - incomplete flights processed");
            track.joinOnGroundRanges();
            track.printRanges("Stage 3 - on-groung ranges joined");

            return track;
        }
    }

    public List<Flight1> getFlights() {
        return flights.stream()
                .map(this::buildFlight1)
                .sorted(Flight1::compareByTakeoff)
                .collect(Collectors.toList());
    }

    private Flight1 buildFlight1(TrackedFlight trackedFlight) {
        Position firstSeenPosition = trackedFlight.firstSeenEvent.getNextRange().getFirstPosition();
        Position takeoffPosition = trackedFlight.takeoffEvent != null
                ? trackedFlight.takeoffEvent.getNextRange().getFirstPosition()
                : null;
        Position landingPosition = trackedFlight.landingEvent != null
                ? trackedFlight.landingEvent.getPreviousRange().getLastPosition()
                : null;
        Position lastSeenPosition = trackedFlight.lastSeenEvent.getPreviousRange().getLastPosition();

        Flight1 flight1 = new Flight1();

        flight1.setPilotNumber(pilotNumber);
        flight1.setDateOfFlight(JavaTime.yMd.format(firstSeenPosition.getReportInfo().getDt()));
        flight1.setCallsign(firstSeenPosition.getCallsign()); // todo ak3 frequency-based determination
        flight1.setAircraftType(firstSeenPosition.getFpAircraftType()); // todo ak3 frequency-based determination
        flight1.setAircraftRegNo(firstSeenPosition.getRegNo()); // todo ak3 frequency-based determination

        Map<String, Double> distanceAndTime = calculateDistanceAndTime(trackedFlight);
        flight1.setDistanceFlown(distanceAndTime.get("total.distance"));
        flight1.setFlightTime(distanceAndTime.get("total.time.flight"));
        flight1.setAirTime(distanceAndTime.get("total.time.air"));
        flight1.setFlightplan(Flightplan.fromPosition(firstSeenPosition)); // todo ak somehow to calculate most populated flightplan

        flight1.setFirstSeen(Flight1.position(firstSeenPosition));
        flight1.setTakeoff(takeoffPosition != null ? Flight1.position(takeoffPosition) : null);
        flight1.setLanding(landingPosition != null ? Flight1.position(landingPosition) : null);
        flight1.setLastSeen(Flight1.position(lastSeenPosition));

        flight1.setComplete(trackedFlight.trackingMode != TrackingMode.Incomplete);
        flight1.setTrackingMode(trackedFlight.trackingMode.name());

        return flight1;
    }

    // future to add online/offline, flighttime/airtime, etc
    private Map<String, Double> calculateDistanceAndTime(TrackedFlight trackedFlight) {
        Map<String, Double> result = new HashMap<>();

        TrackedEvent currentEvent = trackedFlight.firstSeenEvent;
        while (currentEvent != trackedFlight.lastSeenEvent) {
            TrackedRange range = currentEvent.getNextRange();
            double distance;
            double flightTime;
            double airTime;
            if (range.getType() == RangeType.Flying) {
                distance = range.getDistance();
                flightTime = airTime = range.getDuration().getSeconds() / 3600.0;
            } else if (range.getType() == RangeType.OnGround) {
                distance = 0;
                flightTime = range.getDuration().getSeconds() / 3600.0;
                airTime = 0;
            } else if (range.getType() == RangeType.Offline) {
                distance = Geo.distance(range.previousEvent.previousRange.getLastPosition().getCoords(),
                        range.nextEvent.nextRange.getFirstPosition().getCoords());
                flightTime = airTime = range.getDuration().getSeconds() / 3600.0;
            } else {
                throw new IllegalStateException();
            }

            result.merge("total.distance", distance, Double::sum);
            result.merge("total.time.flight", flightTime, Double::sum);
            result.merge("total.time.air", airTime, Double::sum);

            currentEvent = range.nextEvent;
        }

        return result;
    }

    private void buildRanges() {
        try (BMC ignored = BMC.start("Track1.buildRanges")) {
            Iterator<Position> iterator = trackData.iterator();
            Position startPosition = iterator.next();

            startOfTrack = TrackedEvent.startOfTrack(startPosition.getReportInfo().getReport());

            Position position = startPosition;

            // online/offline section
            TrackedRange range = startOfTrack.startNextRange(position);
            while (iterator.hasNext()) {
                position = iterator.next();
                boolean consumed = range.offer(position);
                if (consumed) {
                    continue;
                }

                // we have some event here

                Position prevPosition = range.getLastPosition();
                boolean wentOnline = !prevPosition.isPositionKnown() && position.isPositionKnown();
                boolean wentOffline = prevPosition.isPositionKnown() && !position.isPositionKnown();

                if (wentOnline) {
                    range = range.wentOnline(position);
                } else if (wentOffline) {
                    range = range.wentOffline(position);
                }
            }
            endOfTrack = range.endOfTrack();

            // takeoff/landing section
            TrackedEvent event = startOfTrack;
            while (event != endOfTrack) {
                range = event.getNextRange();
                if (range.getType() != RangeType.Online) {
                    event = range.getNextEvent();
                    continue;
                }

                splitByTakeoffLandingEvents(range);

                event = range.getNextEvent();
            }

            // touch&go section
            event = startOfTrack;
            while (event != endOfTrack) {
                if (event.getType() == EventType.Landing) {
                    TrackedEvent landingEvent = event;
                    TrackedRange onGroundRange = event.getNextRange();
                    if (onGroundRange.getType() == RangeType.OnGround) {
                        if (onGroundRange.getPositions().size() == 1) {
                            TrackedEvent takeoffEvent = onGroundRange.getNextEvent();
                            if (takeoffEvent.getType() == EventType.Takeoff) {
                                event = putTouchAndGoEvent(landingEvent, takeoffEvent);
                            }
                        }
                    }
                }

                range = event.getNextRange();
                event = range.getNextEvent();
            }
        }
    }

    private TrackedEvent putTouchAndGoEvent(TrackedEvent landingEvent, TrackedEvent takeoffEvent) {
        TrackedEvent touchAndGoEvent = new TrackedEvent(landingEvent.previousRange, EventType.TouchAndGo, landingEvent.report);
        touchAndGoEvent.nextRange = takeoffEvent.nextRange;
        takeoffEvent.nextRange.previousEvent = touchAndGoEvent;
        return touchAndGoEvent;
    }

    private void splitByTakeoffLandingEvents(TrackedRange outerRange) {
        TrackedRange currentRange = outerRange;
        RangeType currentRangeType = null;

        List<Position> positions = new ArrayList<>(outerRange.getPositions());
        for (int i = 0; i < positions.size(); i++) {
            Position currentPosition = positions.get(i);
            boolean onGround = currentPosition.isOnGround();
            RangeType eachPositionRangeType = onGround ? RangeType.OnGround : RangeType.Flying;

            if (currentRangeType == null) {
                currentRangeType = eachPositionRangeType;
                continue;
            }

            if (currentRangeType == eachPositionRangeType) {
                continue;
            }

            if (eachPositionRangeType == RangeType.Flying) {
                Position previousPosition = positions.get(i - 1);
                TrackedEvent newEvent = currentRange.splitByEvent(EventType.Takeoff, previousPosition);
                TrackedRange previousRange = newEvent.getPreviousRange();
                previousRange.setType(RangeType.OnGround);
                currentRangeType = RangeType.Flying;
                currentRange = newEvent.getNextRange();
            } else {
                TrackedEvent newEvent = currentRange.splitByEvent(EventType.Landing, currentPosition);
                TrackedRange previousRange = newEvent.getPreviousRange();
                previousRange.setType(RangeType.Flying);
                currentRangeType = RangeType.OnGround;
                currentRange = newEvent.getNextRange();
            }
        }

        if (currentRange != null && currentRangeType != null) {
            currentRange.setType(currentRangeType);
        }
    }

    private void printRanges(String stageName) {
        System.out.println("===== " + stageName + " =================================================================");
        TrackedRange range;
        TrackedEvent event = startOfTrack;
        while (event != endOfTrack) {
            System.out.println("Event " + event);
            range = event.getNextRange();
            System.out.println("\t\t" + range);
            event = range.getNextEvent();
        }
        System.out.println("Event " + event);
    }

    private void printFlights() {
        flights.forEach(System.out::println);
    }

    private void buildCompleteFlights() {
        try (BMC ignored = BMC.start("Track1.buildCompleteFlights")) {
            for (TrackedEvent event = startOfTrack; event != endOfTrack; event = event.getNextRange().getNextEvent()) {
                if (event.getType() != EventType.Takeoff) {
                    continue;
                }

                TrackedFlight flight = tryToBuildIdealFlight(event);
                if (flight != null) {
                    flight.markRanges();
                    flights.add(flight);
                    continue;
                }
                flight = tryToTASFlight(event);
                if (flight != null) {
                    flight.markRanges();
                    flights.add(flight);
                    continue;
                }
                flight = tryToEllipseFlight(event);
                if (flight != null) {
                    flight.markRanges();
                    flights.add(flight);
                }
            }
        }
    }

    private void buildIncompleteFlights() {
        try (BMC ignored = BMC.start("Track1.buildIncompleteFlights")) {
            for (TrackedRange range = startOfTrack.getNextRange(); range != null; range = range.getNextEvent().getNextRange()) {
                if (range.getFlight() != null
                    || range.getType() != RangeType.Flying) {
                    continue;
                }

                TrackedRange firstFlyingRange = range;
                TrackedRange lastFlyingRange = range;
                Flightplan flightplan = findFlightplan(range, null);

                // try to join flying range on right side
                boolean continueToRight = true;
                while (continueToRight) {
                    EventType nextEventType = lastFlyingRange.getNextEvent().getType();
                    switch (nextEventType) {
                        case Online:
                        case StartOfTrack:
                        case Takeoff:
                            throw new IllegalStateException();
                        case EndOfTrack:
                        case Landing:
                            continueToRight = false;
                            break;
                        case Offline:
                            TrackedRange nextFlyingRange = getNextFlyingRange(lastFlyingRange);
                            if (nextFlyingRange == null) {
                                continueToRight = false;
                                break; // it means that pilot did not reconnect after that flying section
                            }

                            if (nextFlyingRange.getFlight() != null) {
                                continueToRight = false;
                                break;
                            }

                            if (canSegmentsBeJoinedBasedOnTASversusDistance(lastFlyingRange, nextFlyingRange, flightplan)) {
                                lastFlyingRange = nextFlyingRange;
                                flightplan = flightplan != null ? flightplan : findFlightplan(nextFlyingRange, flightplan);
                            } else {
                                continueToRight = false;
                            }

                            break;
                        case TouchAndGo:
                            TrackedRange flyingAfterTouchAndGo = lastFlyingRange.getNextEvent().getNextRange();
                            if (flyingAfterTouchAndGo.getFlight() != null
                                    || flyingAfterTouchAndGo.getType() != RangeType.Flying) {
                                throw new IllegalStateException(); // this is unexpected, right range after t&g should be non-occupied flying
                            }
                            lastFlyingRange = flyingAfterTouchAndGo;
                            break;
                    }
                }

                TrackedFlight flight = TrackedFlight.first2last(
                        firstFlyingRange.getPreviousEvent(),
                        firstFlyingRange.getPreviousEvent().getType() == EventType.Takeoff ? firstFlyingRange.getPreviousEvent() : null,
                        lastFlyingRange.getNextEvent().getType() == EventType.Landing ? lastFlyingRange.getNextEvent() : null,
                        lastFlyingRange.getNextEvent(),
                        TrackingMode.Incomplete);
                flight.markRanges();
                flights.add(flight);
            }
        }
    }

    private void joinOnGroundRanges() {
        try (BMC ignored = BMC.start("Track1.joinOnGroundRanges")) {
            for (TrackedRange range = startOfTrack.getNextRange(); range != null; range = range.getNextEvent().getNextRange()) {
                if (range.getFlight() != null
                        || range.getType() != RangeType.OnGround) {
                    continue;
                }

                boolean isFlightOnLeft = range.getPreviousEvent().getType() == EventType.Landing;
                boolean isFlightOnRight = range.getNextEvent().getType() == EventType.Takeoff;

                if (isFlightOnLeft && isFlightOnRight) {
                    // todo split somehow
                    throw new UnsupportedOperationException();
                } else if (isFlightOnLeft) {
                    // after landing taxi in and unboarding
                    TrackedFlight flight = range.getPreviousEvent().getPreviousRange().getFlight();
                    flights.remove(flight);

                    TrackedFlight newFlight = TrackedFlight.first2last(flight.firstSeenEvent, flight.takeoffEvent, flight.landingEvent, range.getNextEvent(), flight.trackingMode);
                    newFlight.markRanges();
                    flights.add(newFlight);

                    // todo ak3 limit on-ground time by some time (30 mins?)
                } else { // isFlightOnRight
                    // boarding and before takeoff taxi out
                    TrackedFlight flight = range.getNextEvent().getNextRange().getFlight();
                    flights.remove(flight);

                    TrackedFlight newFlight = TrackedFlight.first2last(range.getPreviousEvent(), flight.takeoffEvent, flight.landingEvent, flight.lastSeenEvent, flight.trackingMode);
                    newFlight.markRanges();
                    flights.add(newFlight);

                    // todo ak3 limit on-ground time by some time (30 mins?)
                    // todo ak3 try to look at actual taxi time?
                }
            }
        }
    }

    private TrackedFlight tryToBuildIdealFlight(TrackedEvent takeoffEvent) {
        TrackedEvent event = takeoffEvent;
        while (event != endOfTrack) {
            TrackedRange nextRange = event.getNextRange();
            if (nextRange.getType() != RangeType.Flying) {
                return null;
            }

            TrackedEvent nextEvent = nextRange.getNextEvent();
            if (nextEvent.getType() == EventType.Landing) {
                return TrackedFlight.takeoff2landing(takeoffEvent, nextEvent, TrackingMode.Ideal);
            } else if (nextEvent.getType() == EventType.TouchAndGo) {
                event = nextEvent;
                // and continue
            } else {
                return null;
            }
        }

        return null;
    }

    private TrackedFlight tryToTASFlight(TrackedEvent takeoffEvent) {
        TrackedRange lastFlyingRange = takeoffEvent.getNextRange();
        Flightplan flightplan = Flightplan.fromPosition(lastFlyingRange.getLastPosition());
        if (flightplan == null) { // future more intellectual collection of flightplan information
            flightplan = Flightplan.fromPosition(takeoffEvent.getNextRange().getFirstPosition());
            if (flightplan == null) {
                return null;
            }
        }

        while (true) {
            TrackedEvent nextEvent = lastFlyingRange.getNextEvent();
            if (nextEvent == endOfTrack) {
                return null;
            } else if (nextEvent.getType() == EventType.Landing) {
                return TrackedFlight.takeoff2landing(takeoffEvent, nextEvent, TrackingMode.TAS);
            } else if (nextEvent.getType() == EventType.TouchAndGo) {
                lastFlyingRange = nextEvent.getNextRange();
                continue;
                // and continue
            }

            TrackedRange nextRange = nextEvent.getNextRange();
            boolean canWeSkipThisRange = false;
            RangeType nextRangeType = nextRange.getType();
            TrackedRange nextFlyingRange = null;
            if (nextRangeType == RangeType.Offline) {
                nextFlyingRange = getNextFlyingRange(nextRange);
                if (nextFlyingRange == null) {
                    return null;
                }

                canWeSkipThisRange = canSegmentsBeJoinedBasedOnTASversusDistance(lastFlyingRange, nextFlyingRange, flightplan);
            } else {
                throw new IllegalStateException();
            }

            if (canWeSkipThisRange) {
                lastFlyingRange = nextFlyingRange;
            } else {
                return null;
            }
        }
    }

    private boolean canSegmentsBeJoinedBasedOnTASversusDistance(TrackedRange lastFlyingRange, TrackedRange nextFlyingRange, Flightplan flightplan) {
        Position nextPosition = nextFlyingRange.getFirstPosition();
        Position lastPosition = lastFlyingRange.getLastPosition();
        double distance = Geo.distance(lastPosition.getCoords(), nextPosition.getCoords());
        double hours = JavaTime.hoursBetween(lastPosition.getReportInfo().getDt(), nextPosition.getReportInfo().getDt());
        int groundspeed = (int) (distance / hours);

        if (flightplan == null) {
            return false;
        }
        String aircraftType = flightplan.getAircraftType();
        if (aircraftType == null) {
            return false;
        }

        Optional<AircraftPerformance> performance = AircraftPerformanceDatabase.getPerformance(aircraftType);
        if (!performance.isPresent()) {
            UnknownAircraftTypes.add(aircraftType);
            return false;
        }

        Integer ias = performance.get().getCruiseIasAtCruiseCeiling();
        if (ias == null) {
            return false;
        }

        int minAltitude = Math.min(lastPosition.getActualAltitude(), nextPosition.getActualAltitude());
        int maxAltitude = Math.max(lastPosition.getActualAltitude(), nextPosition.getActualAltitude());

        if (maxAltitude < 10000) {
            ias = (int) (ias * 0.6); // initial climb or approach speed
        } else if (maxAltitude < 20000) {
            ias = (int) (ias * 0.8); // climb or descend speed
        }

        // todo ak3 max duration of offline segment?
        int minTas = (int) (Airspeed.iasToTas(ias, minAltitude) * 0.66);
        int maxTas = (int) (Airspeed.iasToTas(ias, maxAltitude) * 1.33);

        if (minTas <= groundspeed && groundspeed <= maxTas) {
            // we can join two flying ranges divided by one offline range
            return true;
        } else {
            return false;
        }
    }

    // todo ak3 future more intellectual collection of flightplan information
    private Flightplan findFlightplan(TrackedRange range, Flightplan previousFlightplan) {
        Flightplan flightplan = Flightplan.fromPosition(range.getLastPosition());
        if (flightplan != null) {
            return flightplan;
        }
        return previousFlightplan;
    }

    private TrackedFlight tryToEllipseFlight(TrackedEvent takeoffEvent) {
        TrackedRange lastFlyingRange = takeoffEvent.getNextRange();
        Flightplan flightplan = Flightplan.fromPosition(lastFlyingRange.getLastPosition());
        EllipseCriterion criterion = new EllipseCriterion(takeoffEvent.getNextRange().getFirstPosition(), flightplan);
        while (true) {
            TrackedEvent nextEvent = lastFlyingRange.getNextEvent();
            if (nextEvent == endOfTrack) {
                return null;
            } else if (nextEvent.getType() == EventType.Landing) {
                return TrackedFlight.takeoff2landing(takeoffEvent, nextEvent, TrackingMode.Ellipse);
            } else if (nextEvent.getType() == EventType.TouchAndGo) {
                lastFlyingRange = nextEvent.getNextRange();
                continue;
                // and continue
            }

            TrackedRange nextRange = nextEvent.getNextRange();
            boolean canWeSkipThisRange = false;
            RangeType nextRangeType = nextRange.getType();
            TrackedRange nextFlyingRange = null;
            if (nextRangeType == RangeType.Offline) {
                nextFlyingRange = getNextFlyingRange(nextRange);
                if (nextFlyingRange == null) {
                    return null;
                }

                // can we join lastFlyingRange and nextFlyingRange
                Position nextPosition = nextFlyingRange.getFirstPosition();

                if (criterion.meets(nextPosition)) {
                    canWeSkipThisRange = true;
                }
            } else {
                throw new IllegalStateException();
            }

            if (canWeSkipThisRange) {
                lastFlyingRange = nextFlyingRange;
            } else {
                return null;
            }
        }
    }

    private TrackedRange getNextFlyingRange(TrackedRange range) {
        TrackedEvent event = range.getNextEvent();
        while (event != endOfTrack) {
            if (event.getType() == EventType.Takeoff) {
                return null;
            } else if (event.getType() == EventType.Landing) {
                return null;
            }

            range = event.getNextRange();
            if (range.getType() == RangeType.Flying) {
                return range;
            }
            event = range.getNextEvent();
        }
        return null;
    }

    static class TrackedEvent {
        private TrackedRange previousRange;
        private EventType type;
        private String report;
        private TrackedRange nextRange;

        TrackedEvent(TrackedRange previousRange, EventType type, String report) {
            this.previousRange = previousRange;
            this.type = type;
            this.report = report;

            if (previousRange != null) {
                previousRange.nextEvent = this;
            }
        }

        static TrackedEvent startOfTrack(String report) {
            return new TrackedEvent(null, EventType.StartOfTrack, report);
        }

        TrackedRange startNextRange(Position position) {
            // todo check existing
            TrackedRange range = new TrackedRange();
            range.previousEvent = this;
            range.type = TrackedRange.getRangeType(position);
            range.positions.add(position);
            this.nextRange = range;
            return range;
        }

        TrackedRange getNextRange() {
            return nextRange;
        }

        TrackedRange getPreviousRange() {
            return previousRange;
        }

        @Override
        public String toString() {
            return String.format("Event %s at %s", type.name().toUpperCase(), report);
        }

        EventType getType() {
            return type;
        }
    }

    enum EventType {
        Takeoff,
        Landing,
        StartOfTrack,
        Online,
        Offline,
        EndOfTrack,
        TouchAndGo
    }

    static class TrackedRange {
        private TrackedEvent previousEvent;
        private RangeType type;
        private List<Position> positions = new ArrayList<>();
        private TrackedFlight flight;
        private TrackedEvent nextEvent;

        private TrackedRange() {
        }

        static RangeType getRangeType(Position position) {
            if (!position.isPositionKnown()) {
                return RangeType.Offline;
            } else {
                return RangeType.Online;
            }
        }

        boolean offer(Position position) { // todo checks
            RangeType rangeType = getRangeType(position);
            if (rangeType != this.type) {
                return false;
            }
            positions.add(position);
            return true;
        }

        TrackedRange wentOnline(Position position) { // todo checks, move out
            TrackedEvent onlineEvent = new TrackedEvent(this, EventType.Online, position.getReportInfo().getReport());
            return onlineEvent.startNextRange(position);
        }

        TrackedRange wentOffline(Position position) { // todo checks, move out
            TrackedEvent offlineEvent = new TrackedEvent(this, EventType.Offline, getLastPosition().getReportInfo().getReport());
            return offlineEvent.startNextRange(position);
        }

        TrackedEvent endOfTrack() { // todo checks, move out
            return new TrackedEvent(this, EventType.EndOfTrack, getLastPosition().getReportInfo().getReport());
        }

        // todo checks, move out
        TrackedEvent splitByEvent(EventType eventType, Position splitPosition) {
            int splitIndex = positions.indexOf(splitPosition);

            TrackedRange leftRange = new TrackedRange();
            this.previousEvent.nextRange = leftRange;
            leftRange.previousEvent = this.previousEvent;
            leftRange.type = this.type;
            leftRange.positions = this.positions.subList(0, splitIndex + 1);

            TrackedEvent newEvent = new TrackedEvent(leftRange, eventType, splitPosition.getReportInfo().getReport());

            TrackedRange rightRange = new TrackedRange();
            newEvent.nextRange = rightRange;
            rightRange.previousEvent = newEvent;
            rightRange.type = this.type;
            rightRange.positions = this.positions.subList(splitIndex, this.positions.size());
            rightRange.nextEvent = this.nextEvent;
            this.nextEvent.previousRange = rightRange;

            return newEvent;
        }

        Position getFirstPosition() {
            return positions.get(0);
        }

        Position getLastPosition() {
            return positions.get(positions.size() - 1);
        }

        TrackedEvent getPreviousEvent() {
            return previousEvent;
        }

        TrackedEvent getNextEvent() {
            return nextEvent;
        }

        RangeType getType() {
            return type;
        }

        TrackedFlight getFlight() {
            return flight;
        }

        // todo ???
        void setType(RangeType type) {
            this.type = type;
        }

        List<Position> getPositions() {
            return positions;
        }

        @Override
        public String toString() {
            Double distance = getDistance();
            return String.format("Range (%s) -> %s -> (%s), length %d, duration %s, distance %s, flight %s",
                    (previousEvent != null ? previousEvent.type : "n/a"),
                    type.name().toUpperCase(),
                    (nextEvent != null ? nextEvent.type : "n/a"),
                    positions.size(),
                    JavaTime.toHhmm(getDuration()),
                    (distance != null ? new DecimalFormat("0.0").format(distance) : "n/a"),
                    (flight != null ? flight.toString() : "n/a"));
        }

        Double getDistance() {
            if (type == RangeType.Offline) {
                return null;
            }

            double distance = 0;
            for (int i = 1; i < positions.size(); i++) {
                Position p1 = positions.get(i - 1);
                Position p2 = positions.get(i);

                distance += Geo.distance(p1.getCoords(), p2.getCoords());
            }

            return distance;
        }

        Duration getDuration() {
            return Duration.between(ReportUtils.fromTimestampJava(previousEvent.report), ReportUtils.fromTimestampJava(nextEvent.report));
        }
    }

    enum RangeType {
        Online,
        OnGround,
        Flying,
        Offline
    }

    static class TrackedFlight {
        private TrackedEvent firstSeenEvent;
        private TrackedEvent takeoffEvent;
        private TrackedEvent landingEvent;
        private TrackedEvent lastSeenEvent;
        private TrackingMode trackingMode;

        private TrackedFlight() {
        }

        public void markRanges() {
            TrackedEvent currentEvent = firstSeenEvent;
            while (currentEvent != lastSeenEvent) {
                currentEvent.getNextRange().flight = this;
                currentEvent = currentEvent.getNextRange().getNextEvent();
            }
        }

        public static TrackedFlight takeoff2landing(TrackedEvent takeoffEvent, TrackedEvent landingEvent, TrackingMode trackingMode) {
            TrackedFlight flight = new TrackedFlight();
            flight.firstSeenEvent = takeoffEvent;
            flight.takeoffEvent = takeoffEvent;
            flight.landingEvent = landingEvent;
            flight.lastSeenEvent = landingEvent;
            flight.trackingMode = trackingMode;
            return flight;
        }

        public static TrackedFlight first2last(TrackedEvent firstSeenEvent, TrackedEvent takeoffEvent, TrackedEvent landingEvent, TrackedEvent lastSeenEvent, TrackingMode trackingMode) {
            TrackedFlight flight = new TrackedFlight();
            flight.firstSeenEvent = firstSeenEvent;
            flight.takeoffEvent = takeoffEvent;
            flight.landingEvent = landingEvent;
            flight.lastSeenEvent = lastSeenEvent;
            flight.trackingMode = trackingMode;
            return flight;
        }

        @Override
        public String toString() {
            return String.format("Flight [%s %s -> %s %s] #%s",
                    firstSeenEvent.getNextRange().getFirstPosition().getAirportIcao(),
                    firstSeenEvent.getNextRange().getFirstPosition().getReportInfo().getDt().format(JavaTime.hhmm),
                    lastSeenEvent.getPreviousRange().getLastPosition().getReportInfo().getDt().format(JavaTime.hhmm),
                    lastSeenEvent.getPreviousRange().getLastPosition().getAirportIcao(),
                    hashCode());
        }
    }

    public enum TrackingMode {
        Ideal,
        TAS,
        Ellipse,
        Incomplete
    }
}
