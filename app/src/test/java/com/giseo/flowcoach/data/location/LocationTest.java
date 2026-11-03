package com.giseo.flowcoach.data.location;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LocationTest {

    @Test
    public void 거리와_방향() {
        double d = GeoMath.distanceMeters(37.400, 127.100, 37.5665, 126.978); // 판교 → 서울시청
        assertTrue("약 21km: " + d, d > 20_000 && d < 23_000);
        assertEquals(0, GeoMath.bearingDeg(37, 127, 38, 127), 0.01);
        assertEquals(90, GeoMath.bearingDeg(37, 127, 37, 127.01), 0.1);
        assertEquals(20, GeoMath.angleDiff(350, 10), 1e-9);
        assertEquals(180, GeoMath.angleDiff(0, 180), 1e-9);
    }

    @Test
    public void 움직인_거리가_짧으면_방향을_정하지_않음() {
        HeadingTracker t = new HeadingTracker(30);
        assertTrue(Double.isNaN(t.update(37.0, 127.0)));
        assertTrue(Double.isNaN(t.update(37.0001, 127.0))); // 약 11m
        assertEquals(0, t.update(37.0005, 127.0), 1.0);      // 약 55m 북쪽
        assertEquals(180, t.update(37.0, 127.0), 1.0);       // 다시 남쪽
    }

    @Test
    public void 내비_위치를_먼저_쓰고_끊기면_GPS() {
        long now = 100_000;
        GeoPosition navi = new GeoPosition(37.40, 127.10, 0, "내비", now - 2_000);
        GeoPosition gps = new GeoPosition(37.41, 127.11, Double.NaN, "GPS", now - 60_000);
        assertSame(navi, PositionProvider.pick(navi, gps, now));
        GeoPosition oldNavi = new GeoPosition(37.40, 127.10, 0, "내비", now - 30_000);
        assertSame(gps, PositionProvider.pick(oldNavi, gps, now));
        assertNull(PositionProvider.pick(null, null, now));
    }

    @Test
    public void 국내가_아닌_위치는_쓰지_않음() {
        long now = 100_000;
        GeoPosition googleplex = new GeoPosition(37.422, -122.084, Double.NaN, "GPS", now); // 에뮬레이터 기본 위치
        assertFalse(googleplex.isInKorea());
        assertNull(PositionProvider.pick(null, googleplex, now));
        assertTrue(new GeoPosition(33.5, 126.5, Double.NaN, "GPS", now).isInKorea()); // 제주
    }
}
