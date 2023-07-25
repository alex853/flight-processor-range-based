package net.simforge.flight.processor.rangebased;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MemoryReport {
    private static final Logger log = LoggerFactory.getLogger(MemoryReport.class);

    private static long lastMemoryReportTs;

    public static void print() {
        if (lastMemoryReportTs + 10 * 60 * 1000 < System.currentTimeMillis()) {
            log.info("Memory report, MB: Used = {}, Free = {}, Total = {}, Max = {}", getUsedMB(), getFreeMB(), getTotalMB(), getMaxMB());

            lastMemoryReportTs = System.currentTimeMillis();
        }
    }

    private static int toMB(long size) {
        return (int) (size / 0x100000L);
    }

    public static int getUsedMB() {
        return toMB(Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) ;
    }

    public static int getFreeMB() {
        return toMB(Runtime.getRuntime().freeMemory());
    }

    public static int getTotalMB() {
        return toMB(Runtime.getRuntime().totalMemory());
    }

    public static int getMaxMB() {
        return toMB(Runtime.getRuntime().maxMemory());
    }
}
