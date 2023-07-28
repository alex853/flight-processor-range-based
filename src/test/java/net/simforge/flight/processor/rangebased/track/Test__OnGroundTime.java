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

        assertFlightTimes(flight,
                "08:03",
                "08:29",
                "17:13",
                "17:22");

        assertEquals(9.30, flight.getFlightTime(), 0.05);
        assertEquals(8.75, flight.getAirTime(), 0.05);
    }

    @Test
    public void test__just_connected_shortly_on_ground_without_flying__no_fail_expected__on_ground_only_expected() throws IOException {
        List<Flight1> flights = process(811636,
                "/snapshots/pilot-811636_from-1000000_amount-127321.csv",
                "20200429082154",
                "20200429092854");

        assertEquals(1, flights.size());

        Flight1 flight1 = flights.get(0);

        assertEquals(Track1.TrackingMode.OnGroundOnly.name(), flight1.getTrackingMode());
    }

    @Test
    public void test__connected_on_ground_till_end_of_track__like_being_online_and_preparing_for_a_flight__on_ground_only_expected() throws IOException {
        List<Flight1> flights = process(811636,
                "/snapshots/pilot-811636_from-1000000_amount-127321.csv",
                "20200429092854",
                "20200429102654");

        assertEquals(1, flights.size());

        Flight1 flight1 = flights.get(0);

        assertEquals(Track1.TrackingMode.OnGroundOnly.name(), flight1.getTrackingMode());
    }

    @Test
    public void test__splitting_ground_time_between_two_flights() throws IOException {
        List<Flight1> flights = process(913904,
                "/snapshots/pilot-913904_2018-11-23.csv",
                "20181123071102",
                "20181123103005");

        assertEquals(2, flights.size());

        Flight1 flight1 = flights.get(0);
        assertFlight(flight1,
                "BAW70Y",
                "A319",
                "",
                "EGKK",
                "EGJJ",
                true,
                Track1.TrackingMode.Ideal);
        assertFlightTimes(flight1,
                "07:16",
                "07:51",
                "08:42",
                "08:54");

        Flight1 flight2 = flights.get(1);
        assertFlight(flight2,
                "BAW160J",
                "A319",
                "",
                "EGJJ",
                "EGKK",
                true,
                Track1.TrackingMode.Ideal);
        assertFlightTimes(flight2,
                "08:57",
                "09:23",
                "10:03",
                "10:09");
    }
}
