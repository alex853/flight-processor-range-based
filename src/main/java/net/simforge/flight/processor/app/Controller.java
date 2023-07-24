package net.simforge.flight.processor.app;

import com.google.common.collect.Lists;
import net.simforge.flight.core.storage.FlightStorageService;
import net.simforge.flight.core.storage.impl.LocalGsonFlightStorage;
import net.simforge.flight.processor.rangebased.Flight1;
import net.simforge.flight.processor.rangebased.LastProcessedReports;
import net.simforge.flight.processor.rangebased.UnknownAircraftTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("service/v1")
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
        status.put("processedReportsInLast10Mins", processedReports);

        boolean ok = processedReports >= 4;

        status.put("status", (ok ? "OK" : "FAIL"));

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
