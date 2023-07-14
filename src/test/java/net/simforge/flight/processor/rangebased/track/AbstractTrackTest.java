package net.simforge.flight.processor.rangebased.track;

import net.simforge.commons.io.Csv;
import net.simforge.commons.io.IOHelper;
import net.simforge.flight.processor.rangebased.Flight1;
import net.simforge.flight.processor.rangebased.ReportTimeline;
import net.simforge.flight.processor.rangebased.Track1;
import net.simforge.networkview.core.report.ReportInfo;
import net.simforge.networkview.core.report.ReportRange;
import net.simforge.networkview.core.report.persistence.ReportPilotPosition;
import net.simforge.networkview.core.report.snapshot.CsvSnapshotReportOpsService;
import org.junit.Ignore;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@Ignore
public abstract class AbstractTrackTest {

    protected static void assertFlightRoute(final Flight1 flight,
                                            final String expectedTakeoffIcao,
                                            final String expectedLandingIcao) {
        assertPositionIcao(expectedTakeoffIcao, flight.getTakeoff());
        assertPositionIcao(expectedLandingIcao, flight.getLanding());
    }

    protected static void assertFlight(final Flight1 flight,
                                       final String expectedCallsign,
                                       final String expectedAirTime,
                                       final String expectedRegNo,
                                       final String expectedTakeoffIcao,
                                       final String expectedLandingIcao,
                                       final boolean expectedComplete,
                                       final Track1.TrackingMode expectedTrackingMode) {
        assertEquals(expectedCallsign, flight.getCallsign());
        assertEquals(expectedAirTime, flight.getAircraftType());
        assertEquals(expectedRegNo, flight.getAircraftRegNo());
        assertPositionIcao(expectedTakeoffIcao, flight.getTakeoff());
        assertPositionIcao(expectedLandingIcao, flight.getLanding());
        assertEquals(expectedComplete, flight.getComplete());
        assertEquals(expectedTrackingMode.name(), flight.getTrackingMode());
    }

    private static void assertPositionIcao(String expectedIcao, Flight1.Position1 actualPosition) {
        if (expectedIcao != null) {
            assertEquals(expectedIcao, actualPosition.getIcao());
        } else {
            assertNull(actualPosition);
        }
    }

    protected static void assertFlightTimes(Flight1 flight, String firstSeenTime, String takeoffTime, String landingTime, String lastSeenTime) {
        AbstractTrackTest.assertTime(firstSeenTime, flight.getFirstSeen());
        AbstractTrackTest.assertTime(takeoffTime, flight.getTakeoff());
        AbstractTrackTest.assertTime(landingTime, flight.getLanding());
        AbstractTrackTest.assertTime(lastSeenTime, flight.getLastSeen());
    }

    protected static void assertTime(String time, Flight1.Position1 position) {
        if (time != null) {
            assertEquals(time, position.getTime());
        } else {
            assertNull(position);
        }
    }

    protected List<Flight1> process(final int pilotNumber,
                                    final String csvSnapshot) throws IOException {
        InputStream is = AbstractTrackTest.class.getResourceAsStream(csvSnapshot);
        String csvContent = IOHelper.readInputStream(is);
        Csv csv = Csv.fromContent(csvContent);
        CsvSnapshotReportOpsService reportOpsService = new CsvSnapshotReportOpsService(csv);

        ReportTimeline timeline = ReportTimeline.load(reportOpsService);
        ReportInfo processTrackSinceReport = timeline.getFirstReport();
        ReportInfo processTrackTillReport = timeline.getLastReport();

        List<ReportPilotPosition> positions = reportOpsService.loadPilotPositionsSinceTill(pilotNumber, processTrackSinceReport, processTrackTillReport);
        Track1 track = Track1.build(ReportRange.between(processTrackSinceReport, processTrackTillReport), timeline, positions);
        return track.getFlights();
    }

    protected List<Flight1> process(final int pilotNumber,
                                    final String csvSnapshot,
                                    final String fromReport,
                                    final String toReport) throws IOException {
        InputStream is = AbstractTrackTest.class.getResourceAsStream(csvSnapshot);
        String csvContent = IOHelper.readInputStream(is);
        Csv csv = Csv.fromContent(csvContent);
        CsvSnapshotReportOpsService reportOpsService = new CsvSnapshotReportOpsService(csv);

        ReportTimeline timeline = ReportTimeline.load(reportOpsService);
        ReportInfo processTrackSinceReport = timeline.findPreviousReport(fromReport);
        ReportInfo processTrackTillReport = timeline.findPreviousReport(toReport);

        List<ReportPilotPosition> positions = reportOpsService.loadPilotPositionsSinceTill(pilotNumber, processTrackSinceReport, processTrackTillReport);
        Track1 track = Track1.build(ReportRange.between(processTrackSinceReport, processTrackTillReport), timeline, positions);
        return track.getFlights();
    }
}
