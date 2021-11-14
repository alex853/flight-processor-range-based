package net.simforge.flight.core.storage;

import net.simforge.flight.processor.rangebased.Flight1;
import net.simforge.networkview.core.report.ReportInfo;

import java.util.Collection;

public interface FlightStorageService {
    Collection<Flight1> loadFlights(int pilotNumber, ReportInfo fromReport, ReportInfo toReport);

    void saveFlight(Flight1 flight);

    void deleteFlight(Flight1 flight);
}
