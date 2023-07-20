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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RunIncrementalProcess {
    private static final Logger logger = LoggerFactory.getLogger(RunIncrementalProcess.class);
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
            try {
                processor.process();
            } catch (Throwable t) {
                logger.error("Error on processing", t);
                logger.warn("---===### THE PROCESSING WILL BE RETRIED IN 60 SECS ###===---");
                Thread.sleep(60000);
                continue;
            }

            UnknownAircraftTypes.printStats();
            Thread.sleep(1000);

            BM.logPeriodically(true);
        }

        sessionManager.dispose();
    }
}
