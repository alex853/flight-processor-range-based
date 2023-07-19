package net.simforge.flight.processor.rangebased;

import net.simforge.commons.io.Csv;
import net.simforge.commons.io.IOHelper;
import net.simforge.networkview.core.report.ReportInfo;
import net.simforge.networkview.core.report.ReportInfoDto;
import net.simforge.networkview.core.report.ReportRange;
import net.simforge.networkview.core.report.persistence.Report;
import net.simforge.networkview.core.report.persistence.ReportOpsService;
import net.simforge.networkview.core.report.snapshot.CsvSnapshotReportOpsService;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TestReportTimeline {
    private static final String AHEAD_FIRST_REPORT = "20200423182903";
    private static final String FIRST_REPORT = "20200423182904";
    private static final String SOME_REPORT_BEFORE_SAMPLE_REPORT_1_AND_NO_EXACT_MATCH = "20200423185045";
    private static final String SAMPLE_REPORT_1 = "20200423185048";
    private static final String SOME_PREVIOUS_REPORT_FOR_SAMPLE_REPORT = "20200423193548";
    private static final String SAMPLE_REPORT_2 = "20200423193748";
    private static final String SOME_REPORT_NEXT_AFTER_SAMPLE_REPORT_2_AND_NO_EXACT_MATCH = "20200423193749";
    private static final String SAMPLE_REPORT_3 = "20200815203858";
    private static final String LAST_REPORT = "20200815204458";
    private static final String BEHIND_LAST_REPORT = "20200815205558";

    private ReportOpsService reportOpsService;

    @Before
    public void before() throws IOException {
        InputStream is = TestReportTimeline.class.getResourceAsStream("/snapshots/pilot-811636_from-1000000_amount-127321.csv");
        String csvContent = IOHelper.readInputStream(is);
        Csv csv = Csv.fromContent(csvContent);
        this.reportOpsService = new CsvSnapshotReportOpsService(csv);
    }

    @Test
    public void test_load() {
        ReportTimeline timeline = ReportTimeline.load(reportOpsService);

        assertEquals(FIRST_REPORT, timeline.getFirstReport().getReport());
        assertEquals(LAST_REPORT, timeline.getLastReport().getReport());
    }

    @Test
    public void test_reportsInRange_exactMatch() {
        ReportTimeline timeline = ReportTimeline.load(reportOpsService);

        List<Report> reportsInRange = timeline.getReportsInRange(ReportRange.between(
                new ReportInfoDto(1L, SAMPLE_REPORT_1),
                new ReportInfoDto(1L, SAMPLE_REPORT_2)));
        assertEquals(11, reportsInRange.size());
        assertEquals(SAMPLE_REPORT_1, reportsInRange.get(0).getReport());
        assertEquals(SAMPLE_REPORT_2, reportsInRange.get(reportsInRange.size() - 1).getReport());
    }

    @Test
    public void test_reportsInRange_nonExactMatchOnBothEnds() {
        ReportTimeline timeline = ReportTimeline.load(reportOpsService);

        List<Report> reportsInRange = timeline.getReportsInRange(ReportRange.between(
                new ReportInfoDto(1L, SOME_REPORT_BEFORE_SAMPLE_REPORT_1_AND_NO_EXACT_MATCH),
                new ReportInfoDto(1L, SOME_REPORT_NEXT_AFTER_SAMPLE_REPORT_2_AND_NO_EXACT_MATCH)));
        assertEquals(11, reportsInRange.size());
        assertEquals(SAMPLE_REPORT_1, reportsInRange.get(0).getReport());
        assertEquals(SAMPLE_REPORT_2, reportsInRange.get(reportsInRange.size() - 1).getReport());
    }

    @Test
    public void test_reportsInRange_aheadFirstReport() {
        ReportTimeline timeline = ReportTimeline.load(reportOpsService);

        List<Report> reportsInRange = timeline.getReportsInRange(ReportRange.between(
                new ReportInfoDto(1L, AHEAD_FIRST_REPORT),
                new ReportInfoDto(1L, SAMPLE_REPORT_1)));
        assertEquals(6, reportsInRange.size());
        assertEquals(FIRST_REPORT, reportsInRange.get(0).getReport());
        assertEquals(SAMPLE_REPORT_1, reportsInRange.get(reportsInRange.size() - 1).getReport());
    }

    @Test
    public void test_reportsInRange_behindLastReport() {
        ReportTimeline timeline = ReportTimeline.load(reportOpsService);

        List<Report> reportsInRange = timeline.getReportsInRange(ReportRange.between(
                new ReportInfoDto(1L, SAMPLE_REPORT_3),
                new ReportInfoDto(1L, BEHIND_LAST_REPORT)));
        assertEquals(7, reportsInRange.size());
        assertEquals(SAMPLE_REPORT_3, reportsInRange.get(0).getReport());
        assertEquals(LAST_REPORT, reportsInRange.get(reportsInRange.size() - 1).getReport());
    }

    @Test
    public void test_findPreviousReport_aheadFirstReport_expectNull() {
        ReportTimeline timeline = ReportTimeline.load(reportOpsService);

        ReportInfo previousReport = timeline.findPreviousReport(AHEAD_FIRST_REPORT);
        assertNull(previousReport);
    }

    @Test
    public void test_findPreviousReport_firstReport_expectNull() {
        ReportTimeline timeline = ReportTimeline.load(reportOpsService);

        ReportInfo previousReport = timeline.findPreviousReport(FIRST_REPORT);
        assertNull(previousReport);
    }

    @Test
    public void test_findPreviousReport_behindLastReport_expectLastReport() {
        ReportTimeline timeline = ReportTimeline.load(reportOpsService);

        ReportInfo previousReport = timeline.findPreviousReport(BEHIND_LAST_REPORT);
        assertEquals(LAST_REPORT, previousReport.getReport());
    }

    @Test
    public void test_findPreviousReport_exactMatch_reportWithinRange_expectSomePreviousReport() {
        ReportTimeline timeline = ReportTimeline.load(reportOpsService);

        ReportInfo previousReport = timeline.findPreviousReport(SAMPLE_REPORT_2);
        assertEquals(SOME_PREVIOUS_REPORT_FOR_SAMPLE_REPORT, previousReport.getReport());
    }

    @Test
    public void test_findPreviousReport_exactMatch_firstReport_expectNull() {
        ReportTimeline timeline = ReportTimeline.load(reportOpsService);

        ReportInfo previousReport = timeline.findPreviousReport(FIRST_REPORT);
        assertNull(previousReport);
    }

    @Test
    public void test_findPreviousReport_nonExactMatch_reportWithinRange_expectSomePreviousReport() {
        ReportTimeline timeline = ReportTimeline.load(reportOpsService);

        ReportInfo previousReport = timeline.findPreviousReport(SOME_REPORT_NEXT_AFTER_SAMPLE_REPORT_2_AND_NO_EXACT_MATCH);
        assertEquals(SAMPLE_REPORT_2, previousReport.getReport());
    }
}
