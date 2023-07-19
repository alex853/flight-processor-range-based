package net.simforge.flight.processor.rangebased;

// todo ak3 should it replace or be merged by PilotContext?
public class LoadedPilotInfo {
    private final int pilotNumber;
    private final PilotContext pilotContext;
    private final Track1Data trackData;

    private LoadedPilotInfo(PilotContext pilotContext) {
        this.pilotNumber = pilotContext.getPilotNumber();
        this.pilotContext = pilotContext;
        this.trackData = Track1Data.forPilot(pilotNumber);
    }

    public static LoadedPilotInfo fromPilotContext(PilotContext pilotContext) {
        return new LoadedPilotInfo(pilotContext);
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
}
