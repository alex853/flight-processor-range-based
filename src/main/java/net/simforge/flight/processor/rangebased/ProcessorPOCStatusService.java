package net.simforge.flight.processor.rangebased;

import net.simforge.networkview.core.report.ReportInfo;

public interface ProcessorPOCStatusService {
    PilotContext loadPilotContext(int pilotNumber);

    PilotContext createPilotContext(int pilotNumber);

    void savePilotContext(PilotContext pilotContext);

    ReportInfo loadLastProcessedReport();

    void saveLastProcessedReport(ReportInfo report);
}
