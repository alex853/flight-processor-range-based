package net.simforge.flight.processor.rangebased.track;

import net.simforge.flight.processor.rangebased.Flight1;
import net.simforge.flight.processor.rangebased.Track1;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class Test__Pilot913904 extends AbstractTrackTest {

    private static final int pilotNumber = 913904;

    @Test
    public void test__2018_11_23__four_legs_flyday() throws IOException {
        List<Flight1> flights = process(pilotNumber,
                "/snapshots/pilot-913904_2018-11-23.csv");

        assertEquals(4, flights.size());

        assertFlightRoute(flights.get(0), "EGKK", "EGJJ");
        assertFlightRoute(flights.get(1), "EGJJ", "EGKK");
        assertFlightRoute(flights.get(2), "EGKK", "EHAM");
        assertFlightRoute(flights.get(3), "EHAM", "EHAM");
    }

    @Test
    public void test__2023_07_09__2023_07_11__series_of_airbus_flights() throws IOException {
        List<Flight1> flights = process(pilotNumber,
                "/snapshots/pilot-913904_2023-07-09_2023-07-11.csv");

        assertEquals(5, flights.size());

        assertFlightRoute(flights.get(0), "UATT", "UBBB");
        assertFlightRoute(flights.get(1), "UBBB", null); // FS crashed few mins after takeoff
        assertFlightRoute(flights.get(2), "UBBB", "LTCI");
        assertFlightRoute(flights.get(3), "LTCI", "LTCI");
        assertFlightRoute(flights.get(4), "LTCI", "LTAR");
    }

    @Test
    public void test__2023_07_15__egll_lclk() throws IOException {
        List<Flight1> flights = process(pilotNumber,
                "/snapshots/pilot-913904_2023-07-15.csv");

        assertEquals(1, flights.size());

        Flight1 flight = flights.get(0);
        assertFlight(flight,
                "BAW6CA",
                "A320",
                "GEUYC",
                "EGLL",
                "LCLK",
                true,
                Track1.TrackingMode.Ideal);

        assertFlightTimes(flight,
                "08:16",
                "08:41",
                "12:55",
                "12:59");

        assertEquals(4.75, flight.getFlightTime(), 0.05);
        assertEquals(4.25, flight.getAirTime(), 0.05);
    }

    @Test
    public void test__2023_07_16__lclk_limc__non_completed_in_online() throws IOException {
        List<Flight1> flights = process(pilotNumber,
                "/snapshots/pilot-913904_2023-07-16.csv");

        assertEquals(1, flights.size());

        Flight1 flight = flights.get(0);
        assertFlight(flight,
                "BCS7061",
                "B752",
                "ECFTR",
                "LCLK",
                null,
                false,
                Track1.TrackingMode.Incomplete);

        assertFlightTimes(flight,
                "08:18",
                "08:56",
                null,
                "12:08");

        assertEquals(3.85, flight.getFlightTime(), 0.05);
        assertEquals(3.25, flight.getAirTime(), 0.05);
    }
}
