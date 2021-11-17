package net.simforge.flight.processor.rangebased;

import net.simforge.networkview.core.report.ReportInfo;
import net.simforge.networkview.core.report.ReportInfoDto;
import net.simforge.networkview.core.report.ReportRange;

import java.util.ArrayList;
import java.util.List;

class PilotContext {
    private int pilotNumber;
    private List<ReportRange> processedRanges = new ArrayList<>();
    private ReportInfoDto lastIncrementallyProcessedReport;

    public PilotContext(int pilotNumber) {
        this.pilotNumber = pilotNumber;
    }

    public int getPilotNumber() {
        return pilotNumber;
    }

    public PilotContext makeCopy() {
        PilotContext copy = new PilotContext(pilotNumber);
        copy.processedRanges.addAll(this.processedRanges);
        return copy;
    }

    public boolean isReportProcessed(ReportInfo report) {
        for (ReportRange processedRange : processedRanges) {
            if (processedRange.isWithin(report)) {
                return true;
            }
        }
        return false;
    }

    public ReportRange getNonProcessedRange(ReportInfo report) {
        if (processedRanges.isEmpty()) {
            return null;
        }
        throw new UnsupportedOperationException();
    }

    public void addProcessedRange(ReportRange processedReportsRange, ReportRange completedFlightsRange) {
        if (processedRanges.isEmpty()) {
            processedRanges.add(completedFlightsRange);
            return;
        }
        throw new UnsupportedOperationException();
    }

    public ReportInfo getLastIncrementallyProcessedReport() {
        return lastIncrementallyProcessedReport;
    }

    public void setLastIncrementallyProcessedReport(ReportInfo lastIncrementallyProcessedReport) {
        this.lastIncrementallyProcessedReport = new ReportInfoDto(lastIncrementallyProcessedReport);
    }
}
