package com.giseo.flowcoach.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.giseo.flowcoach.data.demo.DemoScenario;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CoachEngineTest {

    private List<CoachMessage> runDemo(CoachMode mode) {
        CoachEngine engine = new CoachEngine(CoachConfig.demo(), mode);
        List<CoachMessage> spoken = new ArrayList<>();
        for (DemoScenario.Frame f : DemoScenario.highway(0)) {
            TickResult r = engine.tick(f.snapshot, f.road);
            if (r.message != null) spoken.add(r.message);
        }
        return spoken;
    }

    private static List<CoachEvent.Type> types(List<CoachMessage> messages) {
        List<CoachEvent.Type> list = new ArrayList<>();
        for (CoachMessage m : messages) list.add(m.type);
        return list;
    }

    @Test
    public void 시연_시나리오_코치모드_안내_순서() {
        List<CoachMessage> spoken = runDemo(CoachMode.COACH);
        assertEquals(Arrays.asList(
                CoachEvent.Type.SMOOTH_PRAISE,
                CoachEvent.Type.PASSING_LANE_HOLD,
                CoachEvent.Type.TOO_SLOW,
                CoachEvent.Type.REPEATED_HARD_BRAKE,
                CoachEvent.Type.FREQUENT_LANE_CHANGE,
                CoachEvent.Type.OVER_LIMIT
        ), types(spoken));
    }

    @Test
    public void 조용한_모드는_위험_패턴만_안내() {
        List<CoachMessage> spoken = runDemo(CoachMode.QUIET);
        assertEquals(Arrays.asList(
                CoachEvent.Type.PASSING_LANE_HOLD,
                CoachEvent.Type.REPEATED_HARD_BRAKE,
                CoachEvent.Type.FREQUENT_LANE_CHANGE,
                CoachEvent.Type.OVER_LIMIT
        ), types(spoken));
    }

    @Test
    public void 정체_구간에서는_느리다는_안내를_하지_않음() {
        CoachEngine engine = new CoachEngine(CoachConfig.demo(), CoachMode.COACH);
        RoadContext jam = new RoadContext(RoadType.EXPRESSWAY, 100, 25);
        for (int i = 0; i < 60; i++) {
            TickResult r = engine.tick(new DrivingSnapshot(i * 1000L, 22, 90, TurnSignal.OFF, false, 0), jam);
            assertNull(r.message);
        }
    }

    @Test
    public void 안내_사이에는_최소_간격이_있다() {
        List<CoachMessage> spoken = runDemo(CoachMode.COACH);
        long gap = CoachConfig.demo().globalGapMs;
        for (int i = 1; i < spoken.size(); i++) {
            assertTrue(spoken.get(i).timeMs - spoken.get(i - 1).timeMs >= gap);
        }
    }

    @Test
    public void 시연_후_리포트() {
        CoachEngine engine = new CoachEngine(CoachConfig.demo(), CoachMode.COACH);
        for (DemoScenario.Frame f : DemoScenario.highway(0)) engine.tick(f.snapshot, f.road);
        TripReport report = engine.getReport();
        assertEquals(6, report.getLaneChanges());
        assertEquals(2, report.getHardBrakes());
        assertEquals(1, report.getPassingHolds());
        assertEquals(1, report.getFrequentLaneChanges());
        assertTrue(report.score() > 0 && report.score() < 100);
        System.out.println("score=" + report.score() + " (" + report.grade() + "), "
                + String.format("%.2f", report.getDistanceKm()) + "km, " + report.spokenSummary());
    }

    @Test
    public void 모든_안내에는_녹음_음성이_있다() {
        for (CoachMode mode : CoachMode.values()) {
            for (CoachMessage m : runDemo(mode)) {
                assertTrue("녹음 문장 없음: " + m.voiceKey,
                        com.giseo.flowcoach.voice.VoicePrompts.LINES.containsKey(m.voiceKey));
            }
        }
        for (CoachEvent.Type t : CoachEvent.Type.values()) {
            String key = t.name().toLowerCase(java.util.Locale.ROOT);
            boolean spoken = !key.startsWith("lane_change") && !key.equals("hard_brake");
            if (spoken) {
                assertTrue("녹음 문장 없음: " + key, com.giseo.flowcoach.voice.VoicePrompts.LINES.containsKey(key));
            }
        }
        for (String k : new String[]{"now_smooth", "now_too_slow", "now_over_limit", "now_congested",
                "now_unknown", "too_slow_passing", "demo_start", "console_start", "trip_end"}) {
            assertTrue(k, com.giseo.flowcoach.voice.VoicePrompts.LINES.containsKey(k));
        }
    }

    @Test
    public void 지금_어때_질문에_답한다() {
        CoachEngine engine = new CoachEngine(CoachConfig.demo(), CoachMode.COACH);
        RoadContext highway = new RoadContext(RoadType.EXPRESSWAY, 100, 95);
        engine.tick(new DrivingSnapshot(0, 93, 90, TurnSignal.OFF, false, 0), highway);
        assertEquals("지금 시속 93킬로로, 흐름에 잘 맞춰 달리고 있어요.", engine.describeNow());
    }
}
