package net.simforge.flight.processor.rangebased.track;

import net.simforge.flight.processor.rangebased.Flight1;
import net.simforge.flight.processor.rangebased.Track1;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class Test__OnGroundTime extends AbstractTrackTest {

    @Test
    public void test__everything_is_ok() throws IOException {
        List<Flight1> flights = process(811636,
                "/snapshots/pilot-811636_from-1000000_amount-127321.csv",
                "20200424073648",
                "20200424173948");

        assertEquals(1, flights.size());

        Flight1 flight = flights.get(0);
        assertFlight(flight,
                "QFA3",
                "B744",
                "VHOEI",
                "YSSY",
                "PHNL",
                true,
                Track1.TrackingMode.Ideal);

        assertEquals("08:03", flight.getFirstSeen().getTime());
        assertEquals("08:29", flight.getTakeoff().getTime());
        assertEquals("17:13", flight.getLanding().getTime());
        assertEquals("17:22", flight.getLastSeen().getTime());

        assertEquals(9.30, flight.getFlightTime(), 0.05);
        assertEquals(8.75, flight.getAirTime(), 0.05);
    }
}