package net.simforge.flight.processor.rangebased;

import net.simforge.networkview.core.report.ReportInfo;
import net.simforge.networkview.core.report.ReportRange;
import net.simforge.networkview.core.report.ReportUtils;

public class NewMethods {
    public static ReportRange rangeOf(ReportInfo report) {
        return ReportRange.between(report, report);
    }

    public static boolean isEarlierThan(ReportInfo thisReport, String timestampSupposedToBeEarlier) {
        return ReportUtils.isTimestampLess(thisReport.getReport(), timestampSupposedToBeEarlier);
    }

    public static String minusHours(ReportInfo report, int hours) {
        return ReportUtils.toTimestamp(report.getDt().minusHours(hours));
    }
}
