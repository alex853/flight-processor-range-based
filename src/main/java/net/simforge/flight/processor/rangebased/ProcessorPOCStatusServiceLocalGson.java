package net.simforge.flight.processor.rangebased;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.simforge.commons.io.IOHelper;
import net.simforge.networkview.core.report.ReportInfo;
import net.simforge.networkview.core.report.ReportInfoDto;

import java.io.File;
import java.io.IOException;

public class ProcessorPOCStatusServiceLocalGson implements ProcessorPOCStatusService {
    private final File storageRoot;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public ProcessorPOCStatusServiceLocalGson(String storagePath) {
        this.storageRoot = new File(storagePath);
    }

    @Override
    public PilotContext loadPilotContext(int pilotNumber) {
        return load(pilotContextFile(pilotNumber), PilotContext.class);
    }

    @Override
    public PilotContext createPilotContext(int pilotNumber) {
        PilotContext pilotContext = loadPilotContext(pilotNumber);
        if (pilotContext != null) {
            throw new IllegalArgumentException("Pilot context for pilot " + pilotNumber + " exists");
        }
        pilotContext = new PilotContext(pilotNumber);
        savePilotContext(pilotContext);
        return pilotContext;
    }

    @Override
    public void savePilotContext(PilotContext pilotContext) {
        save(pilotContextFile(pilotContext.getPilotNumber()), pilotContext);
    }

    private File pilotContextFile(int pilotNumber) {
        return new File(storageRoot, pilotFolderName(pilotNumber) + "/pilot-context.json");
    }

    private String pilotFolderName(int pilotNumber) {
        String pilotNumberGroup = (pilotNumber / 1000) + "xxx";
        return pilotNumberGroup + "/" + pilotNumber;
    }

    @Override
    public ReportInfo loadLastProcessedReport() {
        ProcessorStatus processorStatus = load(getProcessorStatusFile(), ProcessorStatus.class);
        if (processorStatus == null) {
            return null;
        }
        return processorStatus.getLastProcessedReport();
    }

    @Override
    public void saveLastProcessedReport(ReportInfo report) {
        ProcessorStatus processorStatus = load(getProcessorStatusFile(), ProcessorStatus.class);
        if (processorStatus == null) {
            processorStatus = new ProcessorStatus();
        }
        processorStatus.setLastProcessedReport(report);
        save(getProcessorStatusFile(), processorStatus);
    }

    private File getProcessorStatusFile() {
        return new File(storageRoot, "/processor-status.json");
    }

    private <T> T load(File file, Class<T> aClass) {
        if (!file.exists()) {
            return null;
        }
        try {
            String json = IOHelper.loadFile(file);
            return gson.fromJson(json, aClass);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void save(File file, Object object) {
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        String json = gson.toJson(object);
        try {
            IOHelper.saveFile(file, json);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static class ProcessorStatus {
        private ReportInfoDto lastProcessedReport;

        public ReportInfo getLastProcessedReport() {
            return lastProcessedReport;
        }

        public void setLastProcessedReport(ReportInfo lastProcessedReport) {
            this.lastProcessedReport = new ReportInfoDto(lastProcessedReport);
        }
    }
}
