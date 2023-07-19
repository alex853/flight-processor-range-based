package net.simforge.flight.processor.rangebased;

import com.google.common.collect.Lists;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

// todo ak3 should it replace or be merged by PilotContext?
public class LoadedPilotInfo {
    private final int pilotNumber;
    private final PilotContext pilotContext;
    private final Track1Data trackData;
    private final List<Flight1> flights = new ArrayList<>();

    private LoadedPilotInfo(PilotContext pilotContext, Collection<Flight1> existingFlights) {
        this.pilotNumber = pilotContext.getPilotNumber();
        this.pilotContext = pilotContext;
        this.trackData = Track1Data.forPilot(pilotNumber);
        this.flights.addAll(existingFlights);
        this.flights.sort(Flight1::compareByFirstSeen);
    }

    public static LoadedPilotInfo fromPilotContext(PilotContext pilotContext, Collection<Flight1> existingFlights) {
        return new LoadedPilotInfo(pilotContext, existingFlights);
    }

    public int getPilotNumber() {
        return pilotNumber;
    }

    public PilotContext getPilotContext() {
        return pilotContext;
    }

    public Track1Data getTrackData() {
        return trackData;
    }

    public List<Flight1> getFlights() {
        return Collections.unmodifiableList(flights);
    }

    public void deleteFlight(Flight1 flight1) {
        flights.remove(flight1);
    }

    public void addFlight(Flight1 flight1) {
        flights.add(flight1);
        flights.sort(Flight1::compareByFirstSeen);
    }
}
