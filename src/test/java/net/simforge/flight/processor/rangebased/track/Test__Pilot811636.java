package net.simforge.flight.processor.rangebased.track;

import net.simforge.flight.processor.rangebased.Flight1;
import net.simforge.flight.processor.rangebased.Track1;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class Test__Pilot811636 extends AbstractTrackTest {

    private static final int pilotNumber = 811636;

    @Test
    public void test__everything() throws IOException {
        List<Flight1> flights = process(pilotNumber,
                "/snapshots/pilot-811636_from-1000000_amount-127321.csv");

        assertEquals(104, flights.size());
    }

    @Test
    public void test__2020_04_24__YSSY_PHNL_ideal() throws IOException {
        List<Flight1> flights = process(pilotNumber,
                "/snapshots/pilot-811636_from-1000000_amount-127321.csv",
                "20200424073648",
                "20200424173948");

        assertEquals(1, flights.size());

        assertFlight(flights.get(0),
                "QFA3",
                "B744",
                "VHOEI",
                "YSSY",
                "PHNL",
                true,
                Track1.TrackingMode.Ideal);
    }

    @Test
    public void test__2020_04_26__EGBB_LCLK_ideal() throws IOException {
        List<Flight1> flights = process(pilotNumber,
                "/snapshots/pilot-811636_from-1000000_amount-127321.csv",
                "20200426123158",
                "20200426190258");

        assertEquals(1, flights.size());

        assertFlight(flights.get(0),
                "EXS1269",
                "B738",
                "GCELJ",
                "EGBB",
                "LCLK",
                true,
                Track1.TrackingMode.Ideal);
    }
}
