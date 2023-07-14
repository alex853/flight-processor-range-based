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

        assertEquals("20200423182904", timeline.getFirstReport().getReport());
        assertEquals("20200815204458", timeline.getLastReport().getReport());
    }

    @Test
    public void test_reportsInRange() {
        ReportTimeline timeline = ReportTimeline.load(reportOpsService);

        List<Report> reportsInRange = timeline.getReportsInRange(ReportRange.between(
                new ReportInfoDto(1000005L, "20200423185048"),
                new ReportInfoDto(1000015L, "20200423193748")));
        assertEquals(11, reportsInRange.size());
    }

    @Test
    public void test_findPreviousReport_ok() {
        ReportTimeline timeline = ReportTimeline.load(reportOpsService);

        ReportInfo previousReport = timeline.findPreviousReport("20200423193749");
        assertEquals("20200423193748", previousReport.getReport());
    }

    @Test
    public void test_findPreviousReport_aheadOfFirstReport() {
        ReportTimeline timeline = ReportTimeline.load(reportOpsService);

        ReportInfo previousReport = timeline.findPreviousReport("20200423182903");
        assertNull(previousReport);
    }
}
