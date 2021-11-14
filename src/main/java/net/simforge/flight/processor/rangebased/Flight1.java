package net.simforge.flight.processor.rangebased;

import net.simforge.commons.misc.Geo;
import net.simforge.flight.core.Flightplan;
import net.simforge.networkview.core.report.ReportInfoDto;
import net.simforge.networkview.core.Position;

import java.time.LocalDate;

public class Flight1 {
    private int pilotNumber;
    private LocalDate dateOfFlight;
    private String callsign;
    private String aircraftType;
    private String aircraftRegNo;
    private String departureIcao;
    private String arrivalIcao;
    private String departureTime;
    private String arrivalTime;

    private Double distanceFlown;
    private Double airTime;
    private Flightplan flightplan;

    private Position1 firstSeen;
    private Position1 lastSeen;
    private Position1 takeoff;
    private Position1 landing;

    private Boolean complete;
    private String trackingMode;

    public int getPilotNumber() {
        return pilotNumber;
    }

    public void setPilotNumber(int pilotNumber) {
        this.pilotNumber = pilotNumber;
    }

    public LocalDate getDateOfFlight() {
        return dateOfFlight;
    }

    public void setDateOfFlight(LocalDate dateOfFlight) {
        this.dateOfFlight = dateOfFlight;
    }

    public String getCallsign() {
        return callsign;
    }

    public void setCallsign(String callsign) {
        this.callsign = callsign;
    }

    public String getAircraftType() {
        return aircraftType;
    }

    public void setAircraftType(String aircraftType) {
        this.aircraftType = aircraftType;
    }

    public String getAircraftRegNo() {
        return aircraftRegNo;
    }

    public void setAircraftRegNo(String aircraftRegNo) {
        this.aircraftRegNo = aircraftRegNo;
    }

    public String getDepartureIcao() {
        return departureIcao;
    }

    public void setDepartureIcao(String departureIcao) {
        this.departureIcao = departureIcao;
    }

    public String getArrivalIcao() {
        return arrivalIcao;
    }

    public void setArrivalIcao(String arrivalIcao) {
        this.arrivalIcao = arrivalIcao;
    }

    public String getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(String departureTime) {
        this.departureTime = departureTime;
    }

    public String getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(String arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public Double getDistanceFlown() {
        return distanceFlown;
    }

    public void setDistanceFlown(Double distanceFlown) {
        this.distanceFlown = distanceFlown;
    }

    public Double getAirTime() {
        return airTime;
    }

    public void setAirTime(Double airTime) {
        this.airTime = airTime;
    }

    public Flightplan getFlightplan() {
        return flightplan;
    }

    public void setFlightplan(Flightplan flightplan) {
        this.flightplan = flightplan;
    }

    public Position1 getTakeoff() {
        return takeoff;
    }

    public void setTakeoff(Position1 takeoff) {
        this.takeoff = takeoff;
    }

    public Position1 getLanding() {
        return landing;
    }

    public void setLanding(Position1 landing) {
        this.landing = landing;
    }

    public Boolean getComplete() {
        return complete;
    }

    public void setComplete(Boolean complete) {
        this.complete = complete;
    }

    public String getTrackingMode() {
        return trackingMode;
    }

    public void setTrackingMode(String trackingMode) {
        this.trackingMode = trackingMode;
    }

    public static Position1 position(Position position) {
        return new Position1(position);
    }

    public static int compareByTakeoff(Flight1 flight1, Flight1 flight2) {
        return flight1.getTakeoff().getReportInfo().getReport().compareTo(flight2.getTakeoff().getReportInfo().getReport());
    }

    public static class Position1 {
        private final ReportInfoDto reportInfo;
        private final Geo.Coords coords;
        private final String status;

        private Position1(Position position) {
            this.reportInfo = new ReportInfoDto(position.getReportInfo());
            this.coords = position.isPositionKnown() ? position.getCoords() : null;
            this.status = position.getStatus();
        }

        public ReportInfoDto getReportInfo() {
            return reportInfo;
        }

        public Geo.Coords getCoords() {
            return coords;
        }

        public String getStatus() {
            return status;
        }
    }
}
