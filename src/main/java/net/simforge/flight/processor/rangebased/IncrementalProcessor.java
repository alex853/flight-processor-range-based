package net.simforge.flight.processor.rangebased;

import net.simforge.commons.bm.BMC;
import net.simforge.flight.core.storage.FlightStorageService;
import net.simforge.flight.core.storage.StatusService;
import net.simforge.networkview.core.report.ReportInfo;
import net.simforge.networkview.core.report.ReportRange;
import net.simforge.networkview.core.report.ReportUtils;
import net.simforge.networkview.core.report.persistence.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class IncrementalProcessor {
    private static final Logger logger = LoggerFactory.getLogger(IncrementalProcessor.class);

    private final ReportSessionManager sessionManager;
    private final ReportOpsService reportOpsService;
    private final StatusService statusService;
    private final FlightStorageService flightStorageService;

    private ReportTimeline timeline;

    public IncrementalProcessor(ReportSessionManager sessionManager, ReportOpsService reportOpsService, StatusService statusService, FlightStorageService flightStorageService) {
        this.sessionManager = sessionManager;
        this.reportOpsService = reportOpsService;
        this.statusService = statusService;
        this.flightStorageService = flightStorageService;
    }

    public void process() {
        try (BMC ignored = BMC.start("IncrementalProcessor.process")) {
            ReportInfo lastProcessedReport = statusService.loadLastProcessedReport();
            Report latestReport = reportOpsService.loadLastReport();

            Report newLastProcessedReport = null;
            Set<Integer> pilotNumbers = new TreeSet<>();
            if (lastProcessedReport == null) {
                if (!Boolean.TRUE.equals(latestReport.getParsed())) {
                    return; // need to wait a bit
                }
                newLastProcessedReport = latestReport;
                pilotNumbers.addAll(reportOpsService.loadPilotPositions(latestReport).stream()
                        .map(ReportPilotPosition::getPilotNumber)
                        .collect(Collectors.toSet()));
                logger.info("Pilot Numbers - loaded for {} - INITIAL LOADING", ReportUtils.log(latestReport));
            } else {
                String currReport = lastProcessedReport.getReport();
                int reportCounter = 0;
                while (true) {
                    Report nextReport = reportOpsService.loadNextReport(currReport);
                    if (nextReport == null || !Boolean.TRUE.equals(nextReport.getParsed())) {
                        break;
                    }

                    if (reportCounter >= 30) {
                        logger.warn("Pilot Numbers - There are too many non-processed reports, current batch will be processed then it continue remaining reports");
                        break;
                    }
                    reportCounter++;

                    List<ReportPilotPosition> positions = reportOpsService.loadPilotPositions(nextReport);
                    CachedPositions.consumeReportPositions(nextReport, positions);
                    pilotNumbers.addAll(positions.stream()
                            .map(ReportPilotPosition::getPilotNumber)
                            .collect(Collectors.toSet()));
                    newLastProcessedReport = nextReport;
                    currReport = nextReport.getReport();
                    logger.info("Pilot Numbers - loaded for {}", ReportUtils.log(newLastProcessedReport));
                }
            }

            if (newLastProcessedReport == null) {
                return; // no report to process
            }

            if (pilotNumbers.isEmpty()) {
                logger.warn("Pilot Numbers list is empty! Something is wrong!");
                return;
            }

            timeline = ReportTimeline.load(reportOpsService);
            // todo ak support for "gap report"

            long lastPrintTs = System.currentTimeMillis();
            int counter = 0;

            for (int pilotNumber : pilotNumbers) {
                try {
                    processPilot(pilotNumber);
                } catch (Exception e) {
                    logger.warn("Error on processing pilot " + pilotNumber, e);
                }

                counter++;
                long now = System.currentTimeMillis();
                if (now - lastPrintTs >= 10000) {
                    logger.info(" -     Positions : {} of {} done", counter, pilotNumbers.size());
                    lastPrintTs = now;
                }
            }

            logger.info(" -     Positions : ALL {} DONE", pilotNumbers.size());
            statusService.saveLastProcessedReport(newLastProcessedReport);
        }
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
        try (BMC ignored = BMC.start("IncrementalProcessor.processPilot")) {
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
            oldFlights.sort(Flight1::compareByFirstSeen);

            if (lastProcessedReport != null && !oldFlights.isEmpty()) {
                Flight1 firstFlight = oldFlights.get(0);
                ReportRange firstFlightRange = ReportRange.between(firstFlight.getFirstSeen().getReportInfo(), firstFlight.getLastSeen().getReportInfo());

                if (firstFlightRange.isWithin(lastProcessedReport)) { // todo ak3 rename it to hasReportWithinBoundaries
                    processTrackSinceReport = firstFlight.getFirstSeen().getReportInfo();
                }
            }

            List<ReportPilotPosition> positions = CachedPositions.findPilotPositions(pilotNumber, processTrackSinceReport, processTrackTillReport);
            if (positions == null) { // todo ak1 some kind of statistics here
                positions = reportOpsService.loadPilotPositionsSinceTill(pilotNumber, processTrackSinceReport, processTrackTillReport); logger.warn("loading again");
                CachedPositions.storePilotPositions(pilotNumber, positions, timeline, processTrackSinceReport, processTrackTillReport);
            }

            Track1 track = Track1.build(ReportRange.between(processTrackSinceReport, processTrackTillReport), timeline, positions);

            track.getFlights().forEach(flight1 -> {
                ReportRange flight1Range = ReportRange.between(flight1.getFirstSeen().getReportInfo(), flight1.getLastSeen().getReportInfo());
                Collection<Flight1> overlappedFlights = Flight1Util.findOverlappedFlights(flight1Range, oldFlights);
                // improvement - check if there is only single flight and it matches with new flight - do not need to remove, just update
                overlappedFlights.forEach(flightStorageService::deleteFlight);

                flightStorageService.saveFlight(flight1);
            });

            pilotContext.setLastIncrementallyProcessedReport(processTrackTillReport);
            statusService.savePilotContext(pilotContext);
            CachedPositions.cleanupUselessPilotPositions(pilotNumber, processTrackTillReport);
        }
    }
}
