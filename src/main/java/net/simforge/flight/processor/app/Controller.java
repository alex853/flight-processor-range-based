package net.simforge.flight.processor.app;

import com.google.common.collect.Lists;
import net.simforge.flight.core.storage.FlightStorageService;
import net.simforge.flight.core.storage.impl.LocalGsonFlightStorage;
import net.simforge.flight.processor.rangebased.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@RestController
@RequestMapping("service/v1")
@CrossOrigin
public class Controller {
    private static final Logger log = LoggerFactory.getLogger(Controller.class);

    @GetMapping("hello-world")
    public String getHelloWorld() {
        return "Hello, World!";
    }

    @GetMapping("status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> status = new HashMap<>();

        int processedReports = LastProcessedReports.reportsProcessedInLast10Mins();
        status.put("processedReports", processedReports);

        boolean ok = processedReports >= 4;

        status.put("status", (ok ? "OK" : "FAIL"));

        Map<String, Integer> memoryReport = new TreeMap<>();
        memoryReport.put("used", MemoryReport.getUsedMB());
        memoryReport.put("free", MemoryReport.getFreeMB());
        memoryReport.put("total", MemoryReport.getTotalMB());
        memoryReport.put("max", MemoryReport.getMaxMB());
        status.put("memory", memoryReport);

        status.put("trackStats", IncrementalProcessor.trackStats);

        return ResponseEntity.ok(status);
    }

    @GetMapping("status/aircrafts/top10")
    public ResponseEntity<List<Map.Entry<String, Integer>>> getStatusAircraftsTop10() {
        return ResponseEntity.ok(UnknownAircraftTypes.getTop10());
    }

    @GetMapping("status/aircrafts/all")
    public ResponseEntity<List<Map.Entry<String, Integer>>> getStatusAircraftsAll() {
        return ResponseEntity.ok(UnknownAircraftTypes.getAll());
    }

    @GetMapping("status/aircrafts/clear")
    public void clearAircrafts() {
        UnknownAircraftTypes.clear();
    }

    @GetMapping("flights")
    public ResponseEntity<List<Flight1>> getFlights(@RequestParam("pilotNumber") Integer pilotNumber) {
        log.info("test log message");
        String storagePath = "./storage";
        FlightStorageService flightStorageService = new LocalGsonFlightStorage(storagePath);
        List<Flight1> flights = Lists.newArrayList(flightStorageService.loadAllFlights(pilotNumber));
        flights.sort(Flight1::compareByFirstSeen);
        return ResponseEntity.ok(flights);
    }
}
