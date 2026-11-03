package com.giseo.flowcoach.domain;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.giseo.flowcoach.data.demo.DemoScenario;
import com.giseo.flowcoach.data.demo.DemoScene;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** 장면 버튼 하나를 누르면 그 장면이 보여주려는 안내가 실제로 나오는지. */
public class DemoSceneTest {

    private static List<CoachMessage> play(DemoScene scene, List<DemoScenario.Frame> frames) {
        CoachEngine engine = new CoachEngine(CoachConfig.demo(), CoachMode.COACH);
        List<CoachMessage> spoken = new ArrayList<>();
        for (DemoScenario.Frame f : frames) {
            TickResult r = engine.tick(f.snapshot, f.road);
            if (r.message != null) spoken.add(r.message);
        }
        return spoken;
    }

    @Test
    public void 모든_장면에서_기대한_안내가_나온다() {
        for (DemoScene scene : DemoScene.values()) {
            List<DemoScenario.Frame> frames = DemoScenario.scene(scene, 0);
            assertTrue(scene + " 길이", frames.size() >= 15 && frames.size() <= 40);
            boolean found = false;
            for (CoachMessage m : play(scene, frames)) if (m.type == scene.expectedEvent) found = true;
            assertTrue(scene + " 장면에서 " + scene.expectedEvent + " 안내가 나와야 함", found);
        }
    }

    @Test
    public void 느림_장면의_정체_구간에서는_안내하지_않는다() {
        CoachEngine engine = new CoachEngine(CoachConfig.demo(), CoachMode.COACH);
        List<DemoScenario.Frame> frames = DemoScenario.scene(DemoScene.TOO_SLOW, 0);
        for (DemoScenario.Frame f : frames) {
            TickResult r = engine.tick(f.snapshot, f.road);
            if (f.snapshot.timeMs >= 24_000) { // 정체가 자리 잡은 뒤
                assertTrue(r.flow.state == FlowState.CONGESTED);
                assertNull(r.message);
            }
        }
    }

    @Test
    public void 장면은_정해진_시간_안에_반응한다() {
        // 버튼을 누르고 너무 오래 기다리지 않게: 기대한 안내가 30초 안에 나와야 한다
        for (DemoScene scene : DemoScene.values()) {
            CoachEngine engine = new CoachEngine(CoachConfig.demo(), CoachMode.COACH);
            long at = -1;
            for (DemoScenario.Frame f : DemoScenario.scene(scene, 0)) {
                TickResult r = engine.tick(f.snapshot, f.road);
                if (at < 0 && r.message != null && r.message.type == scene.expectedEvent) at = f.snapshot.timeMs;
            }
            assertTrue(scene + " 반응 시각 " + at, at >= 0 && at <= 30_000);
        }
    }
}
