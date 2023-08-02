package net.simforge.flight.core.deprecated.poc;

import net.simforge.commons.misc.Geo;
import net.simforge.commons.misc.JavaTime;
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

@Deprecated
public class Test {
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

//        int pilotNumber = 1138460;
//        int pilotNumber = 1001634;
//        int pilotNumber = 913904;
//        int pilotNumber = 1283157;
        int pilotNumber = 898859;

        ReportSessionManager reportSessionManager = new ReportSessionManager();
        ReportOpsService service = new BaseReportOpsService(reportSessionManager, Network.VATSIM);
//        ReportOpsService service = new BaseReportOpsService(reportSessionManager, Network.VATSIM, 2020);
//        ReportOpsService service = new ReportOpsServiceCsvDatasourceWrapper("./src/test/resources/snapshots/pilot-913904_2018-11-23.csv");
//        ReportOpsService service = new ReportOpsServiceCsvDatasourceWrapper("./src/test/resources/snapshots/pilot-1283157_from-2015182747_amount-250.csv");
        List<Report> reports = service.loadAllReports();
        List<ReportPilotPosition> reportPilotPositions = service.loadPilotPositions(pilotNumber);
        Map<String, ReportPilotPosition> reportPilotPositionByReport = reportPilotPositions.stream().collect(Collectors.toMap(p -> p.getReport().getReport(), Function.identity()));
        reportSessionManager.dispose();

        Track track = new Track();

        for (Report report : reports) {
            ReportPilotPosition reportPilotPosition = reportPilotPositionByReport.get(report.getReport());
            Position position = reportPilotPosition != null ? Position.create(reportPilotPosition) : Position.createOfflinePosition(report);
            track.add(position);
        }

//        track.recognizeTakeoffsLandings();
//        track.recognizeTouchAndGoes();

//        track.printEvents();

        track.buildRanges();

        track.buildIdealFlights();

        track.printStats();
    }

    private static class Track {
        private List<Position> trackData = new ArrayList<>();
        private Map<String, Set<EventType>> eventsByTime = new TreeMap<>();

        public void add(Position position) {
            trackData.add(position);
        }

        public void recognizeTakeoffsLandings() {
            for (int i = 1; i < trackData.size(); i++) {
                Position prevPosition = trackData.get(i - 1);
                Position position = trackData.get(i);

//            boolean wentOnline = !prevPosition.isPositionKnown() && position.isPositionKnown();
//            boolean wentOffline = prevPosition.isPositionKnown() && !position.isPositionKnown();
                boolean bothPositionsKnown = prevPosition.isPositionKnown() && position.isPositionKnown();

                boolean takeoff = bothPositionsKnown && prevPosition.isOnGround() && !position.isOnGround();
                boolean landing = bothPositionsKnown && !prevPosition.isOnGround() && position.isOnGround();

                if (takeoff) {
                    Set<EventType> events = this.eventsByTime.computeIfAbsent(
                            prevPosition.getReportInfo().getReport(),
                            set -> new TreeSet<>());
                    events.add(EventType.Takeoff);
                }

                if (landing) {
                    Set<EventType> eventList = this.eventsByTime.computeIfAbsent(
                            position.getReportInfo().getReport(),
                            set -> new TreeSet<>());
                    eventList.add(EventType.Landing);
                }
            }
        }

        public void recognizeTouchAndGoes() {
            for (Map.Entry<String, Set<EventType>> entry : eventsByTime.entrySet()) {
                Set<EventType> events = entry.getValue();
                if (events.contains(EventType.Takeoff) && events.contains(EventType.Landing)) {
                    events.remove(EventType.Takeoff);
                    events.remove(EventType.Landing);
                    events.add(EventType.TouchAndGo);
                }
            }
        }

        public void printEvents() {
            for (Map.Entry<String, Set<EventType>> entry : eventsByTime.entrySet()) {
                Position position = trackDataByReport(entry.getKey());
                System.out.println(ReportUtils.log(entry.getKey())
                        + " -> " + entry.getValue()
                        + " | " + position.getStatus()
                        + " " + Flightplan.fromPosition(position));
            }
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

            System.out.println();
            System.out.println("online/offline section completed");
            System.out.println();

            printRanges(startOfTrack, endOfTrack);

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

            System.out.println();
            System.out.println("takeoff/landing section completed");
            System.out.println();

            printRanges(startOfTrack, endOfTrack);

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

            System.out.println();
            System.out.println("touch&go section completed");
            System.out.println();

            printRanges(startOfTrack, endOfTrack);
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

        private void printRanges(Event1 startOfTrack, Event1 endOfTrack) {
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

        public void buildIdealFlights() {
            for (Event1 event = startOfTrack; event != endOfTrack; event = event.getNextRange().getNextEvent()) {
                if (event.getType() != EventType.Takeoff) {
                    continue;
                }

                Flight1 flight = tryToBuildIdealFlight(event);
                if (flight != null) {
                    flights.add(flight);
                }
            }

            flights.forEach(System.out::println);
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
                    return new Flight1(takeoffEvent, nextEvent);
                } else if (nextEvent.getType() == EventType.TouchAndGo) {
                    event = nextEvent;
                    // and continue
                } else {
                    return null;
                }
            }

            return null;
        }

        public void printStats() {
            int totalTakeoffs = 0;
            int totalLandings = 0;

            Event1 event = startOfTrack;
            while (event != endOfTrack) {
                if (event.getType() == EventType.Takeoff) {
                    totalTakeoffs++;
                } else if (event.getType() == EventType.Landing) {
                    totalLandings++;
                }
                event = event.getNextRange().getNextEvent();
            }

            int unrecognizedTakeoffs = totalTakeoffs - flights.size();
            int unrecognizedLandings = totalLandings - flights.size();

            System.out.println(String.format("Stats: total T/Os %d    LANDs %d    Flights %d    UNRECONG T/O %d    LANDs %d",
                    totalTakeoffs, totalLandings, flights.size(), unrecognizedTakeoffs, unrecognizedLandings));
        }
    }

    private static class Flight1 {
        private Event1 takeoffEvent;
        private Event1 landingEvent;

        public Flight1(Event1 takeoffEvent, Event1 landingEvent) {
            this.takeoffEvent = takeoffEvent;
            this.landingEvent = landingEvent;
        }

        @Override
        public String toString() {
            return "Flight "
                    + takeoffEvent.getNextRange().getPositions().get(0).getAirportIcao()
                    + " - "
                    + landingEvent.getPreviousRange().getLastPosition().getAirportIcao();
        }
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
        StartOfTrack, Online, Offline, EndOfTrack, TouchAndGo
    }

    private static class Flight {
        private int pilotNumber;
        private String status;
        private String callsign;
        private String aircraftType;
        private String aircraftRegNo;
        private String origin;
        private String destination;
        private String plannedOrigin;
        private String plannedDestination;
        private Double distanceFlown;
        private Double flownTime;
        private Double networkTime;
        private List<EventInfo> events;
    }

    private static class EventInfo {
        private EventType eventType;
        private String report;
    }
}
