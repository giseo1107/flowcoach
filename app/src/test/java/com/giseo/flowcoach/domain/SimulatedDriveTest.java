package com.giseo.flowcoach.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.giseo.flowcoach.data.demo.ConsoleRoad;
import com.giseo.flowcoach.data.demo.SimulatedDrive;

import org.junit.Test;

public class SimulatedDriveTest {

    @Test
    public void 크루즈_목표_속도를_향해_가속() {
        SimulatedDrive d = new SimulatedDrive(70, 85);
        d.step(0, false, 80);
        assertEquals(74, d.step(1000, false, 80), 0.001);
        assertEquals(78, d.step(2000, false, 80), 0.001);
        assertEquals(80, d.step(3000, false, 80), 0.001); // 목표에서 멈춤
    }

    @Test
    public void 브레이크를_밟으면_급감속으로_감지된다() {
        CoachConfig config = CoachConfig.demo();
        HardBrakeDetector detector = new HardBrakeDetector(config);
        SimulatedDrive d = new SimulatedDrive(90, 90);
        int hard = 0;
        for (int t = 0; t <= 3; t++) {
            boolean braking = t >= 2;
            double v = d.step(t * 1000L, braking, Double.NaN);
            DrivingSnapshot s = new DrivingSnapshot(t * 1000L, v, 90, TurnSignal.OFF, braking,
                    DrivingSnapshot.PEDAL_UNKNOWN);
            for (CoachEvent e : detector.update(s)) if (e.type == CoachEvent.Type.HARD_BRAKE) hard++;
        }
        assertEquals(1, hard);
    }

    @Test
    public void 주차하면_0이고_D에서_0부터_다시_가속() {
        SimulatedDrive d = new SimulatedDrive(70, 85);
        d.step(0, false, 50);
        assertEquals(0, d.stop(1_000), 1e-9);              // P·R
        assertEquals(0, d.stop(30_000), 1e-9);
        assertEquals(4, d.step(31_000, false, 50), 1e-9);  // D: 정지 시간과 상관없이 1초에 4km/h씩
    }

    @Test
    public void 콘솔_주행_도로에서_느림_원활_과속이_모두_재현된다() {
        FlowAnalyzer analyzer = new FlowAnalyzer(CoachConfig.demo());
        RoadContext road = ConsoleRoad.ROAD;
        // 크루즈 목표 속도 범위(약 20~100km/h) 안에서
        assertEquals(FlowState.TOO_SLOW, analyzer.analyze(60, road).state);
        assertEquals(FlowState.SMOOTH, analyzer.analyze(85, road).state);
        assertEquals(FlowState.OVER_LIMIT, analyzer.analyze(100, road).state);
        assertTrue(road.flowSpeedKmh >= CoachConfig.demo().passingRuleMinFlowKmh); // 추월차로 경고 가능
    }
}
