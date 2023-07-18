package net.simforge.flight.core.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.simforge.flight.processor.rangebased.Flight1;
import net.simforge.flight.processor.rangebased.Flight1Util;
import net.simforge.networkview.core.report.ReportInfo;
import net.simforge.networkview.core.report.ReportRange;

import java.util.*;
import java.util.stream.Collectors;

public class InMemoryFlightStorage implements FlightStorageService {
    private final Map<Integer, List<Flight1>> flights = new HashMap<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public Collection<Flight1> loadFlights(int pilotNumber, ReportInfo fromReport, ReportInfo toReport) {
        List<Flight1> flights = this.flights.get(pilotNumber);
        if (flights == null) {
            return new ArrayList<>();
        }

        return Flight1Util.findOverlappedFlights(ReportRange.between(fromReport, toReport), flights)
                .stream().map(this::copy).collect(Collectors.toList());
    }

    @Override
    public void saveFlight(Flight1 flight) {
        Flight1 anotherFlight = find(flight);
        if (anotherFlight != null) {
            throw new IllegalArgumentException("There is already the same flight saved");
        }

        List<Flight1> flights = this.flights.computeIfAbsent(flight.getPilotNumber(), l -> new ArrayList<>());
        flights.add(copy(flight));
    }

    @Override
    public void deleteFlight(Flight1 flight) {
        Flight1 anotherFlight = find(flight);
        if (anotherFlight == null) {
            return;
        }

        this.flights.get(flight.getPilotNumber()).remove(anotherFlight);
    }

    private Flight1 find(Flight1 flight) {
        List<Flight1> flights = this.flights.get(flight.getPilotNumber());
        if (flights == null) {
            return null;
        }

        for (Flight1 anotherFlight : flights) {
            if (anotherFlight.getFirstSeen().getReportInfo().getReport().equals(flight.getFirstSeen().getReportInfo().getReport())) {
                return anotherFlight;
            }
        }

        return null;
    }

    private Flight1 copy(Flight1 flight) {
        String json = gson.toJson(flight);
        return gson.fromJson(json, Flight1.class);
    }
}
