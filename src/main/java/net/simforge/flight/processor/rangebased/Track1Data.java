package net.simforge.flight.processor.rangebased;

import net.simforge.commons.bm.BMC;
import net.simforge.networkview.core.Position;
import net.simforge.networkview.core.report.ReportInfo;
import net.simforge.networkview.core.report.ReportRange;
import net.simforge.networkview.core.report.persistence.Report;
import net.simforge.networkview.core.report.persistence.ReportPilotPosition;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Track1Data {
    private final int pilotNumber;
    private final LinkedList<Position> trackData = new LinkedList<>();

    private Track1Data(int pilotNumber) {
        this.pilotNumber = pilotNumber;
    }

    public static Track1Data forPilot(int pilotNumber) {
        return new Track1Data(pilotNumber);
    }

    // todo ak2 split it into 2 methods - store sequence and attach point
    public boolean storePositions(ReportTimeline timeline, ReportRange loadedRange, List<ReportPilotPosition> loadedPositions) {
        try (BMC ignored = BMC.start("Track1Data.storePositions")) {
            Optional<ReportRange> existingRange = getRange();
            if (!existingRange.isPresent()) {
                putPositionsToTrackData(timeline, loadedRange, loadedPositions);
                return true;
            }

            ReportRange intersection = existingRange.get().intersect(loadedRange);
            boolean hasIntersection = intersection != null;

            if (hasIntersection) {
                return false;
            }

            ReportInfo existingTill = existingRange.get().getTill();
            ReportInfo loadedSince = loadedRange.getSince();

            List<Report> joiningRange = timeline.getReportsInRange(ReportRange.between(existingTill, loadedSince));
            if (joiningRange.size() > 2) {
                return false;
            }

            putPositionsToTrackData(timeline, loadedRange, loadedPositions);
            return true;
        }
    }

    private void putPositionsToTrackData(ReportTimeline timeline, ReportRange loadedRange, List<ReportPilotPosition> loadedPositions) {
        Map<String, ReportPilotPosition> reportPilotPositionByReport = loadedPositions.stream().collect(Collectors.toMap(p -> p.getReport().getReport(), Function.identity()));

        List<Report> reports = timeline.getReportsInRange(loadedRange);
        for (Report report : reports) {
            ReportPilotPosition reportPilotPosition = reportPilotPositionByReport.get(report.getReport());
            Position position = reportPilotPosition != null ? Position.create(reportPilotPosition) : Position.createOfflinePosition(report);
            trackData.add(position);
        }
    }

    public void clearPositions() {
        trackData.clear();
    }

    public Optional<List<Position>> getPositions(ReportRange range) {
        try (BMC ignored = BMC.start("Track1Data.getPositions")) {
            Optional<ReportRange> existingRange = getRange();
            if (!existingRange.isPresent()) {
                return Optional.empty();
            }

            if (!existingRange.get().isWithin(range.getSince())
                    || !existingRange.get().isWithin(range.getTill())) {
                return Optional.empty();
            }

            ReportRange intersection = existingRange.get().intersect(range);
            return Optional.of(trackData.stream()
                    .filter(p -> intersection.isWithin(p.getReportInfo()))
                    .collect(Collectors.toList()));
        }
    }

    public Optional<ReportRange> getRange() {
        if (trackData.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ReportRange.between(trackData.getFirst().getReportInfo(), trackData.getLast().getReportInfo()));
    }

    public int size() {
        return trackData.size();
    }

    public int getPilotNumber() {
        return pilotNumber;
    }

    public int removePositionsOlderThanTimestamp(String thresholdTimestamp) {
        int counter = 0;
        while (!trackData.isEmpty() && NewMethods.isEarlierThan(trackData.get(0).getReportInfo(), thresholdTimestamp)) {
            trackData.removeFirst();
            counter++;
        }
        return counter;
    }

    public boolean hasOnlinePositionsLaterThan(String thresholdTimestamp) {
        Iterator<Position> reversedIterator = trackData.descendingIterator();
        while (reversedIterator.hasNext()) {
            Position position = reversedIterator.next();
            if (NewMethods.isEarlierThan(position.getReportInfo(), thresholdTimestamp)) {
                break;
            }
            if (position.isPositionKnown()) {
                return true;
            }
        }
        return false;
    }
}
