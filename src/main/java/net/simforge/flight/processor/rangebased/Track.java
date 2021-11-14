package net.simforge.flight.processor.rangebased;

import net.simforge.atmosphere.Airspeed;
import net.simforge.commons.misc.Geo;
import net.simforge.commons.misc.JavaTime;
import net.simforge.flight.core.EllipseCriterion;
import net.simforge.flight.core.Flightplan;
import net.simforge.networkview.core.report.ReportUtils;
import net.simforge.networkview.core.report.persistence.Report;
import net.simforge.networkview.core.report.persistence.ReportPilotPosition;
import net.simforge.networkview.core.Position;
import net.simforge.networkview.core.report.ReportRange;
import net.simforge.refdata.aircrafts.apd.AircraftPerformance;
import net.simforge.refdata.aircrafts.apd.AircraftPerformanceDatabase;

import java.text.DecimalFormat;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

class Track {
    private int pilotNumber;
    private List<Position> trackData = new ArrayList<>();
    private TrackedEvent startOfTrack;
    private TrackedEvent endOfTrack;
    private List<TrackedFlight> flights = new ArrayList<>();

    private Track() {}

    public static Track build(ReportRange currentRange, List<Report> reports, List<ReportPilotPosition> reportPilotPositions) {
        Track track = new Track();

        Map<String, ReportPilotPosition> reportPilotPositionByReport = reportPilotPositions.stream().collect(Collectors.toMap(p -> p.getReport().getReport(), Function.identity()));

        for (Report report : reports) {
            ReportPilotPosition reportPilotPosition = reportPilotPositionByReport.get(report.getReport());
            Position position = reportPilotPosition != null ? Position.create(reportPilotPosition) : Position.createOfflinePosition(report);
            track.trackData.add(position);

            if (track.pilotNumber == 0 && reportPilotPosition != null) {
                track.pilotNumber = reportPilotPosition.getPilotNumber();
            }
        }

        track.buildRanges();
        track.printRanges();
        track.buildFlights();

        return track;
    }

    public Collection<Flight1> getFlights() {
        return flights.stream().map(this::buildFlight1).collect(Collectors.toList());
    }

    private Flight1 buildFlight1(TrackedFlight trackedFlight) {
        Position takeoffPosition = trackedFlight.takeoffEvent.getNextRange().getFirstPosition();
        Position landingPosition = trackedFlight.landingEvent.getPreviousRange().getLastPosition();

        Flight1 flight1 = new Flight1();

        flight1.setPilotNumber(pilotNumber);
        flight1.setDateOfFlight(takeoffPosition.getReportInfo().getDt().toLocalDate());
        flight1.setCallsign(takeoffPosition.getCallsign());
        flight1.setAircraftType(takeoffPosition.getFpAircraftType());
        flight1.setAircraftRegNo(takeoffPosition.getRegNo());
        flight1.setDepartureIcao(takeoffPosition.getAirportIcao());
        flight1.setDepartureTime(JavaTime.hhmm.format(takeoffPosition.getReportInfo().getDt()));
        flight1.setArrivalIcao(landingPosition.getAirportIcao());
        flight1.setArrivalTime(JavaTime.hhmm.format(landingPosition.getReportInfo().getDt()));

        Map<String, Double> distanceAndTime = calculateDistanceAndTime(trackedFlight);
        flight1.setDistanceFlown(distanceAndTime.get("total.distance"));
        flight1.setAirTime(distanceAndTime.get("total.airtime"));
        flight1.setFlightplan(Flightplan.fromPosition(takeoffPosition));

        flight1.setTakeoff(Flight1.position(takeoffPosition));
        flight1.setLanding(Flight1.position(landingPosition));

        flight1.setComplete(true);
        flight1.setTrackingMode(trackedFlight.trackingMode.name());

        return flight1;
    }

    // future to add online/offline, flighttime/airtime, etc
    private Map<String, Double> calculateDistanceAndTime(TrackedFlight trackedFlight) {
        Map<String, Double> result = new HashMap<>();

        TrackedEvent currentEvent = trackedFlight.takeoffEvent;
        while (currentEvent != trackedFlight.landingEvent) {
            TrackedRange range = currentEvent.getNextRange();
            double distance;
            double duration;
            if (range.getType() == RangeType.Flying) {
                distance = range.getDistance();
                duration = range.getDuration().getSeconds() / 3600.0;
            } else if (range.getType() == RangeType.Offline) {
                distance = Geo.distance(range.previousEvent.previousRange.getLastPosition().getCoords(),
                        range.nextEvent.nextRange.getFirstPosition().getCoords());
                duration = range.getDuration().getSeconds() / 3600.0;
            } else {
                throw new IllegalStateException();
            }

            result.merge("total.distance", distance, Double::sum);
            result.merge("total.airtime", duration, Double::sum);

            currentEvent = range.nextEvent;
        }

        return result;
    }

    private void buildRanges() {
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

    private void printRanges() {
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

    private void buildFlights() {
        // to add support for incomplete flights
        for (TrackedEvent event = startOfTrack; event != endOfTrack; event = event.getNextRange().getNextEvent()) {
            if (event.getType() != EventType.Takeoff) {
                continue;
            }

            TrackedFlight flight = tryToBuildIdealFlight(event);
            if (flight != null) {
                flights.add(flight);
                continue;
            }
            flight = tryToTASFlight(event);
            if (flight != null) {
                flights.add(flight);
                continue;
            }
            flight = tryToEllipseFlight(event);
            if (flight != null) {
                flights.add(flight);
//                    continue;
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
                return new TrackedFlight(takeoffEvent, nextEvent, TrackingMode.Ideal);
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
                return new TrackedFlight(takeoffEvent, nextEvent, TrackingMode.TAS);
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
                Position lastPosition = lastFlyingRange.getLastPosition();
                double distance = Geo.distance(lastPosition.getCoords(), nextPosition.getCoords());
                double hours = JavaTime.hoursBetween(lastPosition.getReportInfo().getDt(), nextPosition.getReportInfo().getDt());
                int groundspeed = (int) (distance / hours);

                String aircraftType = flightplan.getAircraftType();
                if (aircraftType != null) {
                    Optional<AircraftPerformance> performance = AircraftPerformanceDatabase.getPerformance(aircraftType);
                    if (!performance.isPresent()) {
                        return null;
                    }

                    Integer ias = performance.get().getCruiseIasAtCruiseCeiling();
                    if (ias != null) {
                        int minAltitude = Math.min(lastPosition.getActualAltitude(), nextPosition.getActualAltitude());
                        int maxAltitude = Math.max(lastPosition.getActualAltitude(), nextPosition.getActualAltitude());

                        if (maxAltitude < 10000) {
                            ias = (int) (ias * 0.6); // initial climb or approach speed
                        } else if (maxAltitude < 20000) {
                            ias = (int) (ias * 0.8); // climb or descend speed
                        }

                        int minTas = (int) (Airspeed.iasToTas(ias, minAltitude) * 0.66);
                        int maxTas = (int) (Airspeed.iasToTas(ias, maxAltitude) * 1.33);

                        if (minTas <= groundspeed && groundspeed <= maxTas) {
                            // we can join two flying ranges divided by one offline range
                            canWeSkipThisRange = true;
                        }
                    }
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

    private TrackedFlight tryToEllipseFlight(TrackedEvent takeoffEvent) {
        TrackedRange lastFlyingRange = takeoffEvent.getNextRange();
        Flightplan flightplan = Flightplan.fromPosition(lastFlyingRange.getLastPosition());
        EllipseCriterion criterion = new EllipseCriterion(takeoffEvent.getNextRange().getFirstPosition(), flightplan);
        while (true) {
            TrackedEvent nextEvent = lastFlyingRange.getNextEvent();
            if (nextEvent == endOfTrack) {
                return null;
            } else if (nextEvent.getType() == EventType.Landing) {
                return new TrackedFlight(takeoffEvent, nextEvent, TrackingMode.Ellipse);
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

        TrackedEvent getNextEvent() {
            return nextEvent;
        }

        RangeType getType() {
            return type;
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
            return String.format("Range (%s) -> %s -> (%s), length %d, duration %s, distance %s",
                    (previousEvent != null ? previousEvent.type : "n/a"),
                    type.name().toUpperCase(),
                    (nextEvent != null ? nextEvent.type : "n/a"),
                    positions.size(),
                    JavaTime.toHhmm(getDuration()),
                    (distance != null ? new DecimalFormat("0.0").format(distance) : "n/a"));
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
        private TrackedEvent takeoffEvent;
        private TrackedEvent landingEvent;
        private TrackingMode trackingMode;

        public TrackedFlight(TrackedEvent takeoffEvent, TrackedEvent landingEvent, TrackingMode trackingMode) {
            this.takeoffEvent = takeoffEvent;
            this.landingEvent = landingEvent;
            this.trackingMode = trackingMode;
        }
    }

    enum TrackingMode {
        Ideal,
        TAS,
        Ellipse
    }
}
