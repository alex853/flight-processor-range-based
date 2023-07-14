package net.simforge.flight.processor.rangebased.track;

import net.simforge.flight.processor.rangebased.Flight1;
import net.simforge.flight.processor.rangebased.Track1;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class Test__UnrealisticJump extends AbstractTrackTest {

    @Test
    public void test__flight_unexpectedly_moved_to_departure_point__probably_fs_crash_recovery__should_not_fail__should_filter_out_this_from_flight_data() throws IOException {
        List<Flight1> flights = process(811636,
                "/snapshots/pilot-811636_from-1000000_amount-127321.csv",
                "20200610080659",
                "20200610103259");

        assertEquals(3, flights.size());

        Flight1 flight = flights.get(0);

        assertFlight(flight,
                "BAW16B",
                "B77W",
                "N713SB",
                "WSSS",
                null,
                false,
                Track1.TrackingMode.Incomplete);

        assertFlightTimes(flight,
                "08:23",
                "08:51",
                null,
                "10:04");
    }
}
