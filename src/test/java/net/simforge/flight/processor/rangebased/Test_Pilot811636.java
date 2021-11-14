package net.simforge.flight.processor.rangebased;

import org.junit.Before;
import org.junit.Ignore;

import java.util.Collection;

@Ignore
public class Test_Pilot811636 extends AbstractTest {
    @Before
    public void before() {
        pilotNumber = 811636;
        csvSnapshot = "/snapshots/pilot-811636_from-1000000_amount-127321.csv";
    }

    @Override
    protected void checkFlights(Collection<Flight1> flights) {
        System.out.println();
    }
}
