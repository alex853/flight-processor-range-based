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

    public static final int BASE_TRACK_LENGTH_HOURS = 18;
    public static final int MAX_TRACK_LENGHT_HOURS = 24;
    public static final int REMOVE_IF_OFFLINE_HOURS = 1;
    public static Map<String, Integer> trackStats;

    private final ReportSessionManager sessionManager;
    private final ReportOpsService reportOpsService;
    private final StatusService statusService;
    private final FlightStorageService flightStorageService;

    private final Map<Integer, LoadedPilotInfo> loadedPilots = new TreeMap<>();

    // todo ak3 support for "gap report"
    private ReportTimeline timeline;
    private long timelineLastReload;

    public IncrementalProcessor(ReportSessionManager sessionManager, ReportOpsService reportOpsService, StatusService statusService, FlightStorageService flightStorageService) {
        this.sessionManager = sessionManager;
        this.reportOpsService = reportOpsService;
        this.statusService = statusService;
        this.flightStorageService = flightStorageService;
    }

    public void process() {
        try (BMC ignored = BMC.start("IncrementalProcessor.process")) {
            ReportInfo lastProcessedReport = statusService.loadLastProcessedReport();

            Report newLastProcessedReport = null;
            Set<Integer> pilotNumbers = new TreeSet<>();

            if (lastProcessedReport == null) {

                Report latestReport = reportOpsService.loadLastReport();
                if (!Boolean.TRUE.equals(latestReport.getParsed())) {
                    logger.warn("Latest report {} is not parsed, need to wait a bit", latestReport.getReport());
                    return; // need to wait a bit
                }

                timeline = ReportTimeline.load(reportOpsService);
                timeline.deleteReportsLaterThan(latestReport.getReport());

                newLastProcessedReport = latestReport;
                pilotNumbers.addAll(reportOpsService.loadPilotPositions(latestReport).stream()
                        .map(ReportPilotPosition::getPilotNumber)
                        .collect(Collectors.toSet()));
                logger.info("Pilot Numbers - loaded for {} - INITIAL LOADING", ReportUtils.log(latestReport));

            } else {

                if (timeline == null
                        || (System.currentTimeMillis() - timelineLastReload > 3600000)) {
                    logger.warn("TIMELINE FULL RELOAD");
                    timeline = ReportTimeline.load(reportOpsService);
                    timelineLastReload = System.currentTimeMillis();
                }

                String currReport = lastProcessedReport.getReport();
                timeline.deleteReportsLaterThan(currReport);

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

                    timeline.addReport(nextReport);

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
            LastProcessedReports.save(newLastProcessedReport.getReport());

            Set<Integer> pilotsToRemove = new TreeSet<>();
            final ReportInfo reportToDetermineRemoval = newLastProcessedReport;
            loadedPilots.values().forEach(loadedPilotInfo -> {
                Track1Data trackData = loadedPilotInfo.getTrackData();
                if (!trackData.hasOnlinePositionsLaterThan(NewMethods.minusHours(reportToDetermineRemoval, REMOVE_IF_OFFLINE_HOURS))) {
                    pilotsToRemove.add(trackData.getPilotNumber());
                }
            });
            if (!pilotsToRemove.isEmpty()) {
                logger.warn("            Track Data - The following pilots will be removed due to offline status: {}", pilotsToRemove);
                pilotsToRemove.forEach(loadedPilots::remove);
            }

            int tracksCount = loadedPilots.size();
            int positionsCount = loadedPilots.values().stream().mapToInt(loadedPilotInfo -> loadedPilotInfo.getTrackData().size()).sum();
            int maxPositions = loadedPilots.values().stream().mapToInt(loadedPilotInfo -> loadedPilotInfo.getTrackData().size()).max().orElse(0);

            logger.info("Track Data - Stats: tracks {}, positions {}, positions per track {}, longest track {}", tracksCount, positionsCount, positionsCount / Math.max(tracksCount, 1), maxPositions);

            Map<String, Integer> trackStats = new TreeMap<>();
            trackStats.put("tracks", tracksCount);
            trackStats.put("positions", positionsCount);
            trackStats.put("avgPositions", positionsCount / Math.max(tracksCount, 1));
            trackStats.put("longestTrack", maxPositions);
            IncrementalProcessor.trackStats = trackStats;
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

            String estimatedTrackSinceReport = lastProcessedReport != null
                    ? NewMethods.minusHours(lastProcessedReport, BASE_TRACK_LENGTH_HOURS)
                    : NewMethods.minusHours(processorTillProcessReport, BASE_TRACK_LENGTH_HOURS);
            ReportInfo processTrackSinceReport = timeline.findPreviousReport(estimatedTrackSinceReport);
            if (processTrackSinceReport == null) {
                processTrackSinceReport = timeline.getFirstReport();
            }

            List<Flight1> oldFlights = loadedPilotInfo.getFlights();
            if (lastProcessedReport != null && !oldFlights.isEmpty()) {
                final ReportInfo processTrackSinceReport1 = processTrackSinceReport;
                Optional<Flight1> firstFlightWithinProcessingRange = oldFlights.stream().filter(flight ->
                        ReportRange.between(
                                        flight.getFirstSeen().getReportInfo(),
                                        flight.getLastSeen().getReportInfo())
                                .isWithin(processTrackSinceReport1)
                ).findFirst();

                if (firstFlightWithinProcessingRange.isPresent()) {
                    processTrackSinceReport = firstFlightWithinProcessingRange.get().getFirstSeen().getReportInfo();
                }
            }

            ReportInfo processTrackTillReport = processorTillProcessReport;

            ReportRange currentRange = ReportRange.between(processTrackSinceReport, processTrackTillReport);
            Track1Data trackData = loadedPilotInfo.getTrackData();
            Optional<List<Position>> foundPositions = trackData.getPositions(currentRange);
            if (!foundPositions.isPresent()) {
                logger.info("            Pilot {} - Track Data - Loading {} - {}", pilotNumber, processTrackSinceReport.getReport(), processTrackTillReport.getReport());
                List<ReportPilotPosition> reportPilotPositions = reportOpsService.loadPilotPositionsSinceTill(pilotNumber, processTrackSinceReport, processTrackTillReport);
                trackData.clearPositions();
                boolean success = trackData.storePositions(timeline, currentRange, reportPilotPositions);
                if (!success) {
                    logger.warn("            Pilot {} - Track Data - UNABLE TO STORE POSITIONS, SOMETHING IS WRONG", pilotNumber);
                }
                foundPositions = trackData.getPositions(currentRange);
            }

            Track1 track = Track1.build(pilotNumber, foundPositions.get());

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

            String removalThreshold = NewMethods.minusHours(processTrackTillReport, MAX_TRACK_LENGHT_HOURS);
            if (NewMethods.isEarlierThan(processTrackSinceReport, removalThreshold)) {
                removalThreshold = processTrackSinceReport.getReport();
            }
            trackData.removePositionsOlderThanTimestamp(removalThreshold);
        }
    }
}
