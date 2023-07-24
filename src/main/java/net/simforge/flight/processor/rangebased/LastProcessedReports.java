package net.simforge.flight.processor.rangebased;


import net.simforge.commons.misc.JavaTime;
import net.simforge.flight.processor.app.Controller;
import net.simforge.networkview.core.report.ReportUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.LinkedList;

public class LastProcessedReports {
    private static final Logger log = LoggerFactory.getLogger(Controller.class);
    private static final LinkedList<String> lastProcessedReports = new LinkedList<>();

    public static synchronized int reportsProcessedInLast10Mins() {
        String threshold = ReportUtils.toTimestamp(JavaTime.nowUtc().minusMinutes(10));
        log.info("threshold {}, lastProcessedReports {}", threshold, lastProcessedReports);
        int counter = 0;
        Iterator<String> it = lastProcessedReports.descendingIterator();
        while (it.hasNext()) {
            String report = it.next();
            if (report.compareTo(threshold) < 0) {
                break;
            }
            counter++;
        }
        return counter;
    }

    public static synchronized void save(String report) {
        lastProcessedReports.add(report);
        while (lastProcessedReports.size() > 10) {
            lastProcessedReports.remove(0);
        }
    }
}
