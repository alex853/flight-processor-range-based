package net.simforge.flight.core.deprecated;

import net.simforge.flight.processor.rangebased.ReportTimeline;
import net.simforge.networkview.core.report.ReportInfo;
import net.simforge.networkview.core.report.ReportRange;
import net.simforge.networkview.core.report.ReportUtils;
import net.simforge.networkview.core.report.persistence.Report;
import net.simforge.networkview.core.report.persistence.ReportPilotPosition;

import java.util.*;
import java.util.stream.Collectors;

@Deprecated
public class CachedPositions {
    private static final Map<Integer, PilotTrack> tracks = new HashMap<>();

    public static void storePilotPositions(int pilotNumber, List<ReportPilotPosition> positions, ReportTimeline timeline, ReportInfo sinceReport, ReportInfo tillReport) {
        PilotTrack track = tracks.get(pilotNumber);
        if (track == null) {
            track = new PilotTrack(pilotNumber);
        }
        tracks.put(pilotNumber, track);
        track.storePositions(positions, timeline, sinceReport, tillReport);
    }

    public static List<ReportPilotPosition> findPilotPositions(int pilotNumber, ReportInfo sinceReport, ReportInfo tillReport) {
        PilotTrack track = tracks.get(pilotNumber);
        if (track == null) {
            return null;
        }
        return track.findPositions(sinceReport, tillReport);
    }

    public static void cleanupUselessPilotPositions(int pilotNumber, ReportInfo tillReport) {
        PilotTrack track = tracks.get(pilotNumber);
        if (track == null) {
            return;
        }
        track.cleanupPositions(tillReport);
    }

    public static void consumeReportPositions(Report report, List<ReportPilotPosition> positions) {
        Map<Integer, ReportPilotPosition> newPositions = positions.stream().collect(Collectors.toMap(ReportPilotPosition::getPilotNumber, p -> p));

        tracks.forEach((pilotNumber, track) -> {
            ReportPilotPosition position = newPositions.get(pilotNumber);
            if (position == null) {
                track.attachPositionIfPossible(stubPosition(report));
            } else {
                track.attachPositionIfPossible(position);
            }
        });
    }

    private static ReportPilotPosition stubPosition(Report report) {
        ReportPilotPosition stub = new StubReportPilotPosition(); // todo ak2 optimize it
        stub.setReport(report);
        return stub;
    }

    private static class StubReportPilotPosition extends ReportPilotPosition {

    }

    private static class PilotTrack {
        private final int pilotNumber;
        private LinkedList<ReportPilotPosition> positions = new LinkedList<>();
        private ReportInfo sinceReport;
        private ReportInfo tillReport;

        public PilotTrack(int pilotNumber) {
            this.pilotNumber = pilotNumber;
        }

        public List<ReportPilotPosition> findPositions(ReportInfo sinceReport, ReportInfo tillReport) {
            if (ReportUtils.isTimestampLess(sinceReport.getReport(), this.sinceReport.getReport())
                    || ReportUtils.isTimestampGreater(tillReport.getReport(), this.tillReport.getReport())) {
                return null;
            }
            ReportRange range = ReportRange.between(sinceReport, tillReport);
            return positions.stream()
                    .filter(p -> range.isWithin(p.getReport()) && !(p instanceof StubReportPilotPosition))
                    .collect(Collectors.toList());
        }

        public void cleanupPositions(ReportInfo tillReport) {
            String calculatedDateTime = ReportUtils.toTimestamp(tillReport.getDt().minusHours(72));
            while (!positions.isEmpty() && ReportUtils.isTimestampGreater(calculatedDateTime, positions.getFirst().getReport().getReport())) {
                sinceReport = positions.getFirst().getReport();
                positions.removeFirst();
            }
        }

        public void storePositions(List<ReportPilotPosition> positions, ReportTimeline timeline, ReportInfo sinceReport, ReportInfo tillReport) {
            this.sinceReport = sinceReport;
            this.tillReport = tillReport;
            this.positions.clear();

            String calculatedDateTime = ReportUtils.toTimestamp(tillReport.getDt().minusHours(72));
            if (ReportUtils.isTimestampGreater(calculatedDateTime, sinceReport.getReport())) {
                sinceReport = timeline.findPreviousReport(calculatedDateTime);
            }

            List<Report> reports = timeline.getReportsInRange(ReportRange.between(sinceReport, tillReport));
            Map<String, ReportPilotPosition> newPositions = positions.stream().collect(Collectors.toMap(p -> p.getReport().getReport(), p -> p));

            this.positions.addAll(reports.stream().map(r -> {
                ReportPilotPosition position = newPositions.get(r.getReport());
                if (position == null) {
                    return stubPosition(r);
                } else {
                    return position;
                }
            }).collect(Collectors.toList()));
        }

        public void attachPositionIfPossible(ReportPilotPosition position) {
            if (ReportUtils.isTimestampLessOrEqual(position.getReport().getReport(), this.tillReport.getReport())) {
                return;
            }
            this.positions.add(position);
            this.tillReport = position.getReport();
        }
    }
}
