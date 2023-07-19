package net.simforge.flight.processor.rangebased;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class UnknownAircraftTypes {
    private static final Logger logger = LoggerFactory.getLogger(UnknownAircraftTypes.class);

    private final static Map<String, Integer> data = new HashMap<>();
    private static boolean changedSinceLastStats;
    private static long lastStats;

    public static void add(String aircraftType) {
        data.compute(aircraftType, (unused, current) -> current != null ? current + 1 : 1);
        changedSinceLastStats = true;
    }

    public static void printStats() {
        if (!changedSinceLastStats) {
            return;
        }

        if (System.currentTimeMillis() - lastStats < 60000L) {
            return;
        }

        lastStats = System.currentTimeMillis();
        changedSinceLastStats = false;

        List<Map.Entry<String, Integer>> sortedEntries = new ArrayList<>(data.entrySet());
        sortedEntries.sort(Comparator.comparingInt(Map.Entry::getValue));
        for (int i = sortedEntries.size() - 1; i >= 0; i--) {
            Map.Entry<String, Integer> entry = sortedEntries.get(i);
            logger.info("                         * {} - {} times", entry.getKey(), entry.getValue());
        }
        logger.info("                        Total {} aircraft types", sortedEntries.size());
    }
}
