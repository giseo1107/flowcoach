package com.giseo.flowcoach.data.location;

import android.content.Context;
import android.util.Log;

import java.util.Locale;

/**
 * 현재 위치를 고른다: Pleos 내비(NaviHelper) → Android GPS 순서.
 * 둘 다 없거나 국내 좌표가 아니면 null → 호출부가 시연 위치(고정 도로)를 쓴다.
 */
public final class PositionProvider {

    private static final String TAG = "PositionProvider";
    /** 내비 위치는 3초마다 요청하므로 이보다 오래되면 끊긴 것으로 본다. */
    static final long NAVI_FRESH_MS = 15_000;
    /** GPS는 정지 중(에뮬레이터 고정 위치 포함)에는 새 값이 안 올 수 있어 여유를 둔다. */
    static final long GPS_FRESH_MS = 10 * 60_000;

    private final NaviLocationSource navi; // SDK가 없으면 null
    private final GpsLocationSource gps;

    public PositionProvider(Context context) {
        NaviLocationSource n = null;
        try {
            n = new NaviLocationSource(context);
        } catch (Throwable t) {
            Log.w(TAG, "NaviHelper 사용 불가", t);
        }
        this.navi = n;
        this.gps = new GpsLocationSource(context);
    }

    public void start() {
        if (navi != null) navi.start();
        gps.start();
    }

    public void stop() {
        if (navi != null) navi.stop();
        gps.stop();
    }

    /** 지금 쓸 위치. 없으면 null. */
    public GeoPosition current(long nowMs) {
        return pick(navi != null ? navi.latest() : null, gps.latest(), nowMs);
    }

    /** 내비 → GPS 순서로, 새롭고 국내인 위치를 고른다. 테스트를 위해 분리. */
    static GeoPosition pick(GeoPosition fromNavi, GeoPosition fromGps, long nowMs) {
        if (usable(fromNavi, nowMs, NAVI_FRESH_MS)) return fromNavi;
        if (usable(fromGps, nowMs, GPS_FRESH_MS)) return fromGps;
        return null;
    }

    private static boolean usable(GeoPosition p, long nowMs, long freshMs) {
        return p != null && nowMs - p.timeMs <= freshMs && p.isInKorea();
    }

    /** 내비가 알려준 현재 도로 제한속도(km/h). 없으면 0. */
    public int speedLimitKmh(long nowMs) {
        return navi != null ? navi.speedLimitKmh(nowMs) : 0;
    }

    /** 화면 상태 줄용. */
    public String status(long nowMs) {
        GeoPosition p = current(nowMs);
        if (p != null) {
            String where = String.format(Locale.ROOT, "%.4f,%.4f", p.lat, p.lon);
            String heading = p.hasHeading() ? String.format(Locale.ROOT, " %.0f°", p.headingDeg) : "";
            int limit = speedLimitKmh(nowMs);
            return "위치: " + p.source + " " + where + heading + (limit > 0 ? " · 내비 제한 " + limit : "");
        }
        GeoPosition any = navi != null && navi.latest() != null ? navi.latest() : gps.latest();
        String why = any != null && !any.isInKorea()
                ? String.format(Locale.ROOT, "국내 좌표 아님 %.2f,%.2f", any.lat, any.lon)
                : (navi != null ? navi.status() : "내비 없음") + ", " + gps.status();
        return "위치: 없음(" + why + ") → 시연 위치";
    }
}
