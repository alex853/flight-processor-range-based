package net.simforge.flight.processor.rangebased;

import net.simforge.flight.core.storage.FlightStorageService;
import net.simforge.flight.core.storage.LocalGsonFlightStorage;
import net.simforge.networkview.core.Network;
import net.simforge.networkview.core.report.ReportInfo;
import net.simforge.networkview.core.report.ReportInfoDto;
import net.simforge.networkview.core.report.ReportUtils;
import net.simforge.networkview.core.report.persistence.*;
import net.simforge.networkview.core.report.ReportRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

@Deprecated
public class ProcessorPOC {
    private static final Logger logger = LoggerFactory.getLogger(ProcessorPOC.class);

    public static void main(String[] args) throws InterruptedException {
        ProcessorPOC processor = new ProcessorPOC();

        ReportSessionManager sessionManager = new ReportSessionManager();
        processor.setReportOpsService(new BaseReportOpsService(sessionManager, Network.VATSIM));
        processor.setStatusService(new ProcessorPOCStatusServiceInMemory());
        processor.setFlightStorageService(new LocalGsonFlightStorage("/home/alex853/simforge/range-based-gson-local-storage"));

        while (true) {
            processor.process();
            Thread.sleep(10);
        }
    }

    private ReportOpsService reportOpsService;
    private ProcessorPOCStatusService statusService;
    private FlightStorageService flightStorageService;

    private Map<Integer, PilotContext> pilotContexts = new HashMap<>();

    private Set<Integer> failedPilotNumbers = new HashSet<>();

    public ReportOpsService getReportOpsService() {
        return reportOpsService;
    }

    public void setReportOpsService(ReportOpsService reportOpsService) {
        this.reportOpsService = reportOpsService;
    }

    public ProcessorPOCStatusService getStatusService() {
        return statusService;
    }

    public void setStatusService(ProcessorPOCStatusService statusService) {
        this.statusService = statusService;
    }

    public FlightStorageService getFlightStorageService() {
        return flightStorageService;
    }

    public void setFlightStorageService(FlightStorageService flightStorageService) {
        this.flightStorageService = flightStorageService;
    }

    public void process() {
        ReportInfo lastProcessedReport = statusService.loadLastProcessedReport();

        Report currentReport;
        if (lastProcessedReport != null) {
            currentReport = reportOpsService.loadNextReport(lastProcessedReport.getReport());
        } else {
            currentReport = reportOpsService.loadFirstReport();
        }

        if (currentReport == null) {
            return;
        }

        if (!Boolean.TRUE.equals(currentReport.getParsed())) {
            return;
        }

        logger.info("{} - Processing...", ReportUtils.log(currentReport));

        List<ReportPilotPosition> currentPositions = reportOpsService.loadPilotPositions(currentReport);

        List<Report> allReports = reportOpsService.loadAllReports(); // future optimize it
        ReportInfo fromReport = allReports.get(0);
        ReportInfo toReport = allReports.get(allReports.size() - 1);
        ReportRange allReportsRange = ReportRange.between(fromReport, toReport);

        Map<Integer, PilotContext> nextPilotContexts = new HashMap<>();

        long lastPrintTs = System.currentTimeMillis();
        int counter = 0;

        for (ReportPilotPosition currentPosition : currentPositions) {
            int pilotNumber = currentPosition.getPilotNumber();

            if (failedPilotNumbers.contains(pilotNumber)) {
                logger.warn("SKIPPING FAILED PILOT NUMBER {}", pilotNumber);
            }

            try {
                PilotContext pilotContext = pilotContexts.get(pilotNumber);
                if (pilotContext != null) {
                    pilotContext = pilotContext.makeCopy();
                } else {
                    pilotContext = statusService.loadPilotContext(pilotNumber);

                    if (pilotContext == null) {
                        pilotContext = statusService.createPilotContext(pilotNumber);
                    }

                    nextPilotContexts.put(pilotNumber, pilotContext);
                }

                if (pilotContext.isReportProcessed(currentPosition.getReport())) {
                    continue;
                }

                ReportRange reportInRange = pilotContext.getNonProcessedRange(currentPosition.getReport());
                ReportRange currentRange = reportInRange != null ? allReportsRange.intersect(reportInRange) : allReportsRange;
                if (currentRange == null) {
                    throw new IllegalStateException();
                }

                List<ReportPilotPosition> reportPilotPositions = reportOpsService.loadPilotPositionsSinceTill(pilotNumber, currentRange.getSince(), currentRange.getTill());

                Track track = Track.build(currentRange, allReports, reportPilotPositions); // todo limit allRanges by currentRange

                Collection<Flight1> persistedFlights = flightStorageService.loadFlights(pilotNumber, currentRange.getSince(), currentRange.getTill());
                Flight1[] limitingCompletedFlights = new Flight1[2];
                track.getFlights().forEach(flight1 -> {
                    ReportRange flight1Range = ReportRange.between(flight1.getTakeoff().getReportInfo(), flight1.getLanding().getReportInfo());
                    Collection<Flight1> overlappedFlights = Flight1Util.findOverlappedFlights(flight1Range, persistedFlights);
                    // improvement - check if there is only single flight and it matches with new flight - do not need to remove, just update
                    overlappedFlights.forEach(flightStorageService::deleteFlight);

                    flightStorageService.saveFlight(flight1);

                    if (isCompleted(flight1)) {
                        if (limitingCompletedFlights[0] != null) {
                            limitingCompletedFlights[0] =
                                    limitingCompletedFlights[0].getTakeoff().getReportInfo().getDt().isBefore(flight1.getTakeoff().getReportInfo().getDt()) ?
                                            limitingCompletedFlights[0] :
                                            flight1;
                            limitingCompletedFlights[1] =
                                    limitingCompletedFlights[1].getLanding().getReportInfo().getDt().isAfter(flight1.getLanding().getReportInfo().getDt()) ?
                                            limitingCompletedFlights[1] :
                                            flight1;
                        } else {
                            limitingCompletedFlights[0] = flight1;
                            limitingCompletedFlights[1] = flight1;
                        }
                    }
                });

                Flight1 firstCompletedFlight = limitingCompletedFlights[0];
                Flight1 lastCompletedFlight = limitingCompletedFlights[1];
// todo if null?
                pilotContext.addProcessedRange(currentRange,
                        ReportRange.between(
                                firstCompletedFlight.getTakeoff().getReportInfo(),
                                lastCompletedFlight.getLanding().getReportInfo()));
                statusService.savePilotContext(pilotContext);
            } catch (Throwable t) {
                logger.error("Error on processing pilot number " + pilotNumber, t);
                failedPilotNumbers.add(pilotNumber);
            }

            counter++;
            long now = System.currentTimeMillis();
            if (now - lastPrintTs >= 10000) {
                logger.info("{} -     Positions : {} of {} done", ReportUtils.log(currentReport), counter, currentPositions.size());
                lastPrintTs = now;
            }
        }

        statusService.saveLastProcessedReport(new ReportInfoDto(currentReport));
        pilotContexts = nextPilotContexts;

        logger.info("{} - Processing completed | Contexts {}", ReportUtils.log(currentReport), pilotContexts.size());
    }

    private boolean isCompleted(Flight1 flight1) {
        return true;
    }

}
