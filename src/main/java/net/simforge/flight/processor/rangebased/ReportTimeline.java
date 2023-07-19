package net.simforge.flight.processor.rangebased;

import net.simforge.commons.bm.BMC;
import net.simforge.networkview.core.report.ReportInfo;
import net.simforge.networkview.core.report.ReportRange;
import net.simforge.networkview.core.report.persistence.Report;
import net.simforge.networkview.core.report.persistence.ReportOpsService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class ReportTimeline {

    private static final Comparator binarySearchComparator = (o1, o2) -> {
        String r1 = o1 instanceof Report ? ((Report) o1).getReport() : (String) o1;
        String r2 = o2 instanceof Report ? ((Report) o2).getReport() : (String) o2;
        return String.CASE_INSENSITIVE_ORDER.compare(r1, r2);
    };

    private final Report[] reports;

    private ReportTimeline(List<Report> reports) {
        this.reports = reports.toArray(new Report[0]);
    }

    public static ReportTimeline load(ReportOpsService reportOpsService) {
        List<Report> reports = reportOpsService.loadAllReports();
        return new ReportTimeline(reports);
    }

    public ReportInfo getFirstReport() {
        if (reports.length == 0) {
            return null;
        }
        return reports[0];
    }

    public ReportInfo getLastReport() {
        if (reports.length == 0) {
            return null;
        }
        return reports[reports.length - 1];
    }

    public ReportInfo findPreviousReport(String report) {
        try (BMC ignored = BMC.start("ReportTimeline.findPreviousReport")) {
            //noinspection unchecked
            int index = Arrays.binarySearch(reports, report, binarySearchComparator);

            if (index >= 0) {
                // we found exact match, and we need to return __previous__ report
                if (index > 0) {
                    return reports[index - 1];
                } else {
                    return null;
                }
            } else {
                index = -(index + 1);
                if (index == 0) {
                    return null;
                } if (index == reports.length) {
                    return reports[reports.length - 1];
                } else {
                    return reports[index - 1];
                }
            }
        }
    }

    public List<Report> getReportsInRange(ReportRange range) {
        try (BMC ignored = BMC.start("ReportTimeline.getReportsInRange")) {
            //noinspection unchecked
            int left = Arrays.binarySearch(reports, range.getSince().getReport(), binarySearchComparator);
            //noinspection unchecked
            int right = Arrays.binarySearch(reports, range.getTill().getReport(), binarySearchComparator);

            if (left >= 0) {
                // noop
            } else {
                left = -(left + 1);
            }

            if (right >= 0) {
                // noop
            } else {
                right = -(right + 1);
                right = right - 1; // we offset this pointer by one to left
            }

            List<Report> result = new ArrayList<>(right - left + 1);
            for (int i = left; i <= right; i++) {
                result.add(reports[i]);
            }
            return result;
        }
    }
}
