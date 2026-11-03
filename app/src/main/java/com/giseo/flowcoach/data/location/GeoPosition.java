package com.giseo.flowcoach.data.location;

import java.util.Locale;

/** 차량의 현재 위치 한 점. */
public final class GeoPosition {

    public final double lat;
    public final double lon;
    /** 진행 방향(도, 북=0, 시계 방향). 모르면 Double.NaN. */
    public final double headingDeg;
    /** 어디서 받은 위치인지(예: "내비", "GPS"). 화면 표시용. */
    public final String source;
    /** 받은 시각(SystemClock.elapsedRealtime 기준 ms). */
    public final long timeMs;

    public GeoPosition(double lat, double lon, double headingDeg, String source, long timeMs) {
        this.lat = lat;
        this.lon = lon;
        this.headingDeg = headingDeg;
        this.source = source == null ? "" : source;
        this.timeMs = timeMs;
    }

    public boolean hasHeading() {
        return !Double.isNaN(headingDeg);
    }

    /** 대한민국 범위 안인지. 에뮬레이터 기본 위치(미국)처럼 국내가 아니면 교통정보를 조회하지 않는다. */
    public boolean isInKorea() {
        return lat >= 33.0 && lat <= 38.7 && lon >= 124.5 && lon <= 131.0;
    }

    public GeoPosition withHeading(double heading) {
        return new GeoPosition(lat, lon, heading, source, timeMs);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s(%.4f,%.4f%s)", source, lat, lon,
                hasHeading() ? String.format(Locale.ROOT, " %.0f°", headingDeg) : "");
    }
}
