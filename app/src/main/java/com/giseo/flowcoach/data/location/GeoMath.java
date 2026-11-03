package com.giseo.flowcoach.data.location;

/** 위경도 계산(짧은 거리용, 구면 근사). */
public final class GeoMath {

    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private GeoMath() {
    }

    /** 두 점 사이 거리(m). */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = p2 - p1;
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1, Math.sqrt(a)));
    }

    /** 1에서 2로 가는 방향(도, 북=0, 시계 방향, 0~360). */
    public static double bearingDeg(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dl = Math.toRadians(lon2 - lon1);
        double y = Math.sin(dl) * Math.cos(p2);
        double x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl);
        return normalize(Math.toDegrees(Math.atan2(y, x)));
    }

    /** 두 방향의 차이(0~180도). */
    public static double angleDiff(double a, double b) {
        double d = Math.abs(normalize(a) - normalize(b));
        return d > 180 ? 360 - d : d;
    }

    public static double normalize(double deg) {
        double d = deg % 360.0;
        return d < 0 ? d + 360.0 : d;
    }
}
