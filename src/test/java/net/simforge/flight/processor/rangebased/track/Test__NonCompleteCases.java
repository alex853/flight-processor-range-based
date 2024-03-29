package net.simforge.flight.processor.rangebased.track;

import net.simforge.flight.processor.rangebased.Flight1;
import net.simforge.flight.processor.rangebased.Track1;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class Test__NonCompleteCases extends AbstractTrackTest {

    private static final int pilotNumber = 811636;

    @Test
    public void test__tracked_till_midflight() throws IOException {
        List<Flight1> flights = process(pilotNumber,
                "/snapshots/pilot-811636_from-1000000_amount-127321.csv",
                "20200426123158",
                "20200426171858");

        assertEquals(1, flights.size());

        assertFlight(flights.get(0),
                "EXS1269",
                "B738",
                "GCELJ",
                "EGBB",
                null,
                false,
                Track1.TrackingMode.Incomplete);
    }

    @Test
    public void test__tracked_from_midflight() throws IOException {
        List<Flight1> flights = process(pilotNumber,
                "/snapshots/pilot-811636_from-1000000_amount-127321.csv",
                "20200426171858",
                "20200426190258");

        assertEquals(1, flights.size());

        assertFlight(flights.get(0),
                "EXS1269",
                "B738",
                "GCELJ",
                null,
                "LCLK",
                false,
                Track1.TrackingMode.Incomplete);
    }

    @Test
    public void test__cessna_with_30_hours_flight__should_not_be_allowed() throws IOException {
        List<Flight1> flights = process(pilotNumber,
                "/snapshots/pilot-1001023_2023-07-15_2023-07-16.csv");

        assertEquals(2, flights.size());
    }
}
