package net.simforge.flight.processor.rangebased;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class UnknownAircraftTypes {
    private static final Logger log = LoggerFactory.getLogger(UnknownAircraftTypes.class);

    private final static Map<String, Integer> data = new HashMap<>();
    private static boolean changedSinceLastStats;
    private static long lastStats;

    public static synchronized void add(String aircraftType) {
        data.compute(aircraftType, (unused, current) -> current != null ? current + 1 : 1);
        changedSinceLastStats = true;
    }

    public static synchronized List<Map.Entry<String, Integer>> getAll() {
        List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(data.entrySet());
        sortedEntries.sort(Comparator.comparingInt(e -> -e.getValue()));
        return sortedEntries;

    }

    public static synchronized List<Map.Entry<String, Integer>> getTop10() {
        List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(data.entrySet());
        sortedEntries.sort(Comparator.comparingInt(e -> -e.getValue()));
        return sortedEntries.size() < 10 ? sortedEntries : sortedEntries.subList(0, 10);
    }

    public static synchronized void clear() {
        data.clear();
    }

    public static synchronized void printStats() {
        if (!changedSinceLastStats) {
            return;
        }

        if (System.currentTimeMillis() - lastStats < 60000L) {
            return;
        }

        lastStats = System.currentTimeMillis();
        changedSinceLastStats = false;

        List<Map.Entry<String, Integer>> top10 = getTop10();
        int printed = 0;
        log.info("                        TOP 10");
        for (int i = top10.size() - 1; i >= 0; i--) {
            Map.Entry<String, Integer> entry = top10.get(i);
            log.info("                         * {} - {} times", entry.getKey(), entry.getValue());
            printed++;
            if (printed == 10) {
                break;
            }
        }
        log.info("                        Total {} aircraft types", top10.size());
    }
}
