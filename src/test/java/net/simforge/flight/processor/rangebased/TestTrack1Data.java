package net.simforge.flight.processor.rangebased;

import com.google.common.collect.Lists;
import net.simforge.commons.io.Csv;
import net.simforge.commons.io.IOHelper;
import net.simforge.networkview.core.Position;
import net.simforge.networkview.core.report.ReportInfo;
import net.simforge.networkview.core.report.ReportInfoDto;
import net.simforge.networkview.core.report.ReportRange;
import net.simforge.networkview.core.report.persistence.ReportOpsService;
import net.simforge.networkview.core.report.persistence.ReportPilotPosition;
import net.simforge.networkview.core.report.snapshot.CsvSnapshotReportOpsService;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.*;

public class TestTrack1Data {

    private ReportOpsService reportOpsService;
    private ReportTimeline timeline;

    private static final int pilotNumber = 811636;

    private static final ReportInfo SAMPLE_REPORT_1 = new ReportInfoDto(1003103L, "20200428120054");
    private static final ReportInfo SAMPLE_REPORT_2 = new ReportInfoDto(1003136L, "20200428130354");
    private static final ReportInfo SAMPLE_REPORT_3 = new ReportInfoDto(1003159L, "20200428134654");
    private static final ReportInfo SAMPLE_REPORT_4 = new ReportInfoDto(1003160L, "20200428134854");
    private static final ReportInfo SAMPLE_REPORT_5 = new ReportInfoDto(1003163L, "20200428135354");
    private static final ReportInfo SAMPLE_REPORT_6 = new ReportInfoDto(1003170L, "20200428140654");
    private static final ReportInfo SAMPLE_REPORT_10 = new ReportInfoDto(1003230L, "20200428160054");

    @Before
    public void before() throws IOException {
        InputStream is = TestReportTimeline.class.getResourceAsStream("/snapshots/pilot-811636_from-1000000_amount-127321.csv");
        String csvContent = IOHelper.readInputStream(is);
        Csv csv = Csv.fromContent(csvContent);
        this.reportOpsService = new CsvSnapshotReportOpsService(csv);
        this.timeline = ReportTimeline.load(reportOpsService);
    }

    @Test
    public void test__emptyTrackData() {
        // GIVEN
        Track1Data trackData = Track1Data.forPilot(pilotNumber);

        // WHEN

        // THEN
        assertFalse(trackData.getRange().isPresent());
        assertEquals(0, trackData.size());
    }

    @Test
    public void test__storePositions__allOnline__addingToEmptyTrackData__fine() {
        // GIVEN
        List<ReportPilotPosition> positions = reportOpsService.loadPilotPositionsSinceTill(pilotNumber, SAMPLE_REPORT_2, SAMPLE_REPORT_3);
        Track1Data trackData = Track1Data.forPilot(pilotNumber);

        // WHEN
        boolean success = trackData.storePositions(timeline, ReportRange.between(SAMPLE_REPORT_2, SAMPLE_REPORT_3), positions);

        // THEN
        assertTrue(success);
        assertEquals(24, trackData.size());
        assertEquals(SAMPLE_REPORT_2.getReport(), trackData.getRange().get().getSince().getReport());
        assertEquals(SAMPLE_REPORT_3.getReport(), trackData.getRange().get().getTill().getReport());
    }

    @Test
    public void test__storePositions__partiallyOffline__addingToEmptyTrackData__fine() {
        // GIVEN
        List<ReportPilotPosition> positions = reportOpsService.loadPilotPositionsSinceTill(pilotNumber, SAMPLE_REPORT_2, SAMPLE_REPORT_6);
        Track1Data trackData = Track1Data.forPilot(pilotNumber);

        // WHEN
        boolean success = trackData.storePositions(timeline, ReportRange.between(SAMPLE_REPORT_2, SAMPLE_REPORT_6), positions);

        // THEN
        assertTrue(success);
        assertEquals(35, trackData.size());
        assertEquals(SAMPLE_REPORT_2.getReport(), trackData.getRange().get().getSince().getReport());
        assertEquals(SAMPLE_REPORT_6.getReport(), trackData.getRange().get().getTill().getReport());
    }

    @Test
    public void test__storePositions__addingOneSingleAlreadyPresentedPosition__noChange() {
        // GIVEN
        List<ReportPilotPosition> positions = reportOpsService.loadPilotPositionsSinceTill(pilotNumber, SAMPLE_REPORT_2, SAMPLE_REPORT_3);
        Track1Data trackData = Track1Data.forPilot(pilotNumber);
        trackData.storePositions(timeline, ReportRange.between(SAMPLE_REPORT_2, SAMPLE_REPORT_3), positions);

        // WHEN
        ReportPilotPosition positionToAddLater = positions.get(15);
        boolean success = trackData.storePositions(timeline, NewMethods.rangeOf(positionToAddLater.getReport()), Lists.newArrayList(positionToAddLater));

        // THEN
        assertFalse(success);
        assertEquals(24, trackData.size());
        assertEquals(SAMPLE_REPORT_2.getReport(), trackData.getRange().get().getSince().getReport());
        assertEquals(SAMPLE_REPORT_3.getReport(), trackData.getRange().get().getTill().getReport());
    }

    @Test
    public void test__storePositions__addingOneSingleJustAfterTheLastReport__fine() {
        // GIVEN
        List<ReportPilotPosition> positions = reportOpsService.loadPilotPositionsSinceTill(pilotNumber, SAMPLE_REPORT_2, SAMPLE_REPORT_3);
        Track1Data trackData = Track1Data.forPilot(pilotNumber);
        trackData.storePositions(timeline, ReportRange.between(SAMPLE_REPORT_2, SAMPLE_REPORT_3), positions);

        // WHEN
        ReportPilotPosition positionToAddLater = reportOpsService.loadPilotPositionsSinceTill(pilotNumber, SAMPLE_REPORT_4, SAMPLE_REPORT_4).get(0);
        boolean success = trackData.storePositions(timeline, NewMethods.rangeOf(positionToAddLater.getReport()), Lists.newArrayList(positionToAddLater));

        // THEN
        assertTrue(success);
        assertEquals(25, trackData.size());
        assertEquals(SAMPLE_REPORT_2.getReport(), trackData.getRange().get().getSince().getReport());
        assertEquals(SAMPLE_REPORT_4.getReport(), trackData.getRange().get().getTill().getReport());
    }

    @Test
    public void test__storePositions__addingOneSingleWithSomeGapBetweenTheLastReport__noChange() {
        // GIVEN
        List<ReportPilotPosition> positions = reportOpsService.loadPilotPositionsSinceTill(pilotNumber, SAMPLE_REPORT_2, SAMPLE_REPORT_3);
        Track1Data trackData = Track1Data.forPilot(pilotNumber);
        trackData.storePositions(timeline, ReportRange.between(SAMPLE_REPORT_2, SAMPLE_REPORT_3), positions);

        // WHEN
        ReportPilotPosition positionToAddLater = reportOpsService.loadPilotPositionsSinceTill(pilotNumber, SAMPLE_REPORT_5, SAMPLE_REPORT_5).get(0);
        boolean success = trackData.storePositions(timeline, NewMethods.rangeOf(positionToAddLater.getReport()), Lists.newArrayList(positionToAddLater));

        // THEN
        assertFalse(success);
        assertEquals(24, trackData.size());
        assertEquals(SAMPLE_REPORT_2.getReport(), trackData.getRange().get().getSince().getReport());
        assertEquals(SAMPLE_REPORT_3.getReport(), trackData.getRange().get().getTill().getReport());
    }

    @Test
    public void test__getPositions__withinExistingRange__fine() {
        // GIVEN
        List<ReportPilotPosition> positions = reportOpsService.loadPilotPositionsSinceTill(pilotNumber, SAMPLE_REPORT_2, SAMPLE_REPORT_5);
        Track1Data trackData = Track1Data.forPilot(pilotNumber);
        trackData.storePositions(timeline, ReportRange.between(SAMPLE_REPORT_2, SAMPLE_REPORT_5), positions);

        // WHEN
        Optional<List<Position>> foundPositions = trackData.getPositions(ReportRange.between(SAMPLE_REPORT_3, SAMPLE_REPORT_4));

        // THEN
        assertTrue(foundPositions.isPresent());
        List<Position> foundPositionList = foundPositions.get();
        assertEquals(2, foundPositionList.size());
        assertEquals(SAMPLE_REPORT_3.getReport(), foundPositionList.get(0).getReportInfo().getReport());
        assertEquals(SAMPLE_REPORT_4.getReport(), foundPositionList.get(foundPositionList.size() - 1).getReportInfo().getReport());
    }

    @Test
    public void test__getPositions__exactAllPositions__fine() {
        // GIVEN
        List<ReportPilotPosition> positions = reportOpsService.loadPilotPositionsSinceTill(pilotNumber, SAMPLE_REPORT_2, SAMPLE_REPORT_5);
        Track1Data trackData = Track1Data.forPilot(pilotNumber);
        trackData.storePositions(timeline, ReportRange.between(SAMPLE_REPORT_2, SAMPLE_REPORT_5), positions);

        // WHEN
        Optional<List<Position>> foundPositions = trackData.getPositions(ReportRange.between(SAMPLE_REPORT_2, SAMPLE_REPORT_5));

        // THEN
        assertTrue(foundPositions.isPresent());
        List<Position> foundPositionList = foundPositions.get();
        assertEquals(28, foundPositionList.size());
        assertEquals(SAMPLE_REPORT_2.getReport(), foundPositionList.get(0).getReportInfo().getReport());
        assertEquals(SAMPLE_REPORT_5.getReport(), foundPositionList.get(foundPositionList.size() - 1).getReportInfo().getReport());
    }

    @Test
    public void test__getPositions__someRangePartiallyOutsideOfExistingRange__emptyResult() {
        // GIVEN
        List<ReportPilotPosition> positions = reportOpsService.loadPilotPositionsSinceTill(pilotNumber, SAMPLE_REPORT_2, SAMPLE_REPORT_4);
        Track1Data trackData = Track1Data.forPilot(pilotNumber);
        trackData.storePositions(timeline, ReportRange.between(SAMPLE_REPORT_2, SAMPLE_REPORT_4), positions);

        // WHEN
        Optional<List<Position>> foundPositions = trackData.getPositions(ReportRange.between(SAMPLE_REPORT_3, SAMPLE_REPORT_5));

        // THEN
        assertFalse(foundPositions.isPresent());
    }
}
