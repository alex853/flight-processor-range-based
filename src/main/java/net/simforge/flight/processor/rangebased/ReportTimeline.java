package net.simforge.flight.processor.rangebased;

import net.simforge.networkview.core.report.ReportInfo;
import net.simforge.networkview.core.report.ReportRange;
import net.simforge.networkview.core.report.persistence.Report;
import net.simforge.networkview.core.report.persistence.ReportOpsService;

import java.util.ArrayList;
import java.util.List;

public class ReportTimeline {
    private final List<Report> reports;

    private ReportTimeline(List<Report> reports) {
        this.reports = new ArrayList<>(reports);
    }

    public static ReportTimeline load(ReportOpsService reportOpsService) {
        List<Report> reports = reportOpsService.loadAllReports();
        return new ReportTimeline(reports);
    }

    public ReportInfo getFirstReport() {
        return reports.get(0);
    }

    public ReportInfo getLastReport() {
        return reports.get(reports.size() - 1);
    }

    public ReportInfo findPreviousReport(String report) {
        // improvement - not optimal
        Report previous = null;
        for (Report current : reports) {
            int result = current.getReport().compareTo(report);
            if (result == 0) {
                return current;
            } else if (result > 0) {
                return previous;
            }
            previous = current;
        }
        return null;
    }

    public List<Report> getReportsInRange(ReportRange range) {
        // improvement - not optimal
        List<Report> result = new ArrayList<>();
        for (Report report : reports) {
            if (range.isWithin(report)) {
                result.add(report);
            }
        }
        return result;
    }
}
