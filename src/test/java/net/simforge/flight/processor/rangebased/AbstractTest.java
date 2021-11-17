package net.simforge.flight.processor.rangebased;

import net.simforge.commons.io.Csv;
import net.simforge.commons.io.IOHelper;
import net.simforge.flight.core.storage.InMemoryFlightStorage;
import net.simforge.networkview.core.report.ReportInfo;
import net.simforge.networkview.core.report.persistence.Report;
import net.simforge.networkview.core.report.snapshot.CsvSnapshotReportOpsService;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

@Ignore
public abstract class AbstractTest {

    protected int pilotNumber;
    protected String csvSnapshot;
    protected boolean singleReportToProcess = true;
    protected ReportInfo savedProcessedReport;

    @Test
    public void test() throws IOException {
        InputStream is = Class.class.getResourceAsStream(csvSnapshot);
        String csvContent = IOHelper.readInputStream(is);
        Csv csv = Csv.fromContent(csvContent);
        CsvSnapshotReportOpsService reportOpsService = new CsvSnapshotReportOpsService(csv);
        List<Report> allReports = reportOpsService.loadAllReports();
        ReportInfo fromReport = allReports.get(0);
        ReportInfo toReport = allReports.get(allReports.size() - 1);

        InMemoryFlightStorage flightStorageService = new InMemoryFlightStorage();

        ProcessorPOCStatusService statusService = new ProcessorPOCStatusServiceInMemory();
        if (savedProcessedReport != null) {
            statusService.saveLastProcessedReport(savedProcessedReport);
        }

        ProcessorPOC processor = new ProcessorPOC();
        processor.setReportOpsService(reportOpsService);
        processor.setFlightStorageService(flightStorageService);
        processor.setStatusService(statusService);

        while (true) {
            processor.process();

            if (singleReportToProcess) {
                break;
            }

            ReportInfo lastProcessedReport = statusService.loadLastProcessedReport();
            if (lastProcessedReport.getId().equals(toReport.getId())) {
                break;
            }
        }

        Collection<Flight1> flights = flightStorageService.loadFlights(pilotNumber, fromReport, toReport);

        checkFlights(flights);
    }

    protected abstract void checkFlights(Collection<Flight1> flights);
}
