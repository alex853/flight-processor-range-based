package net.simforge.flight.processor.rangebased;

import net.simforge.flight.core.storage.FlightStorageService;
import net.simforge.flight.core.storage.LocalGsonFlightStorage;
import net.simforge.networkview.core.Network;
import net.simforge.networkview.core.report.persistence.BaseReportOpsService;
import net.simforge.networkview.core.report.persistence.ReportOpsService;
import net.simforge.networkview.core.report.persistence.ReportSessionManager;

// todo ak wrap into BM
public class RunIncrementalProcess {
    public static void main(String[] args) throws InterruptedException {
        String storagePath = "/home/alex853/simforge/range-based-gson-local-storage";
        boolean singleRun = false;

        ReportSessionManager sessionManager = new ReportSessionManager();
        ReportOpsService reportOpsService = new BaseReportOpsService(sessionManager, Network.VATSIM);
        ProcessorPOCStatusService statusService = new ProcessorPOCStatusServiceLocalGson(storagePath);
        FlightStorageService flightStorageService = new LocalGsonFlightStorage(storagePath);

        IncrementalProcessor processor = new IncrementalProcessor(
                sessionManager,
                reportOpsService,
                statusService,
                flightStorageService);

        while (!singleRun) {
            processor.process();
            UnknownAircraftTypes.printStats();
            Thread.sleep(100);
        }

        sessionManager.dispose();
    }
}
