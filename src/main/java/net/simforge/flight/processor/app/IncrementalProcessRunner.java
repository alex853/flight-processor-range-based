package net.simforge.flight.processor.app;

import net.simforge.flight.processor.rangebased.RunIncrementalProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class IncrementalProcessRunner {
    private static final Logger log = LoggerFactory.getLogger(IncrementalProcessRunner.class);

    @Scheduled(fixedRate = 1000)
    public void run() {
        RunIncrementalProcess.run();
    }
}
