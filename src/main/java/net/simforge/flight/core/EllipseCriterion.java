package net.simforge.flight.core;

import net.simforge.commons.misc.Geo;
import net.simforge.networkview.core.Position;
import net.simforge.refdata.airports.Airport;
import net.simforge.refdata.airports.Airports;

public class EllipseCriterion implements Criterion {

    private Position takeoffPosition;
    private Flightplan flightplan;

    public EllipseCriterion(Position takeoffPosition, Flightplan flightplan) {
        this.takeoffPosition = takeoffPosition;
        this.flightplan = flightplan;
    }

    @Override
    public boolean meets(Position position) {
        if (takeoffPosition == null) {
            return false;
        }
        if (!takeoffPosition.isInAirport()) {
            return false;
        }

        if (flightplan == null) {
            return false;
        }
        String destinationIcao = flightplan.getDestination();
        if (destinationIcao == null) {
            return false;
        }
        Airport destinationAirport = Airports.get().getByIcao(destinationIcao);
        if (destinationAirport == null) {
            return false;
        }

        Geo.Coords takeoffCoords = takeoffPosition.getCoords();
        Geo.Coords destinationCoords = destinationAirport.getCoords();
        Geo.Coords positionCoords = position.getCoords();

        double dist = Geo.distance(takeoffCoords, destinationCoords);

        // https://ru.wikipedia.org/wiki/%D0%AD%D0%BB%D0%BB%D0%B8%D0%BF%D1%81
        double c = dist / 2;
        double a = c + 100; // 100 nm
        double b = Math.sqrt(a * a - c * c);

        double summedDist = Geo.distance(takeoffCoords, positionCoords) + Geo.distance(positionCoords, destinationCoords);

        return summedDist <= 2 * a;
    }
}
