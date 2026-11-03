package com.giseo.flowcoach.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

/** 리포트 감점: 화면에 보이는 항목별 감점과 점수가 같은 식에서 나온다. */
public class TripReportTest {

    @Test
    public void 감점은_큰_것부터_점수와_맞는다() {
        // 평가 450초 중 느림 30초 · 초과 20초, 추월 후 미복귀 1 · 잦은 변경 1 · 급감속 2
        List<TripReport.Deduction> d = TripReport.deductions(400, 30, 20, 1, 1, 2);
        assertEquals(TripReport.Item.PASSING, d.get(0).item);
        assertEquals(10, d.get(0).points, 1e-9);
        assertEquals(TripReport.Item.FREQUENT_LANE, d.get(1).item); // 8점끼리는 원래 순서
        assertEquals(TripReport.Item.HARD_BRAKE, d.get(2).item);
        assertEquals(TripReport.Item.TOO_SLOW, d.get(3).item);      // 30 × 30/450 = 2
        assertEquals(2.0, d.get(3).points, 1e-9);
        assertEquals(TripReport.Item.OVER_LIMIT, d.get(4).item);    // 40 × 20/450 ≈ 1.78
        assertEquals(70, TripReport.score(d));
        assertEquals("조금 더 부드럽게", TripReport.grade(70));
    }

    @Test
    public void 감점이_없으면_100점() {
        List<TripReport.Deduction> d = TripReport.deductions(300, 0, 0, 0, 0, 0);
        assertEquals(0, d.get(0).points, 1e-9);
        assertEquals(100, TripReport.score(d));
        assertEquals("흐름 마스터", TripReport.grade(100));
    }
}
