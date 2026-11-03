package com.giseo.flowcoach.data.route;

import com.giseo.flowcoach.data.location.GeoMath;

import java.util.List;

/** 실제 도로 좌표(위도·경도) 선. 출발점에서 몇 미터 지점의 위치와 진행 방향을 구한다. */
public final class Polyline {

    private final double[] lat;
    private final double[] lon;
    /** 각 점까지의 누적 거리(m). */
    private final double[] cum;

    public Polyline(List<double[]> points) {
        if (points.size() < 2) throw new IllegalArgumentException("점이 2개 이상 필요해요");
        int n = points.size();
        lat = new double[n];
        lon = new double[n];
        cum = new double[n];
        for (int i = 0; i < n; i++) {
            lat[i] = points.get(i)[0];
            lon[i] = points.get(i)[1];
            if (i > 0) cum[i] = cum[i - 1] + GeoMath.distanceMeters(lat[i - 1], lon[i - 1], lat[i], lon[i]);
        }
    }

    public double lengthMeters() {
        return cum[cum.length - 1];
    }

    /** 출발점에서 meters 지점: {위도, 경도, 진행 방향(도)}. 범위를 넘으면 양 끝으로 맞춘다. */
    public double[] at(double meters) {
        double m = Math.max(0, Math.min(meters, lengthMeters()));
        int i = 1;
        while (i < cum.length - 1 && cum[i] < m) i++;
        double segment = cum[i] - cum[i - 1];
        double t = segment <= 0 ? 0 : (m - cum[i - 1]) / segment;
        double la = lat[i - 1] + (lat[i] - lat[i - 1]) * t;
        double lo = lon[i - 1] + (lon[i] - lon[i - 1]) * t;
        double bearing = GeoMath.bearingDeg(lat[i - 1], lon[i - 1], lat[i], lon[i]);
        return new double[]{la, lo, bearing};
    }
}
