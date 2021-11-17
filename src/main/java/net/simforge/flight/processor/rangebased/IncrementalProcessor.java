package net.simforge.flight.processor.rangebased;

import net.simforge.flight.core.storage.FlightStorageService;
import net.simforge.networkview.core.report.ReportInfo;
import net.simforge.networkview.core.report.ReportRange;
import net.simforge.networkview.core.report.ReportUtils;
import net.simforge.networkview.core.report.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class IncrementalProcessor {
    private static final Logger logger = LoggerFactory.getLogger(IncrementalProcessor.class);

    private final ReportSessionManager sessionManager;
    private final ReportOpsService reportOpsService;
    private final ProcessorPOCStatusService statusService;
    private final FlightStorageService flightStorageService;

    private ReportTimeline timeline;

    public IncrementalProcessor(ReportSessionManager sessionManager, ReportOpsService reportOpsService, ProcessorPOCStatusService statusService, FlightStorageService flightStorageService) {
        this.sessionManager = sessionManager;
        this.reportOpsService = reportOpsService;
        this.statusService = statusService;
        this.flightStorageService = flightStorageService;
    }

    // todo ak to load active pilots that not presented in the report being processed
    public void process() {
        Report reportToProcess;
        ReportInfo lastProcessedReport = statusService.loadLastProcessedReport();
        if (lastProcessedReport != null) {
            reportToProcess = reportOpsService.loadNextReport(lastProcessedReport.getReport());
        } else {
            reportToProcess = reportOpsService.loadLastReport();
        }

        if (reportToProcess == null) {
//            logger.warn("The report to process not found");
            return;
        } else if (!Boolean.TRUE.equals(reportToProcess.getParsed())) {
//            logger.warn("The report to process is not parsed");
            return;
        }
        logger.info("{} - Processing...", ReportUtils.log(reportToProcess));

        timeline = ReportTimeline.load(reportOpsService);
        // todo ak support for "gap report"

        List<ReportPilotPosition> positions = reportOpsService.loadPilotPositions(reportToProcess);

        long lastPrintTs = System.currentTimeMillis();
        int counter = 0;

        for (ReportPilotPosition position : positions) {
            Integer pilotNumber = position.getPilotNumber();
            try {
                processPilot(pilotNumber);
            } catch (Exception e) {
                logger.warn("Error on processing pilot " + pilotNumber, e);
            }

            counter++;
            long now = System.currentTimeMillis();
            if (now - lastPrintTs >= 10000) {
                logger.info("{} -     Positions : {} of {} done", ReportUtils.log(reportToProcess), counter, positions.size());
                lastPrintTs = now;
            }
        }

        statusService.saveLastProcessedReport(reportToProcess);
    }

    // load "last processed report" from some pilot-specific structure
    // "process track since report" = "last processed report" - 48 hours
    // load flights in last 48 hours
    // to check the first one if "process track since report" is inside of flight
    // if yes - put beginning of the flight as "process track since report"
    // load positions since "process track since report"
    // build track using timeline and loaded positions
    // merge flights - think about events
    // save "last processed report"

    // improvement - this algorithm ignores an ability to put all that info into a memory cache
    // improvement - memory cache will complicate things however significantly improve the performance
    private void processPilot(Integer pilotNumber) {
        PilotContext pilotContext = statusService.loadPilotContext(pilotNumber);
        if (pilotContext == null) {
            pilotContext = statusService.createPilotContext(pilotNumber);
        }

        ReportInfo lastProcessedReport = pilotContext.getLastIncrementallyProcessedReport();

        ReportInfo processTrackSinceReport = timeline.getFirstReport();
        if (lastProcessedReport != null) {
            String calculatedDateTime = ReportUtils.toTimestamp(lastProcessedReport.getDt().minusHours(48));
            processTrackSinceReport = timeline.findPreviousReport(calculatedDateTime);

            if (processTrackSinceReport == null) {
                processTrackSinceReport = timeline.getFirstReport();
            }
        }

        ReportInfo processTrackTillReport = timeline.getLastReport();

        Collection<Flight1> unsortedFlights = flightStorageService.loadFlights(pilotNumber, processTrackSinceReport, processTrackTillReport);
        List<Flight1> oldFlights = new ArrayList<>(unsortedFlights);
        oldFlights.sort(Flight1::compareByTakeoff);

        if (lastProcessedReport != null && !oldFlights.isEmpty()) {
            Flight1 firstFlight = oldFlights.get(0);
            // todo ak first/last seen instead of takeoff/landing
            ReportRange firstFlightRange = ReportRange.between(firstFlight.getTakeoff().getReportInfo(), firstFlight.getLanding().getReportInfo());

            if (firstFlightRange.isWithin(lastProcessedReport)) {
                processTrackSinceReport = firstFlight.getTakeoff().getReportInfo(); // todo ak first seen!!!
            }
        }

        List<ReportPilotPosition> positions = reportOpsService.loadPilotPositionsSinceTill(pilotNumber, processTrackSinceReport, processTrackTillReport);
        Track1 track = Track1.build(ReportRange.between(processTrackSinceReport, processTrackTillReport), timeline, positions);

        track.getFlights().forEach(flight1 -> {
            ReportRange flight1Range = ReportRange.between(flight1.getTakeoff().getReportInfo(), flight1.getLanding().getReportInfo());
            Collection<Flight1> overlappedFlights = Flight1Util.findOverlappedFlights(flight1Range, oldFlights);
            // improvement - check if there is only single flight and it matches with new flight - do not need to remove, just update
            overlappedFlights.forEach(flightStorageService::deleteFlight);

            flightStorageService.saveFlight(flight1);
        });

        pilotContext.setLastIncrementallyProcessedReport(processTrackTillReport);
        statusService.savePilotContext(pilotContext);
    }
}
