package net.simforge.flight.processor.rangebased;

import net.simforge.commons.misc.Geo;
import net.simforge.commons.misc.JavaTime;
import net.simforge.flight.core.Flightplan;
import net.simforge.networkview.core.report.ReportInfoDto;
import net.simforge.networkview.core.Position;

public class Flight1 {
    private int pilotNumber;
    private String dateOfFlight;
    private String callsign;
    private String aircraftType;
    private String aircraftRegNo;

    private Double distanceFlown;
    private Double flightTime;
    private Double airTime;
    private Flightplan flightplan;

    private Position1 firstSeen;
    private Position1 takeoff;
    private Position1 landing;
    private Position1 lastSeen;

    private Boolean complete;
    private String trackingMode;

    public int getPilotNumber() {
        return pilotNumber;
    }

    public void setPilotNumber(int pilotNumber) {
        this.pilotNumber = pilotNumber;
    }

    public String getDateOfFlight() {
        return dateOfFlight;
    }

    public void setDateOfFlight(String dateOfFlight) {
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

    public Double getDistanceFlown() {
        return distanceFlown;
    }

    public void setDistanceFlown(Double distanceFlown) {
        this.distanceFlown = distanceFlown;
    }

    public Double getFlightTime() {
        return flightTime;
    }

    public void setFlightTime(Double flightTime) {
        this.flightTime = flightTime;
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

    public Position1 getFirstSeen() {
        return firstSeen;
    }

    public void setFirstSeen(Position1 firstSeen) {
        this.firstSeen = firstSeen;
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

    public Position1 getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(Position1 lastSeen) {
        this.lastSeen = lastSeen;
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

    public static int compareByFirstSeen(Flight1 flight1, Flight1 flight2) {
        return flight1.getFirstSeen().getReportInfo().getReport().compareTo(flight2.getFirstSeen().getReportInfo().getReport());
    }

    public static class Position1 {
        private final ReportInfoDto reportInfo;
        private final Geo.Coords coords;
        private final String icao;
        private final String date;
        private final String time;
        private final String status;

        private Position1(Position position) {
            this.reportInfo = new ReportInfoDto(position.getReportInfo());
            this.coords = position.isPositionKnown() ? position.getCoords() : null;
            this.icao = position.getAirportIcao();
            this.date = JavaTime.yMd.format(reportInfo.getDt());
            this.time = JavaTime.hhmm.format(reportInfo.getDt());
            this.status = position.getStatus();
        }

        public ReportInfoDto getReportInfo() {
            return reportInfo;
        }

        public Geo.Coords getCoords() {
            return coords;
        }

        public String getIcao() {
            return icao;
        }

        public String getDate() {
            return date;
        }

        public String getTime() {
            return time;
        }

        public String getStatus() {
            return status;
        }
    }
}
