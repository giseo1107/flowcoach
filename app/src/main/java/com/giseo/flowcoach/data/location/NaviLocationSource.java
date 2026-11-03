package com.giseo.flowcoach.data.location;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import ai.pleos.playground.navi.constants.NaviErrorCode;
import ai.pleos.playground.navi.data.CurrentLocationInfo;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import kotlin.Unit;

/**
 * Pleos NaviHelper SDK로 현재 위치·진행 방향·현재 도로 제한속도를 받는다.
 *
 *  - 위치: getCurrentLocationInfo()를 몇 초마다 요청 → onCurrentLocationInfo(위도, 경도, heading, 주소)
 *  - 제한속도: 리스너를 등록하면 onCurrentRoadSpeedLimit(km/h)가 1초마다 들어온다
 *  - 권한: pleos.car.permission.NAVI_ROUTE (보호 수준 Normal → 매니페스트 선언만)
 *
 * 내비 서비스가 없거나 연결에 실패하면 latest()가 null이고, PositionProvider가 GPS로 대신한다.
 */
public final class NaviLocationSource {

    private static final String TAG = "NaviLocation";
    private static final long POLL_MS = 3_000;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final HeadingTracker headingTracker = new HeadingTracker();

    private NaviHelper navi;
    private boolean running;

    private volatile GeoPosition latest;
    private volatile String address = "";
    private volatile int speedLimitKmh;
    private volatile long speedLimitAtMs;
    private volatile String status = "내비 연결 전";

    private final NaviHelperEventListener listener = new NaviHelperEventListener() {
        @Override
        public void onCurrentLocationInfo(CurrentLocationInfo info) {
            double lat = info.getLatitude();
            double lon = info.getLongitude();
            double moved = headingTracker.update(lat, lon);
            // heading 기본값이 0.0이라 '북쪽'과 '모름'을 구분할 수 없다.
            // 실제로 움직여서 계산한 방향이 있으면 그쪽을 우선한다.
            double heading = info.getHeading();
            if (heading == 0.0 && !Double.isNaN(moved)) heading = moved;
            latest = new GeoPosition(lat, lon, heading, "내비", SystemClock.elapsedRealtime());
            address = info.getAddress() == null ? "" : info.getAddress();
            status = "내비 위치 수신";
        }

        @Override
        public void onCurrentRoadSpeedLimit(int speedLimit) {
            speedLimitKmh = speedLimit;
            speedLimitAtMs = SystemClock.elapsedRealtime();
        }

        @Override
        public void onError(NaviErrorCode errorCode) {
            status = "내비 오류: " + errorCode;
        }
    };

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            try {
                navi.getCurrentLocationInfo();
            } catch (Throwable t) { // 서비스 미연결·권한 문제 등
                status = "내비 위치 요청 실패: " + t.getClass().getSimpleName();
            }
            handler.postDelayed(this, POLL_MS);
        }
    };

    public NaviLocationSource(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start() {
        if (running) return;
        running = true;
        headingTracker.reset();
        try {
            navi = new NaviHelper(context);
            navi.initialize(() -> {
                status = "내비 연결됨";
                return Unit.INSTANCE;
            }, error -> {
                status = "내비 연결 실패: " + error.getMessage();
                Log.w(TAG, "NaviHelper 연결 실패", error);
                return Unit.INSTANCE;
            });
            navi.addListener(listener);
            handler.postDelayed(poll, 1_000);
        } catch (Throwable t) { // SDK가 없는 기기
            status = "내비 SDK 사용 불가";
            Log.w(TAG, "NaviHelper 시작 실패", t);
            navi = null;
            running = false;
        }
    }

    public void stop() {
        if (!running) return;
        running = false;
        handler.removeCallbacks(poll);
        if (navi != null) {
            try {
                navi.removeListener(listener);
                navi.release();
            } catch (Throwable t) {
                Log.w(TAG, "NaviHelper 해제 실패", t);
            }
        }
        navi = null;
    }

    public GeoPosition latest() {
        return latest;
    }

    public String address() {
        return address;
    }

    /** 최근 5초 안에 받은 현재 도로 제한속도(km/h). 없으면 0. */
    public int speedLimitKmh(long nowMs) {
        return nowMs - speedLimitAtMs < 5_000 ? speedLimitKmh : 0;
    }

    public String status() {
        return status;
    }
}
