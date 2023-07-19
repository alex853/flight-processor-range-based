package net.simforge.flight.core.storage;

import net.simforge.flight.processor.rangebased.PilotContext;
import net.simforge.networkview.core.report.ReportInfo;

public interface StatusService {
    PilotContext loadPilotContext(int pilotNumber);

    PilotContext createPilotContext(int pilotNumber);

    void savePilotContext(PilotContext pilotContext);

    ReportInfo loadLastProcessedReport();

    void saveLastProcessedReport(ReportInfo report);
}
