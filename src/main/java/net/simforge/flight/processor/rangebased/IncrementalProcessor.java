package net.simforge.flight.processor.rangebased;

import com.google.common.collect.Lists;
import net.simforge.commons.bm.BMC;
import net.simforge.flight.core.storage.FlightStorageService;
import net.simforge.flight.core.storage.StatusService;
import net.simforge.networkview.core.Position;
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

    private final Map<Integer, LoadedPilotInfo> loadedPilots = new TreeMap<>();

    private ReportTimeline timeline;

    public IncrementalProcessor(ReportSessionManager sessionManager, ReportOpsService reportOpsService, StatusService statusService, FlightStorageService flightStorageService) {
        this.sessionManager = sessionManager;
        this.reportOpsService = reportOpsService;
        this.statusService = statusService;
        this.flightStorageService = flightStorageService;
    }

    public void process() {
        try (BMC ignored = BMC.start("IncrementalProcessor.process")) {
            // todo ak3 support for "gap report"
            if (timeline == null) {
                timeline = ReportTimeline.load(reportOpsService);
            } else {
                while (true) { // todo ak3 timeline cleanup?
                    Report nextReport = reportOpsService.loadNextReport(timeline.getLastReport().getReport());
                    if (nextReport == null) {
                        break;
                    }

                    timeline.addReport(nextReport);
                }
            }

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

                    if (NewMethods.isEarlierThan(timeline.getLastReport(), nextReport.getReport())) {
                        logger.warn("TIMELINE IS LAGGING, Last Report {}, Next Report {}", timeline.getLastReport().getReport(), nextReport.getReport());
                        break;
                    }

                    if (reportCounter >= 30) {
                        logger.warn("Pilot Numbers - There are too many non-processed reports, current batch will be processed then it continue remaining reports");
                        break;
                    }
                    reportCounter++;

                    List<ReportPilotPosition> positions = reportOpsService.loadPilotPositions(nextReport);

                    Set<Integer> pilotsNotSeenInThisReport = new TreeSet<>(loadedPilots.keySet());
                    Set<Integer> addedPilots = new TreeSet<>();
                    positions.forEach(p -> {
                        int pilotNumber = p.getPilotNumber();
                        pilotNumbers.add(pilotNumber);

                        LoadedPilotInfo loadedPilotInfo = loadedPilots.get(pilotNumber);
                        if (loadedPilotInfo == null) {
                            addedPilots.add(pilotNumber);
                            return;
                        }
                        Track1Data trackData = loadedPilotInfo.getTrackData();

                        pilotsNotSeenInThisReport.remove(pilotNumber);
                        boolean success = trackData.storePositions(timeline, NewMethods.rangeOf(nextReport), Lists.newArrayList(p));
                        if (!success) {
                            logger.warn("            Pilot {} - Track Data - UNABLE TO STORE SINGLE POSITION, SOMETHING IS WRONG", pilotNumber);
                        }
                    });

                    for (int pilotNumber : pilotsNotSeenInThisReport) {
                        LoadedPilotInfo loadedPilotInfo = loadedPilots.get(pilotNumber);
                        Track1Data trackData = loadedPilotInfo.getTrackData();
                        boolean success = trackData.storePositions(timeline, NewMethods.rangeOf(nextReport), Lists.newArrayList());
                        if (!success) {
                            logger.warn("            Pilot {} - Track Data - UNABLE TO STORE SINGLE OFFLINE POSITION, SOMETHING IS WRONG", pilotNumber);
                        }
                    }

                    pilotNumbers.addAll(pilotsNotSeenInThisReport);

                    if (!addedPilots.isEmpty()) {
                        logger.info("            Track Data - {} - Appeared pilots: {}", nextReport.getReport(), addedPilots);
                    }

                    if (!pilotsNotSeenInThisReport.isEmpty()) {
                        logger.info("            Track Data - {} - Disappeared pilots: {}", nextReport.getReport(), addedPilots);
                    }

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

            long lastPrintTs = System.currentTimeMillis();
            int counter = 0;

            for (int pilotNumber : pilotNumbers) {
                try {
                    processPilot(pilotNumber, newLastProcessedReport);
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

            Set<Integer> pilotsToRemove = new TreeSet<>();
            final ReportInfo reportToDetermineRemoval = newLastProcessedReport;
            loadedPilots.values().forEach(loadedPilotInfo -> {
                Track1Data trackData = loadedPilotInfo.getTrackData();
                if (!trackData.hasOnlinePositionsLaterThan(NewMethods.minusHours(reportToDetermineRemoval, 6))) {
                    pilotsToRemove.add(trackData.getPilotNumber());
                }
            });
            if (!pilotsToRemove.isEmpty()) {
                logger.warn("            Track Data - The following pilots will be removed due to offline status: {}", pilotsToRemove);
                pilotsToRemove.forEach(loadedPilots::remove);
            }

            int tracksCount = loadedPilots.size();
            int positionsCount = loadedPilots.values().stream().mapToInt(loadedPilotInfo -> loadedPilotInfo.getTrackData().size()).sum();

            logger.info("Track Data - Stats: tracks {}, positions {}, positions per track {}", tracksCount, positionsCount, positionsCount / Math.max(tracksCount, 1));

            printMemoryReport();
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
    private void processPilot(Integer pilotNumber, Report processorTillProcessReport) {
        try (BMC ignored = BMC.start("IncrementalProcessor.processPilot")) {
            LoadedPilotInfo draftLoadedPilotInfo = loadedPilots.get(pilotNumber);
            PilotContext pilotContext;
            if (draftLoadedPilotInfo == null) {
                pilotContext = statusService.loadPilotContext(pilotNumber);
                if (pilotContext == null) {
                    pilotContext = statusService.createPilotContext(pilotNumber);
                }

                Collection<Flight1> unsortedFlights = flightStorageService.loadAllFlights(pilotNumber);

                draftLoadedPilotInfo = LoadedPilotInfo.fromPilotContext(pilotContext, unsortedFlights);
                loadedPilots.put(pilotNumber, draftLoadedPilotInfo);
            } else {
                pilotContext = draftLoadedPilotInfo.getPilotContext();
            }
            final LoadedPilotInfo loadedPilotInfo = draftLoadedPilotInfo;

            ReportInfo lastProcessedReport = pilotContext.getLastIncrementallyProcessedReport();

            ReportInfo processTrackSinceReport = timeline.getFirstReport();
            if (lastProcessedReport != null) {
                String calculatedDateTime = NewMethods.minusHours(lastProcessedReport, 24);
                processTrackSinceReport = timeline.findPreviousReport(calculatedDateTime);

                if (processTrackSinceReport == null) {
                    processTrackSinceReport = timeline.getFirstReport();
                }
            }

            ReportInfo processTrackTillReport = processorTillProcessReport;//timeline.getLastReport(); // todo ak2 timeline vs separate logic in processor - there are issues that need to be solved

/*            if (lastProcessedReport != null && !oldFlights.isEmpty()) {
                Flight1 firstFlight = oldFlights.get(0);
                ReportRange firstFlightRange = ReportRange.between(firstFlight.getFirstSeen().getReportInfo(), firstFlight.getLastSeen().getReportInfo());

                if (firstFlightRange.isWithin(lastProcessedReport)) { // todo ak3 rename it to hasReportWithinBoundaries
                    processTrackSinceReport = firstFlight.getFirstSeen().getReportInfo();
                }
            }*/

            ReportRange currentRange = ReportRange.between(processTrackSinceReport, processTrackTillReport);
            Track1Data trackData = loadedPilotInfo.getTrackData();
            Optional<List<Position>> foundPositions = trackData.getPositions(currentRange);
            if (!foundPositions.isPresent()) {
                logger.info("            Pilot {} - Track Data - Loading Positions since {} till {}", pilotNumber, processTrackSinceReport.getReport(), processTrackTillReport.getReport());
                List<ReportPilotPosition> reportPilotPositions = reportOpsService.loadPilotPositionsSinceTill(pilotNumber, processTrackSinceReport, processTrackTillReport);
                trackData.clearPositions();
                boolean success = trackData.storePositions(timeline, currentRange, reportPilotPositions);
                if (!success) {
                    logger.warn("            Pilot {} - Track Data - UNABLE TO STORE POSITIONS, SOMETHING IS WRONG", pilotNumber);
                }
                foundPositions = trackData.getPositions(currentRange);
            }

            Track1 track = Track1.build(pilotNumber, foundPositions.get());

            List<Flight1> oldFlights = loadedPilotInfo.getFlights();
            track.getFlights().forEach(flight1 -> {
                ReportRange flight1Range = ReportRange.between(flight1.getFirstSeen().getReportInfo(), flight1.getLastSeen().getReportInfo());
                Collection<Flight1> overlappedFlights = Flight1Util.findOverlappedFlights(flight1Range, oldFlights);

                // improvement - check if there is only single flight and it matches with new flight - do not need to remove, just update
                overlappedFlights.forEach(flightStorageService::deleteFlight);
                overlappedFlights.forEach(loadedPilotInfo::deleteFlight);

                flightStorageService.saveFlight(flight1);
                loadedPilotInfo.addFlight(flight1);
            });

            pilotContext.setLastIncrementallyProcessedReport(processTrackTillReport);
            statusService.savePilotContext(pilotContext);
            trackData.removePositionsOlderThanTimestamp(NewMethods.minusHours(processTrackTillReport, 30));
        }
    }

    private static long lastMemoryReportTs;

    private static void printMemoryReport() {
        if (lastMemoryReportTs + 10 * 60 * 1000 < System.currentTimeMillis()) {
            Runtime runtime = Runtime.getRuntime();
            long mm = runtime.maxMemory();
            long fm = runtime.freeMemory();
            long tm = runtime.totalMemory();
            String str = "Memory report: Used = " + toMB(tm - fm) + ", " + "Free = " + toMB(fm) + ", " + "Total = " + toMB(tm) + ", " + "Max = " + toMB(mm);
            logger.info(str);

            lastMemoryReportTs = System.currentTimeMillis();
        }
    }

    private static String toMB(long size) {
        return Long.toString(size / 0x100000L);
    }

}
