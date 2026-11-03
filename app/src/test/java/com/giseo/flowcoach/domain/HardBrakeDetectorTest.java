package com.giseo.flowcoach.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class HardBrakeDetectorTest {

    private final CoachConfig config = CoachConfig.defaults();
    private final HardBrakeDetector detector = new HardBrakeDetector(config);
    private final List<CoachEvent> all = new ArrayList<>();

    private void at(long sec, double speed, int pedal) {
        all.addAll(detector.update(new DrivingSnapshot(sec * 1000, speed, 90, TurnSignal.OFF, pedal > 0, pedal)));
    }

    private int count(CoachEvent.Type type) {
        int n = 0;
        for (CoachEvent e : all) if (e.type == type) n++;
        return n;
    }

    @Test
    public void 초당_10킬로_이상_줄면_급감속() {
        at(0, 100, 0);
        at(1, 88, 0);
        assertEquals(1, count(CoachEvent.Type.HARD_BRAKE));
    }

    @Test
    public void 완만한_감속은_급감속이_아님() {
        at(0, 100, 0);
        at(1, 95, 0);
        at(2, 90, 0);
        assertEquals(0, count(CoachEvent.Type.HARD_BRAKE));
    }

    @Test
    public void 연속된_감속은_한번으로_센다() {
        at(0, 100, 0);
        at(1, 85, 0);
        at(2, 70, 0);
        assertEquals(1, count(CoachEvent.Type.HARD_BRAKE));
    }

    @Test
    public void 브레이크를_길게_밟아도_한번으로_센다() {
        double v = 100;
        for (int t = 0; t <= 6; t++) {
            at(t, v, t > 0 ? 80 : 0);
            v -= 12; // 6초 동안 계속 급감속
        }
        assertEquals(1, count(CoachEvent.Type.HARD_BRAKE));
        assertEquals(0, count(CoachEvent.Type.REPEATED_HARD_BRAKE));
    }

    @Test
    public void 페달을_깊게_밟는_순간도_급제동() {
        at(0, 90, 0);
        at(1, 90, 80);
        at(2, 89, 80); // 계속 밟고 있는 건 다시 세지 않음
        assertEquals(1, count(CoachEvent.Type.HARD_BRAKE));
    }

    @Test
    public void 저속에서는_급감속으로_보지_않음() {
        at(0, 25, 0);
        at(1, 5, 0);
        assertEquals(0, count(CoachEvent.Type.HARD_BRAKE));
    }

    @Test
    public void 급감속이_반복되면_간격_조언() {
        for (int i = 0; i < 3; i++) {
            long base = i * 20L;
            at(base, 100, 0);
            at(base + 1, 85, 0);
        }
        assertEquals(3, count(CoachEvent.Type.HARD_BRAKE));
        assertEquals(1, count(CoachEvent.Type.REPEATED_HARD_BRAKE));
    }
}
