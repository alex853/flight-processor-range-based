package net.simforge.flight.processor.rangebased.poc;

import net.simforge.commons.misc.Geo;
import net.simforge.commons.misc.JavaTime;
import net.simforge.flight.core.EllipseCriterion;
import net.simforge.flight.core.Flightplan;
import net.simforge.networkview.core.Network;
import net.simforge.networkview.core.report.ReportUtils;
import net.simforge.networkview.core.report.persistence.*;
import net.simforge.networkview.core.Position;

import java.io.IOException;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Test2 {

    private static int totalTakeoffs = 0;
    private static int totalLandings = 0;
    private static int totalFlights = 0;

    public static void main(String[] args) throws IOException {
        // load pilot track for some range
        // determine online/offline/takeoff/landing/touch&go events
        // determine ranges
        //    on ground
        //    flying
        // split ranges by abrupt movements (jumps) (ie jump event)
        // callsign change on ground requires to split range (flightplan change too if some defined plan is changed into another)

        // on ground section BEFORE takeoff - taxi-out section
        // is being determined as short (10-20 mins) on ground section SINCE first movement
        // on ground section AFTER landing - taxi-in section
        // is being determined as short (10-20 mins) on ground section TILL end of movement

        // on ground section between LANDING and then TAKEOFF without any sign of callsign or flightplan change
        // or taxi-out/taxi-in sections should be splitted by 2 halves

        // flight is sequence of sections - taxi out section, flying section, taxi in section and set of events

        ReportSessionManager reportSessionManager = new ReportSessionManager();

        List<Integer> pilotNumbers;
//        try (Session session = reportSessionManager.getSession(Network.VATSIM)) {
//            pilotNumbers = session
//                    .createQuery("select distinct(pilotNumber) from ReportPilotPosition")
//                    .list();
//        }
        pilotNumbers = Collections.singletonList(1340422);

//        ReportOpsService service = new BaseReportOpsService(reportSessionManager, Network.VATSIM);
        ReportOpsService service = new BaseReportOpsService(reportSessionManager, Network.VATSIM, 2020);
        List<Report> reports = service.loadAllReports();

        long lastPrintTs = System.currentTimeMillis();
        int counter = 0;
        for (Integer pilotNumber : pilotNumbers) {
            List<ReportPilotPosition> reportPilotPositions = service.loadPilotPositions(pilotNumber);
            Map<String, ReportPilotPosition> reportPilotPositionByReport = reportPilotPositions.stream().collect(Collectors.toMap(p -> p.getReport().getReport(), Function.identity()));

            Track track = new Track();

            for (Report report : reports) {
                ReportPilotPosition reportPilotPosition = reportPilotPositionByReport.get(report.getReport());
                Position position = reportPilotPosition != null ? Position.create(reportPilotPosition) : Position.createOfflinePosition(report);
                track.add(position);
            }

            track.buildRanges();
            track.printRanges();
            track.buildFlights();
            track.printFlights();
            track.printStats();

            counter++;
            long now = System.currentTimeMillis();
            if (now - lastPrintTs > 10000) {
                System.out.println(String.format("\t\t%d of %d done", counter, pilotNumbers.size()));
                lastPrintTs = now;
            }
        }

        reportSessionManager.dispose();
    }

    private static class Track {
        private List<Position> trackData = new ArrayList<>();

        public void add(Position position) {
            trackData.add(position);
        }

        private Position trackDataByReport(String report) {
            for (Position position : trackData) {
                if (position.getReportInfo().getReport().equals(report)) {
                    return position;
                }
            }
            return null;
        }

        Event1 startOfTrack;
        Event1 endOfTrack;

        public void buildRanges() {
            Iterator<Position> iterator = trackData.iterator();
            Position startPosition = iterator.next();

            startOfTrack = Event1.startOfTrack(startPosition.getReportInfo().getReport());

            Position position = startPosition;

            Range range = startOfTrack.startNextRange(position);

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

//            System.out.println();
//            System.out.println("online/offline section completed");
//            System.out.println();

//            printRanges(startOfTrack, endOfTrack);

            Event1 event = startOfTrack;
            while (event != endOfTrack) {
                range = event.getNextRange();
                if (range.getType() != RangeType.Online) {
                    event = range.getNextEvent();
                    continue;
                }

                splitByTakeoffLandingEvents(range);

                event = range.getNextEvent();
            }

//            System.out.println();
//            System.out.println("takeoff/landing section completed");
//            System.out.println();

//            printRanges(startOfTrack, endOfTrack);

            event = startOfTrack;
            while (event != endOfTrack) {
                if (event.getType() == EventType.Landing) {
                    Event1 landingEvent = event;
                    Range onGroundRange = event.getNextRange();
                    if (onGroundRange.getType() == RangeType.OnGround) {
                        if (onGroundRange.getPositions().size() == 1) {
                            Event1 takeoffEvent = onGroundRange.getNextEvent();
                            if (takeoffEvent.getType() == EventType.Takeoff) {
                                event = putTouchAndGoEvent(landingEvent, takeoffEvent);
                            }
                        }
                    }
                }

                range = event.getNextRange();
                event = range.getNextEvent();
            }

//            System.out.println();
//            System.out.println("touch&go section completed");
//            System.out.println();

//            printRanges();
        }

        private Event1 putTouchAndGoEvent(Event1 landingEvent, Event1 takeoffEvent) {
            Event1 touchAndGoEvent = new Event1(landingEvent.previousRange, EventType.TouchAndGo, landingEvent.report);
            touchAndGoEvent.nextRange = takeoffEvent.nextRange;
            takeoffEvent.nextRange.previousEvent = touchAndGoEvent;
            return touchAndGoEvent;
        }

        private void splitByTakeoffLandingEvents(Range outerRange) {
            Range currentRange = outerRange;
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
                    Event1 newEvent = currentRange.splitByEvent(EventType.Takeoff, previousPosition);
                    Range previousRange = newEvent.getPreviousRange();
                    previousRange.setType(RangeType.OnGround);
                    currentRangeType = RangeType.Flying;
                    currentRange = newEvent.getNextRange();
                } else {
                    Event1 newEvent = currentRange.splitByEvent(EventType.Landing, currentPosition);
                    Range previousRange = newEvent.getPreviousRange();
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
            Range range;
            Event1 event = startOfTrack;
            while (event != endOfTrack) {
                System.out.println("Event " + event);
                range = event.getNextRange();
                System.out.println("\t\t" + range);
                event = range.getNextEvent();
            }
            System.out.println("Event " + event);
        }

        List<Flight1> flights = new ArrayList<>();

        public void printFlights() {
            flights.forEach(System.out::println);
        }

        public void buildFlights() {
            for (Event1 event = startOfTrack; event != endOfTrack; event = event.getNextRange().getNextEvent()) {
                if (event.getType() != EventType.Takeoff) {
                    continue;
                }

                Flight1 flight = tryToBuildIdealFlight(event);
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

        private Flight1 tryToBuildIdealFlight(Event1 takeoffEvent) {
            Event1 event = takeoffEvent;
            while (event != endOfTrack) {
                Range nextRange = event.getNextRange();
                if (nextRange.getType() != RangeType.Flying) {
                    return null;
                }

                Event1 nextEvent = nextRange.getNextEvent();
                if (nextEvent.getType() == EventType.Landing) {
                    return new Flight1(takeoffEvent, nextEvent, FlightMethod.Ideal);
                } else if (nextEvent.getType() == EventType.TouchAndGo) {
                    event = nextEvent;
                    // and continue
                } else {
                    return null;
                }
            }

            return null;
        }

        private Flight1 tryToTASFlight(Event1 takeoffEvent) {
            Range lastFlyingRange = takeoffEvent.getNextRange();
            Flightplan flightplan = Flightplan.fromPosition(lastFlyingRange.getLastPosition());
            while (true) {
                Event1 nextEvent = lastFlyingRange.getNextEvent();
                if (nextEvent == endOfTrack) {
                    return null;
                } else if (nextEvent.getType() == EventType.Landing) {
                    return new Flight1(takeoffEvent, nextEvent, FlightMethod.TAS);
                } else if (nextEvent.getType() == EventType.TouchAndGo) {
                    lastFlyingRange = nextEvent.getNextRange();
                    continue;
                    // and continue
                }

                Range nextRange = nextEvent.getNextRange();
                boolean canWeSkipThisRange = false;
                RangeType nextRangeType = nextRange.getType();
                Range nextFlyingRange = null;
                if (nextRangeType == RangeType.Offline) {
                    nextFlyingRange = getNextFlyingRange(nextRange);
                    if (nextFlyingRange == null) {
                        return null;
                    }

                    // can we join lastFlyingRange and nextFlyingRange
                    Position nextPosition = nextFlyingRange.getPositions().get(0);
                    Position lastPosition = lastFlyingRange.getLastPosition();
                    double distance = Geo.distance(lastPosition.getCoords(), nextPosition.getCoords());
                    double hours = JavaTime.hoursBetween(lastPosition.getReportInfo().getDt(), nextPosition.getReportInfo().getDt());
                    int groundspeed = (int) (distance / hours);

                    String aircraftType = flightplan.getAircraftType();
                    if (aircraftType != null) {
                        Integer ias = getCruiseIas(aircraftType);
                        if (ias != null) {
                            int minAltitude = Math.min(lastPosition.getActualAltitude(), nextPosition.getActualAltitude());
                            int maxAltitude = Math.max(lastPosition.getActualAltitude(), nextPosition.getActualAltitude());

                            if (maxAltitude < 10000) {
                                ias = (int) (ias * 0.6); // initial climb or approach speed
                            } else if (maxAltitude < 20000) {
                                ias = (int) (ias * 0.8); // climb or descend speed
                            }

                            int minTas = (int) (iasToTas(ias, minAltitude) * 0.66);
                            int maxTas = (int) (iasToTas(ias, maxAltitude) * 1.33);

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

        private Flight1 tryToEllipseFlight(Event1 takeoffEvent) {
            Range lastFlyingRange = takeoffEvent.getNextRange();
            Flightplan flightplan = Flightplan.fromPosition(lastFlyingRange.getLastPosition());
            EllipseCriterion criterion = new EllipseCriterion(takeoffEvent.getNextRange().getPositions().get(0), flightplan);
            while (true) {
                Event1 nextEvent = lastFlyingRange.getNextEvent();
                if (nextEvent == endOfTrack) {
                    return null;
                } else if (nextEvent.getType() == EventType.Landing) {
                    return new Flight1(takeoffEvent, nextEvent, FlightMethod.Ellipse);
                } else if (nextEvent.getType() == EventType.TouchAndGo) {
                    lastFlyingRange = nextEvent.getNextRange();
                    continue;
                    // and continue
                }

                Range nextRange = nextEvent.getNextRange();
                boolean canWeSkipThisRange = false;
                RangeType nextRangeType = nextRange.getType();
                Range nextFlyingRange = null;
                if (nextRangeType == RangeType.Offline) {
                    nextFlyingRange = getNextFlyingRange(nextRange);
                    if (nextFlyingRange == null) {
                        return null;
                    }

                    // can we join lastFlyingRange and nextFlyingRange
                    Position nextPosition = nextFlyingRange.getPositions().get(0);

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

        private int iasToTas(int ias, int altitude) {
            return (int) (ias * .02 * altitude / 1000) + ias;
        }

        private Range getNextFlyingRange(Range range) {
            Event1 event = range.getNextEvent();
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

        private Integer getCruiseIas(String aircraftType) {
            if (aircraftType.startsWith("B7")
                    || aircraftType.startsWith("A3")
                    || aircraftType.equals("CRJ7")) {
                return 280;
            }

            switch (aircraftType) {
                case "AT72":
                    return 185;
                case "DHC6":
                    return 170;
                case "M20T":
                    return 140;
            }

            throw new IllegalArgumentException("Unknown aircraft type '" + aircraftType + "'");
        }

        public void printStats() {
            Event1 event = startOfTrack;
            while (event != endOfTrack) {
                if (event.getType() == EventType.Takeoff) {
                    totalTakeoffs++;
                } else if (event.getType() == EventType.Landing) {
                    totalLandings++;
                }
                event = event.getNextRange().getNextEvent();
            }

            totalFlights += flights.size();

            int unrecognizedTakeoffs = totalTakeoffs - totalFlights;
            int unrecognizedLandings = totalLandings - totalFlights;

            System.out.println(String.format("Stats: total T/Os %d    LANDs %d    Flights %d    UNRECONG T/O %d    LANDs %d",
                    totalTakeoffs, totalLandings, totalFlights, unrecognizedTakeoffs, unrecognizedLandings));
        }
    }

    private static class Flight1 {
        private Event1 takeoffEvent;
        private Event1 landingEvent;
        private FlightMethod method;

        public Flight1(Event1 takeoffEvent, Event1 landingEvent, FlightMethod method) {
            this.takeoffEvent = takeoffEvent;
            this.landingEvent = landingEvent;
            this.method = method;
        }

        @Override
        public String toString() {
            return "Flight "
                    + takeoffEvent.getNextRange().getPositions().get(0).getAirportIcao()
                    + " - "
                    + landingEvent.getPreviousRange().getLastPosition().getAirportIcao()
                    + "      (" + method + ")";
        }
    }

    private enum FlightMethod {
        Ideal, TAS, Ellipse
    }

    private static class Event1 {
        private Range previousRange;
        private EventType type;
        private String report;
        private Range nextRange;

        Event1(Range previousRange, EventType type, String report) {
            this.previousRange = previousRange;
            this.type = type;
            this.report = report;

            if (previousRange != null) {
                previousRange.nextEvent = this;
            }
        }

        public static Event1 startOfTrack(String report) {
            return new Event1(null, EventType.StartOfTrack, report);
        }

        public Range startNextRange(Position position) {
            Range range = new Range();
            range.previousEvent = this;
            range.type = Range.getRangeType(position);
            range.positions.add(position);
            this.nextRange = range;
            return range;
        }

        public Range getNextRange() {
            return nextRange;
        }

        public Range getPreviousRange() {
            return previousRange;
        }

        @Override
        public String toString() {
            return String.format("Event %s", type.name().toUpperCase());
        }

        public EventType getType() {
            return type;
        }
    }

    private static class Range {
        private Event1 previousEvent;
        private RangeType type;
        private List<Position> positions = new ArrayList<>();
        private Event1 nextEvent;

        private Range() {
        }

        public static RangeType getRangeType(Position position) {
            if (!position.isPositionKnown()) {
                return RangeType.Offline;
            } else {
                return RangeType.Online;
            }
        }

        boolean offer(Position position) {
            RangeType rangeType = getRangeType(position);
            if (rangeType != this.type) {
                return false;
            }
            positions.add(position);
            return true;
        }

        public Range wentOnline(Position position) {
            Event1 onlineEvent = new Event1(this, EventType.Online, position.getReportInfo().getReport());
            return onlineEvent.startNextRange(position);
        }

        public Range wentOffline(Position position) {
            Event1 offlineEvent = new Event1(this, EventType.Offline, getLastPosition().getReportInfo().getReport());
            return offlineEvent.startNextRange(position);
        }

        public Position getLastPosition() {
            return positions.get(positions.size() - 1);
        }

        public Event1 endOfTrack() {
            return new Event1(this, EventType.EndOfTrack, getLastPosition().getReportInfo().getReport());
        }

        public Event1 splitByEvent(EventType eventType, Position splitPosition) {
            int splitIndex = positions.indexOf(splitPosition);

            Range leftRange = new Range();
            this.previousEvent.nextRange = leftRange;
            leftRange.previousEvent = this.previousEvent;
            leftRange.type = this.type;
            leftRange.positions = this.positions.subList(0, splitIndex + 1);

            Event1 newEvent = new Event1(leftRange, eventType, splitPosition.getReportInfo().getReport());

            Range rightRange = new Range();
            newEvent.nextRange = rightRange;
            rightRange.previousEvent = newEvent;
            rightRange.type = this.type;
            rightRange.positions = this.positions.subList(splitIndex, this.positions.size());
            rightRange.nextEvent = this.nextEvent;
            this.nextEvent.previousRange = rightRange;

            return newEvent;
        }

        public Event1 getNextEvent() {
            return nextEvent;
        }

        public RangeType getType() {
            return type;
        }

        public void setType(RangeType type) {
            this.type = type;
        }

        public List<Position> getPositions() {
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

        private Double getDistance() {
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

        public Duration getDuration() {
            return Duration.between(ReportUtils.fromTimestampJava(previousEvent.report), ReportUtils.fromTimestampJava(nextEvent.report));
        }
    }

    private enum RangeType {
        Online,
        OnGround,
        Flying,
        Offline
    }

    private enum EventType {
        Takeoff,
        Landing,
        StartOfTrack,
        Online,
        Offline,
        EndOfTrack,
        TouchAndGo
    }
}
