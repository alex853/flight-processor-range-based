package net.simforge.flight.processor.rangebased;

import net.simforge.commons.legacy.BM;
import net.simforge.flight.core.storage.FlightStorageService;
import net.simforge.flight.core.storage.StatusService;
import net.simforge.flight.core.storage.impl.LocalGsonFlightStorage;
import net.simforge.flight.core.storage.impl.LocalGsonStatusService;
import net.simforge.networkview.core.Network;
import net.simforge.networkview.core.report.persistence.BaseReportOpsService;
import net.simforge.networkview.core.report.persistence.ReportOpsService;
import net.simforge.networkview.core.report.persistence.ReportSessionManager;

public class RunIncrementalProcess {
    public static void main(String[] args) throws InterruptedException {
        String storagePath = "./range-based-gson-local-storage";
        boolean singleRun = false;

        BM.init("RunIncrementalProcessor");
        BM.setLoggingPeriod(600000L);

        ReportSessionManager sessionManager = new ReportSessionManager();
        ReportOpsService reportOpsService = new BaseReportOpsService(sessionManager, Network.VATSIM);
        StatusService statusService = new LocalGsonStatusService(storagePath);
        FlightStorageService flightStorageService = new LocalGsonFlightStorage(storagePath);

        IncrementalProcessor processor = new IncrementalProcessor(
                sessionManager,
                reportOpsService,
                statusService,
                flightStorageService);

        while (!singleRun) {
            processor.process();
            UnknownAircraftTypes.printStats();
            Thread.sleep(1000);

            BM.logPeriodically(true);
        }

        sessionManager.dispose();
    }
}
