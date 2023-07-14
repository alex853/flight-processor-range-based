package net.simforge.flight.processor.rangebased.track;

import net.simforge.flight.processor.rangebased.Flight1;
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

        assertEquals(4, flights.size());

        assertFlightRoute(flights.get(0), "UATT", "UBBB");
        // there is incomplete flight UBBB-LTCI terminated in few minutes after takeoff due to FS crash
        assertFlightRoute(flights.get(1), "UBBB", "LTCI");
        assertFlightRoute(flights.get(2), "LTCI", "LTCI");
        assertFlightRoute(flights.get(3), "LTCI", "LTAR");
    }
}
