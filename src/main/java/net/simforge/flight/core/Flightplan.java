package net.simforge.flight.core;

import net.simforge.networkview.core.Position;

import java.util.Objects;

public class Flightplan {
    private final String aircraftType;
    private final String regNo;
    private final String departure;
    private final String destination;

    public Flightplan(String aircraftType, String regNo, String departure, String destination) {
        this.aircraftType = aircraftType;
        this.regNo = regNo;
        this.departure = departure;
        this.destination = destination;
    }

    public String getAircraftType() {
        return aircraftType;
    }

    public String getRegNo() {
        return regNo;
    }

    public String getDeparture() {
        return departure;
    }

    public String getDestination() {
        return destination;
    }

    public static Flightplan fromPosition(Position position) {
        if (position.hasFlightplan()) {
            return new Flightplan(position.getFpAircraftType(), position.getRegNo(), position.getFpDeparture(), position.getFpDestination());
        } else {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Flightplan that = (Flightplan) o;

        if (!Objects.equals(aircraftType, that.aircraftType)) return false;
        if (!Objects.equals(regNo, that.regNo)) return false;
        if (!Objects.equals(departure, that.departure)) return false;
        if (!Objects.equals(destination, that.destination)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = aircraftType != null ? aircraftType.hashCode() : 0;
        result = 31 * result + (regNo != null ? regNo.hashCode() : 0);
        result = 31 * result + (departure != null ? departure.hashCode() : 0);
        result = 31 * result + (destination != null ? destination.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "FP[" + aircraftType + "," + regNo + "," + departure + "-" + destination + "]";
    }
}
