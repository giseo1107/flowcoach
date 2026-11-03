package com.giseo.flowcoach.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 알림 세분화: 화면이 보일 때·안 보일 때 알리는 방법. */
public class AlertPolicyTest {

    @Test
    public void 안내_종류_나누기() {
        assertEquals(AlertPolicy.Category.DANGER, AlertPolicy.categoryOf(CoachEvent.Type.OVER_LIMIT));
        assertEquals(AlertPolicy.Category.DANGER, AlertPolicy.categoryOf(CoachEvent.Type.REPEATED_HARD_BRAKE));
        assertEquals(AlertPolicy.Category.HABIT, AlertPolicy.categoryOf(CoachEvent.Type.PASSING_LANE_HOLD));
        assertEquals(AlertPolicy.Category.HABIT, AlertPolicy.categoryOf(CoachEvent.Type.FREQUENT_LANE_CHANGE));
        assertEquals(AlertPolicy.Category.HABIT, AlertPolicy.categoryOf(CoachEvent.Type.TOO_SLOW));
        assertEquals(AlertPolicy.Category.PRAISE, AlertPolicy.categoryOf(CoachEvent.Type.SMOOTH_PRAISE));
    }

    @Test
    public void 위험_경고는_화면이_안_보일_때만_팝업() {
        AlertPolicy.Delivery hidden = AlertPolicy.delivery(CoachEvent.Type.OVER_LIMIT, false);
        assertTrue(hidden.voice);
        assertTrue(hidden.popup);
        assertFalse(hidden.highlight);

        AlertPolicy.Delivery visible = AlertPolicy.delivery(CoachEvent.Type.OVER_LIMIT, true);
        assertTrue(visible.voice);
        assertFalse(visible.popup);   // 화면이 보이면 팝업으로 가리지 않고
        assertTrue(visible.highlight); // 패널을 강조
    }

    @Test
    public void 습관_코칭과_칭찬은_팝업_없이_음성만() {
        for (CoachEvent.Type t : new CoachEvent.Type[]{CoachEvent.Type.PASSING_LANE_HOLD,
                CoachEvent.Type.FREQUENT_LANE_CHANGE, CoachEvent.Type.TOO_SLOW, CoachEvent.Type.SMOOTH_PRAISE}) {
            for (boolean visible : new boolean[]{true, false}) {
                AlertPolicy.Delivery d = AlertPolicy.delivery(t, visible);
                assertTrue(d.voice);
                assertFalse(t + " 팝업", d.popup);
                assertFalse(t + " 강조", d.highlight);
            }
        }
    }

    @Test
    public void 조용한_모드는_말하지_않고_위험만_경고음() {
        for (boolean visible : new boolean[]{true, false}) {
            AlertPolicy.Delivery danger = AlertPolicy.delivery(CoachEvent.Type.OVER_LIMIT, visible, CoachMode.QUIET);
            assertFalse(danger.voice);
            assertTrue(danger.chime);
            assertEquals(!visible, danger.popup);   // 화면이 안 보이면 팝업
            assertEquals(visible, danger.highlight); // 보이면 강조

            AlertPolicy.Delivery habit = AlertPolicy.delivery(CoachEvent.Type.PASSING_LANE_HOLD, visible, CoachMode.QUIET);
            assertFalse(habit.voice);
            assertFalse(habit.chime); // 습관은 소리 없이 화면 문장과 리포트로
            assertFalse(habit.popup);
        }
    }

    @Test
    public void 코치_모드는_경고음_없이_말로() {
        AlertPolicy.Delivery d = AlertPolicy.delivery(CoachEvent.Type.REPEATED_HARD_BRAKE, true, CoachMode.COACH);
        assertTrue(d.voice);
        assertFalse(d.chime);
    }
}
