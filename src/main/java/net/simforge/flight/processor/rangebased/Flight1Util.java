package net.simforge.flight.processor.rangebased;

import net.simforge.networkview.core.report.ReportRange;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Flight1Util {
    public static Collection<Flight1> findOverlappedFlights(ReportRange flight1Range, Collection<Flight1> flights) {
        List<Flight1> result = new ArrayList<>();
        for (Flight1 flight : flights) {
            ReportRange flightRange = ReportRange.between(flight.getTakeoff().getReportInfo(), flight.getLanding().getReportInfo());
            ReportRange intersection = flight1Range.intersect(flightRange);
            if (intersection != null) {
                result.add(flight);
            }
        }
        return result;
    }
}
