package com.giseo.flowcoach.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LaneHabitDetectorTest {

    private CoachConfig config;
    private LaneHabitDetector detector;
    private final RoadContext highway = new RoadContext(RoadType.EXPRESSWAY, 100, 95);
    private final List<CoachEvent> all = new ArrayList<>();
    private long t;

    @Before
    public void setUp() {
        config = CoachConfig.defaults();
        config.passingHoldMs = 30_000;
        detector = new LaneHabitDetector(config);
        all.clear();
        t = 0;
    }

    /** 1초씩 seconds만큼 같은 상태로 주행. */
    private void drive(int seconds, double speed, double heading, TurnSignal signal, RoadContext road) {
        for (int i = 0; i < seconds; i++) {
            all.addAll(detector.update(new DrivingSnapshot(t, speed, heading, signal, false, 0), road));
            t += 1000;
        }
    }

    private int count(CoachEvent.Type type) {
        int n = 0;
        for (CoachEvent e : all) if (e.type == type) n++;
        return n;
    }

    @Test
    public void 방향지시등을_2초_켰다_끄면_차로_변경_1회() {
        drive(3, 95, 90, TurnSignal.OFF, highway);
        drive(2, 95, 90, TurnSignal.LEFT, highway);
        drive(1, 95, 90, TurnSignal.OFF, highway);
        assertEquals(1, count(CoachEvent.Type.LANE_CHANGE_LEFT));
        assertEquals(1, detector.getLaneOffset());
        assertTrue(detector.isPassing());
    }

    @Test
    public void 방향지시등을_툭_건드린_것은_무시() {
        config.minSignalMs = 1500;
        drive(1, 95, 90, TurnSignal.LEFT, highway);
        drive(1, 95, 90, TurnSignal.OFF, highway);
        assertEquals(0, count(CoachEvent.Type.LANE_CHANGE_LEFT));
    }

    @Test
    public void 진행방향이_크게_바뀌면_회전이라_차로_변경이_아님() {
        drive(3, 40, 0, TurnSignal.RIGHT, highway);
        drive(1, 40, 90, TurnSignal.OFF, highway); // 90도 회전
        assertEquals(0, count(CoachEvent.Type.LANE_CHANGE_RIGHT));
    }

    @Test
    public void 추월_후_복귀하지_않으면_경고() {
        drive(2, 95, 90, TurnSignal.LEFT, highway);
        drive(35, 95, 90, TurnSignal.OFF, highway);
        assertEquals(1, count(CoachEvent.Type.PASSING_LANE_HOLD));
    }

    @Test
    public void 추월_후_제때_복귀하면_경고_없음() {
        drive(2, 95, 90, TurnSignal.LEFT, highway);
        drive(10, 98, 90, TurnSignal.OFF, highway);
        drive(2, 95, 90, TurnSignal.RIGHT, highway);
        drive(40, 95, 90, TurnSignal.OFF, highway);
        assertEquals(0, count(CoachEvent.Type.PASSING_LANE_HOLD));
        assertFalse(detector.isPassing());
    }

    @Test
    public void 흐름이_80킬로_미만인_정체에서는_추월차로_경고_없음_법규정() {
        RoadContext slowFlow = new RoadContext(RoadType.EXPRESSWAY, 100, 60);
        drive(2, 60, 90, TurnSignal.LEFT, slowFlow);
        drive(40, 60, 90, TurnSignal.OFF, slowFlow);
        assertEquals(0, count(CoachEvent.Type.PASSING_LANE_HOLD));
    }

    @Test
    public void 도심도로에서는_추월차로_경고_없음() {
        RoadContext urban = new RoadContext(RoadType.URBAN, 50, 45);
        drive(2, 45, 90, TurnSignal.LEFT, urban);
        drive(40, 45, 90, TurnSignal.OFF, urban);
        assertEquals(0, count(CoachEvent.Type.PASSING_LANE_HOLD));
    }

    @Test
    public void 일분_안에_세번_바꾸면_잦은_차로_변경() {
        for (int i = 0; i < 3; i++) {
            drive(2, 95, 90, i % 2 == 0 ? TurnSignal.LEFT : TurnSignal.RIGHT, highway);
            drive(3, 95, 90, TurnSignal.OFF, highway);
        }
        assertEquals(1, count(CoachEvent.Type.FREQUENT_LANE_CHANGE));
    }

    @Test
    public void 비상등은_차로_변경으로_세지_않음() {
        drive(2, 95, 90, TurnSignal.LEFT, highway);
        drive(3, 95, 90, TurnSignal.HAZARD, highway);
        drive(1, 95, 90, TurnSignal.OFF, highway);
        assertEquals(0, count(CoachEvent.Type.LANE_CHANGE_LEFT));
    }

    @Test
    public void 각도_차이_계산() {
        assertEquals(20, LaneHabitDetector.angleDiff(350, 10), 0.001);
        assertEquals(90, LaneHabitDetector.angleDiff(0, 90), 0.001);
        assertEquals(180, LaneHabitDetector.angleDiff(0, 180), 0.001);
    }
}
