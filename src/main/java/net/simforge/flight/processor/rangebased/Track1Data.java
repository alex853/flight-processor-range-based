package net.simforge.flight.processor.rangebased;

import net.simforge.commons.bm.BMC;
import net.simforge.networkview.core.Position;
import net.simforge.networkview.core.report.ReportInfo;
import net.simforge.networkview.core.report.ReportRange;
import net.simforge.networkview.core.report.persistence.Report;
import net.simforge.networkview.core.report.persistence.ReportPilotPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Track1Data {
    private static final Logger log = LoggerFactory.getLogger(Track1Data.class);

    private final int pilotNumber;
    private List<Position> trackData = new ArrayList<>();

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
            position = Position.compactify(position);
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
                log.warn("Pilot {} - getPositions - no existing range", pilotNumber);
                return Optional.empty();
            }

            //noinspection unchecked
            int left = Collections.binarySearch(trackData, range.getSince(), trackDataComparator);
            //noinspection unchecked
            int right = Collections.binarySearch(trackData, range.getTill(), trackDataComparator);

            if (left < 0) {
                left = -(left + 1);
                if (left == 0) {
                    log.warn("Pilot {} - getPositions - 'since' report is earlier that the earliest - no positions will be returned", pilotNumber);
                    return Optional.empty(); // 'since' report is earlier that the earliest - no positions will be returned
                } else if (left == trackData.size()) {
                    log.warn("Pilot {} - getPositions - 'since' report is sooner that the soonest - no positions will be returned", pilotNumber);
                    return Optional.empty(); // 'since' report is sooner that the soonest - no positions will be returned
                }
            }

            if (right < 0) {
                right = -(right + 1);
                if (right == 0) {
                    log.warn("Pilot {} - getPositions - 'till' report is earlier that the earliest - no positions will be returned", pilotNumber);
                    return Optional.empty(); // 'till' report is earlier that the earliest - no positions will be returned
                } else if (right == trackData.size()) {
                    log.warn("Pilot {} - getPositions - 'till' report is sooner that the soonest - no positions will be returned", pilotNumber);
                    return Optional.empty(); // 'till' report is sooner that the soonest - no positions will be returned
                }
            }

            return Optional.of(trackData.subList(left, right + 1));
        }
    }

    public Optional<ReportRange> getRange() {
        if (trackData.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ReportRange.between(trackData.get(0).getReportInfo(), trackData.get(trackData.size() - 1).getReportInfo()));
    }

    public int size() {
        return trackData.size();
    }

    public int getPilotNumber() {
        return pilotNumber;
    }

    public void removePositionsOlderThanTimestamp(String thresholdTimestamp) {
        //noinspection unchecked
        int index = Collections.binarySearch(trackData, thresholdTimestamp, trackDataComparator);

        if (index < 0) {
            index = -(index + 1);
            index--; // this is because we did not find exact match and we need to keep one more report in the list to prevent reloading
        }

        if (index == trackData.size()) {
            trackData.clear();
            return;
        }

        if (index <= 0) {
            return;
        }

        trackData = new ArrayList<>(trackData.subList(index, trackData.size()));
    }

    public boolean hasOnlinePositionsLaterThan(String thresholdTimestamp) {
        for (int index = trackData.size() - 1; index >= 0; index--) {
            Position position = trackData.get(index);

            if (NewMethods.isEarlierThan(position.getReportInfo(), thresholdTimestamp)) {
                break;
            }

            if (position.isPositionKnown()) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("rawtypes")
    private static final Comparator trackDataComparator = new Comparator() {
        @Override
        public int compare(Object o1, Object o2) {
            return getReport(o1).compareTo(getReport(o2));
        }

        private String getReport(Object o) {
            return o instanceof Position ? ((Position) o).getReportInfo().getReport() :
                    o instanceof ReportInfo ? ((ReportInfo) o).getReport() :
                            (String) o;
        }
    };
}
