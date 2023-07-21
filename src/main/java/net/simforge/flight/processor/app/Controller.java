package net.simforge.flight.processor.app;

import com.google.common.collect.Lists;
import net.simforge.flight.core.storage.FlightStorageService;
import net.simforge.flight.core.storage.impl.LocalGsonFlightStorage;
import net.simforge.flight.processor.rangebased.Flight1;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("service/v1")
//@Slf4j
public class Controller {
    @GetMapping("hello-world")
    public String getHelloWorld() {
        return "Hello, World!";
    }
    @GetMapping("flights")
    public ResponseEntity<List<Flight1>> getFlights(@RequestParam("pilotNumber") Integer pilotNumber) {
        String storagePath = "./env-hp/range-based-gson-local-storage";
        FlightStorageService flightStorageService = new LocalGsonFlightStorage(storagePath);
        List<Flight1> flights = Lists.newArrayList(flightStorageService.loadAllFlights(pilotNumber));
        flights.sort(Flight1::compareByFirstSeen);
        return ResponseEntity.ok(flights);
    }
}
