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

        assertEquals(1, flights.size());

        // todo ak check that flight does not contain section
    }
}
