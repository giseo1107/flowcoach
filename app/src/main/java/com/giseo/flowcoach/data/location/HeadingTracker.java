package com.giseo.flowcoach.data.location;

/**
 * 위치 기록으로 진행 방향을 계산한다.
 * 위치 출처가 방향을 주지 않거나(정지, GPS bearing 없음) 0으로만 줄 때 쓴다.
 * GPS 오차로 방향이 튀지 않도록 일정 거리 이상 움직였을 때만 갱신한다.
 */
public final class HeadingTracker {

    /** 이만큼 움직여야 방향을 새로 계산한다(m). */
    private final double minMoveMeters;

    private double anchorLat = Double.NaN;
    private double anchorLon = Double.NaN;
    private double heading = Double.NaN;

    public HeadingTracker() {
        this(30.0);
    }

    public HeadingTracker(double minMoveMeters) {
        this.minMoveMeters = minMoveMeters;
    }

    /** 새 위치를 넣고, 지금까지 계산된 진행 방향(모르면 NaN)을 돌려준다. */
    public double update(double lat, double lon) {
        if (Double.isNaN(anchorLat)) {
            anchorLat = lat;
            anchorLon = lon;
            return heading;
        }
        if (GeoMath.distanceMeters(anchorLat, anchorLon, lat, lon) >= minMoveMeters) {
            heading = GeoMath.bearingDeg(anchorLat, anchorLon, lat, lon);
            anchorLat = lat;
            anchorLon = lon;
        }
        return heading;
    }

    public double heading() {
        return heading;
    }

    public void reset() {
        anchorLat = Double.NaN;
        anchorLon = Double.NaN;
        heading = Double.NaN;
    }
}
