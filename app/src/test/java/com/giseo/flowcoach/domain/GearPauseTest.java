package com.giseo.flowcoach.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** P·R·N에서는 코칭(판단·음성·리포트)을 쉰다. */
public class GearPauseTest {

    private static final RoadContext HIGHWAY = new RoadContext(RoadType.EXPRESSWAY, 100, 84);

    private static DrivingSnapshot at(long sec, double kmh, Gear gear) {
        return at(sec, kmh, gear, TurnSignal.OFF);
    }

    private static DrivingSnapshot at(long sec, double kmh, Gear gear, TurnSignal signal) {
        return new DrivingSnapshot(sec * 1000L, kmh, 90, signal, false, DrivingSnapshot.PEDAL_UNKNOWN, gear);
    }

    private static CoachEngine engine() {
        return new CoachEngine(CoachConfig.demo(), CoachMode.COACH);
    }

    @Test
    public void AAOS_기어값_변환() {
        assertEquals(Gear.PARK, Gear.fromAaos(4));
        assertEquals(Gear.REVERSE, Gear.fromAaos(2));
        assertEquals(Gear.NEUTRAL, Gear.fromAaos(1));
        assertEquals(Gear.DRIVE, Gear.fromAaos(8));
        assertEquals(Gear.DRIVE, Gear.fromAaos(16));   // 수동 1단
        assertEquals(Gear.UNKNOWN, Gear.fromAaos(0));
        assertTrue(Gear.UNKNOWN.allowsCoaching());     // 기어 신호가 없으면 예전처럼 코칭
        assertFalse(Gear.REVERSE.allowsCoaching());
    }

    @Test
    public void 후진_중엔_흐름보다_느려도_안내하지_않음() {
        CoachEngine e = engine();
        for (long t = 0; t < 60; t++) {
            TickResult r = e.tick(at(t, 25, Gear.REVERSE), HIGHWAY); // D였다면 '흐름보다 느림'
            assertNull(r.message);
            assertEquals(FlowState.UNKNOWN, r.flow.state);
            assertEquals(100, r.flow.speedLimitKmh);                 // 게이지용 값은 그대로
        }
        assertTrue(e.isPaused());

        // D로 바꾸면 그때부터 다시 판단: '느림'이 8초 이어진 뒤에 안내
        Long firstSlow = null;
        for (long t = 60; t < 90; t++) {
            TickResult r = e.tick(at(t, 60, Gear.DRIVE), HIGHWAY);
            if (r.message != null && r.message.type == CoachEvent.Type.TOO_SLOW && firstSlow == null) firstSlow = t;
        }
        assertFalse(e.isPaused());
        assertNotNull(firstSlow);
        assertTrue("재개 후 지속 시간부터 다시 셈: " + firstSlow, firstSlow >= 60 + 8);
    }

    @Test
    public void 중립_주차_중_감속은_급제동으로_세지_않음() {
        CoachEngine e = engine();
        List<CoachMessage> spoken = new ArrayList<>();
        long t = 0;
        for (int i = 0; i < 4; i++) { // 60 → 40 km/h를 1초 만에(D였다면 급제동)
            spoken.add(e.tick(at(t++, 60, Gear.NEUTRAL), HIGHWAY).message);
            spoken.add(e.tick(at(t++, 40, Gear.NEUTRAL), HIGHWAY).message);
        }
        // 다시 D: 쉬기 전 속도(40)와 비교해 급감속으로 착각하지 않아야 한다
        e.tick(at(t++, 35, Gear.DRIVE), HIGHWAY);
        assertEquals(0, e.getReport().getHardBrakes());
        for (CoachMessage m : spoken) assertNull(m);
    }

    @Test
    public void 정차했다_다시_출발하면_추월_상태를_지움() {
        CoachEngine e = engine();
        // D, 시속 100으로 왼쪽 차로 변경 → 추월 중
        e.tick(at(0, 100, Gear.DRIVE, TurnSignal.LEFT), HIGHWAY);
        e.tick(at(2, 100, Gear.DRIVE, TurnSignal.LEFT), HIGHWAY);
        TickResult r = e.tick(at(3, 100, Gear.DRIVE), HIGHWAY);
        assertTrue(r.passing);

        for (long t = 4; t < 120; t++) e.tick(at(t, 0, Gear.PARK), HIGHWAY); // 휴게소 정차

        for (long t = 120; t < 200; t++) {
            r = e.tick(at(t, 84, Gear.DRIVE), HIGHWAY);
            assertFalse(r.passing);
            if (r.message != null) assertTrue(r.message.type != CoachEvent.Type.PASSING_LANE_HOLD);
        }
    }

    @Test
    public void 후진_구간은_리포트에_넣지_않음() {
        CoachEngine e = engine();
        for (long t = 0; t < 60; t++) e.tick(at(t, 20, Gear.REVERSE), HIGHWAY);
        assertEquals(0, e.getReport().durationSec());
        assertEquals(0, e.getReport().getDistanceKm(), 1e-9);

        for (long t = 60; t <= 96; t++) e.tick(at(t, 100, Gear.DRIVE), HIGHWAY); // 36초 × 100km/h = 1km
        assertEquals(1.0, e.getReport().getDistanceKm(), 0.01);

        for (long t = 97; t < 200; t++) e.tick(at(t, 10, Gear.REVERSE), HIGHWAY); // 다시 후진
        e.tick(at(200, 100, Gear.DRIVE), HIGHWAY);                                  // 재개 순간엔 거리 0
        assertEquals(1.0, e.getReport().getDistanceKm(), 0.01);
        assertTrue(e.getReport().durationSec() > 0); // 주행 종료 시 리포트는 나온다
    }
}
