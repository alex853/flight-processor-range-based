package net.simforge.flight.core.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.simforge.commons.bm.BMC;
import net.simforge.commons.io.IOHelper;
import net.simforge.flight.processor.rangebased.Flight1;
import net.simforge.flight.processor.rangebased.Flight1Util;
import net.simforge.networkview.core.report.ReportInfo;
import net.simforge.networkview.core.report.ReportUtils;
import net.simforge.networkview.core.report.ReportRange;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class LocalGsonFlightStorage implements FlightStorageService {
    private final File storageRoot;

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public LocalGsonFlightStorage(String storagePath) {
        this.storageRoot = new File(storagePath);
    }

    @Override
    public Collection<Flight1> loadFlights(int pilotNumber, ReportInfo fromReport, ReportInfo toReport) {
        try (BMC ignored = BMC.start("LocalGsonFlightStorage.loadFlights")) {
            File flightsFolder = flightsFolder(pilotNumber);
            File[] flightFiles = flightsFolder.listFiles((file, s) -> s.endsWith(".json") && s.length() >= 14 && ReportUtils.isTimestamp(s.substring(0, 14)));
            if (flightFiles == null) {
                return new ArrayList<>();
            }
            List<Flight1> flights = Arrays.stream(flightFiles).map(flightFile -> {
                try {
                    String json = IOHelper.loadFile(flightFile);
                    return gson.fromJson(json, Flight1.class);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).collect(Collectors.toList());
            return Flight1Util.findOverlappedFlights(ReportRange.between(fromReport, toReport), flights);
        }
    }

    @Override
    public void saveFlight(Flight1 flight) {
        try (BMC ignored = BMC.start("LocalGsonFlightStorage.saveFlight")) {
            File flightFile = flightFile(flight);
            //noinspection ResultOfMethodCallIgnored
            flightFile.getParentFile().mkdirs();
            String json = gson.toJson(flight);
            try {
                IOHelper.saveFile(flightFile, json);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void deleteFlight(Flight1 flight) {
        try (BMC ignored = BMC.start("LocalGsonFlightStorage.deleteFlight")) {
            File flightFile = flightFile(flight);
            //noinspection ResultOfMethodCallIgnored
            flightFile.delete();
        }
    }

    private File flightsFolder(int pilotNumber) {
        return new File(storageRoot, pilotFolderName(pilotNumber) + "/flights");
    }

    private File flightFile(Flight1 flight) {
        return new File(flightsFolder(flight.getPilotNumber()), flight.getTakeoff().getReportInfo().getReport() + ".json");
    }

    private String pilotFolderName(int pilotNumber) {
        String pilotNumberGroup = (pilotNumber / 1000) + "xxx";
        return pilotNumberGroup + "/" + pilotNumber;
    }
}
