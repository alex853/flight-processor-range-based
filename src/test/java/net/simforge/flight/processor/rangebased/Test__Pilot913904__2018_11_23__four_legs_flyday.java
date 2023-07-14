package net.simforge.flight.processor.rangebased;

import net.simforge.networkview.core.report.ReportInfoDto;
import org.junit.Before;
import org.junit.Ignore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.Assert.assertEquals;

@Deprecated
@Ignore
public class Test__Pilot913904__2018_11_23__four_legs_flyday extends AbstractTest {

    @Before
    public void before() {
        pilotNumber = 913904;
        csvSnapshot = "/snapshots/pilot-913904_2018-11-23.csv";
        savedProcessedReport = new ReportInfoDto(1016601L, "20181123071339");
    }

    @Override
    protected void checkFlights(Collection<Flight1> flights) {
        assertEquals(4, flights.size());

        List<Flight1> sortedFlights = new ArrayList<>(flights);
        sortedFlights.sort(Flight1::compareByTakeoff);

        Flight1 flight = sortedFlights.get(0);
        assertEquals("EGKK", null /*flight.getDepartureIcao()*/);
        assertEquals("EGJJ", null /*flight.getArrivalIcao()*/);

        flight = sortedFlights.get(1);
        assertEquals("EGJJ", null /*flight.getDepartureIcao()*/);
        assertEquals("EGKK", null /*flight.getArrivalIcao()*/);

        flight = sortedFlights.get(2);
        assertEquals("EGKK", null /*flight.getDepartureIcao()*/);
        assertEquals("EHAM", null /*flight.getArrivalIcao()*/);

        flight = sortedFlights.get(3);
        assertEquals("EHAM", null /*flight.getDepartureIcao()*/);
        assertEquals("EHAM", null /*flight.getArrivalIcao()*/);
    }
}
