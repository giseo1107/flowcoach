package com.giseo.flowcoach.data.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

/**
 * Android 기본 GPS(LocationManager)로 위치를 받는다. NaviHelper가 응답하지 않을 때 대신 쓴다.
 * 에뮬레이터에서는 Extended Controls → Location에서 지정한 위치(또는 경로 재생)가 들어온다.
 */
public final class GpsLocationSource {

    private static final String TAG = "GpsLocation";
    /** bearing은 이 속도(m/s) 이상일 때만 믿는다. 정지 중 bearing은 무의미하다. */
    private static final float BEARING_MIN_SPEED_MPS = 2f;

    private final Context context;
    private final HeadingTracker headingTracker = new HeadingTracker();
    private LocationManager manager;
    private boolean running;

    private volatile GeoPosition latest;
    private volatile String status = "GPS 시작 전";

    private final LocationListener listener = new LocationListener() {
        @Override
        public void onLocationChanged(Location loc) {
            accept(loc);
        }

        // API 28~29 호환: 아래 메서드는 기본 구현이 없어서 비워 둔다
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
            status = "GPS 꺼짐";
        }
    };

    public GpsLocationSource(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start() {
        if (running) return;
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            status = "위치 권한 없음";
            return;
        }
        manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null || !manager.getAllProviders().contains(LocationManager.GPS_PROVIDER)) {
            status = "GPS 없음";
            return;
        }
        try {
            headingTracker.reset();
            manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000, 0f, listener, Looper.getMainLooper());
            Location last = manager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (last != null) accept(last);
            running = true;
            if (latest == null) status = "GPS 수신 대기";
        } catch (SecurityException | IllegalArgumentException e) {
            status = "GPS 시작 실패";
            Log.w(TAG, "GPS 시작 실패", e);
        }
    }

    public void stop() {
        if (!running) return;
        running = false;
        try {
            manager.removeUpdates(listener);
        } catch (RuntimeException e) {
            Log.w(TAG, "GPS 해제 실패", e);
        }
    }

    private void accept(Location loc) {
        double moved = headingTracker.update(loc.getLatitude(), loc.getLongitude());
        double heading = (loc.hasBearing() && loc.hasSpeed() && loc.getSpeed() >= BEARING_MIN_SPEED_MPS)
                ? loc.getBearing() : moved;
        // 측정 시각 기준(마지막으로 알려진 위치가 오래된 값일 수 있다)
        long fixAtMs = loc.getElapsedRealtimeNanos() > 0 ? loc.getElapsedRealtimeNanos() / 1_000_000 : SystemClock.elapsedRealtime();
        latest = new GeoPosition(loc.getLatitude(), loc.getLongitude(), heading, "GPS", fixAtMs);
        status = "GPS 수신";
    }

    public GeoPosition latest() {
        return latest;
    }

    public String status() {
        return status;
    }
}
