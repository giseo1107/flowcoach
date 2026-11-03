package com.giseo.flowcoach.session;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.giseo.flowcoach.data.demo.ConsoleRoad;
import com.giseo.flowcoach.data.location.GeoPosition;
import com.giseo.flowcoach.data.location.PositionProvider;
import com.giseo.flowcoach.data.demo.SimulatedDrive;
import com.giseo.flowcoach.data.traffic.TrafficFlowRepository;
import com.giseo.flowcoach.data.vehicle.AaosGearSource;
import com.giseo.flowcoach.data.vehicle.AaosSpeedSource;
import com.giseo.flowcoach.data.vehicle.VehicleSignalSource;
import com.giseo.flowcoach.domain.CoachEngine;
import com.giseo.flowcoach.domain.DrivingSnapshot;
import com.giseo.flowcoach.domain.Gear;
import com.giseo.flowcoach.domain.RoadContext;
import com.giseo.flowcoach.domain.TickResult;
import com.giseo.flowcoach.domain.TurnSignal;

import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Virtual Console 연동 주행.
 *  - 방향지시등·브레이크·크루즈 목표 속도: Pleos Vehicle SDK
 *  - 기어: AAOS 표준 GEAR_SELECTION. P·R·N이면 코칭(판단·음성·리포트)을 쉰다
 *  - 차속: AAOS 표준 PERF_VEHICLE_SPEED (Virtual Console의 가속·브레이크 페달)
 *    차가 한 번도 움직이지 않았다면(차속 신호가 없으면) 크루즈 목표 속도를 따라가는 간단한 모델로 대신한다.
 *  - 위치: Pleos 내비(NaviHelper) → GPS. 현재 도로 제한속도도 내비에서 받는다
 *  - 흐름 속도: 국가교통정보센터 실시간 교통소통정보
 *    위치가 있으면 주변 도로·진행 방향으로, 없으면 시연 위치(경부고속도로 상행, 판교 부근)로 조회
 *    API 키가 없거나 조회 실패 시 ConsoleRoad 가정값(자동차전용도로, 제한 90, 흐름 88)
 */
public final class ConsoleDriveSession {

    public interface Callback {
        void onTick(DrivingSnapshot snapshot, TickResult result, String status);
    }

    private static final String TAG = "ConsoleDrive";
    private static final long TICK_MS = 1000;
    private static final double HEADING = 90.0;
    /** 위치를 모를 때 쓰는 시연 조회(경부고속도로 상행, 판교 부근). */
    private static final TrafficFlowRepository.Query DEMO_QUERY = TrafficFlowRepository.Query.fixed(
            ConsoleRoad.DEMO_LAT, ConsoleRoad.DEMO_LON, ConsoleRoad.DEMO_ROUTE_NO,
            ConsoleRoad.DEMO_ROAD_HINT, ConsoleRoad.DEMO_DIRECTION);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CoachEngine engine;
    private final VehicleSignalSource signals;
    private final AaosSpeedSource speedSource; // 차량 API가 없는 환경이면 null
    private final PositionProvider positions;  // null이면 항상 시연 위치
    private final AaosGearSource gearSource;   // null이면 기어를 모름(계속 코칭)
    private final TrafficFlowRepository traffic; // null이면 가정값만 사용. CoachRuntime이 소유
    /** 실시간 교통정보를 쓸지(시연 중 전환 가능). */
    private final BooleanSupplier liveTraffic;
    private final Callback callback;
    private final SimulatedDrive model = new SimulatedDrive(70, 85);
    /**
     * 실제 차속이 아직 0이면 크루즈 목표 속도를 따라가는 모델로 대신할지.
     * [콘솔 연동 주행] 버튼(크루즈 속도로 조작해 보는 시연)에서만 켠다.
     * D 자동 시작은 실제 주행처럼 실제 차속만 믿는다(서 있으면 0km/h).
     */
    private final boolean cruiseFallback;

    private boolean running = false;
    /** 마지막 틱에서 실시간 교통정보로 흐름을 판단했는지(화면 표시용). */
    private volatile boolean flowLive = false;

    private final Runnable step = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            long now = SystemClock.elapsedRealtime();
            boolean brake = signals.isBrakePressed();
            TurnSignal signal = signals.currentTurnSignal();
            double cruise = signals.cruiseTargetKmh();
            Gear gear = gearSource != null ? gearSource.gear() : Gear.UNKNOWN;

            double real = speedSource != null ? speedSource.speedKmh() : Double.NaN; // 매초 직접 읽기
            boolean realSpeed = speedSource != null && !Double.isNaN(real)
                    && (speedSource.hasMoved() || !cruiseFallback);
            // 차속 신호가 없을 때 쓰는 모델: P·R에서는 0(주차 중에 크루즈 속도가 보이지 않게)
            double modelSpeed = (gear == Gear.PARK || gear == Gear.REVERSE)
                    ? model.stop(now) : model.step(now, brake, cruise);
            double speed = realSpeed ? real : modelSpeed;

            GeoPosition position = positions != null ? positions.current(now) : null;
            int naviLimit = positions != null ? positions.speedLimitKmh(now) : 0;

            RoadContext road = ConsoleRoad.ROAD;
            if (traffic != null && liveTraffic.getAsBoolean()) {
                TrafficFlowRepository.Query q = position != null
                        ? TrafficFlowRepository.Query.located(position, speed, naviLimit)
                        : DEMO_QUERY;
                traffic.maybeRefresh(q, now);
                road = traffic.roadOr(ConsoleRoad.ROAD, now);
            }
            flowLive = road != ConsoleRoad.ROAD;
            // 내비가 현재 도로 제한속도를 알려주면 추정값 대신 그것을 쓴다
            if (naviLimit > 0) road = new RoadContext(road.roadType, naviLimit, road.flowSpeedKmh);

            DrivingSnapshot snapshot = new DrivingSnapshot(now, speed, HEADING, signal, brake,
                    DrivingSnapshot.PEDAL_UNKNOWN, gear);
            TickResult result = engine.tick(snapshot, road);
            callback.onTick(snapshot, result, statusLine(realSpeed, cruise, brake, gear, now));
            handler.postDelayed(this, TICK_MS);
        }
    };

    public ConsoleDriveSession(CoachEngine engine, VehicleSignalSource signals,
                               AaosSpeedSource speedSource, AaosGearSource gearSource, PositionProvider positions,
                               TrafficFlowRepository traffic, BooleanSupplier liveTraffic, Callback callback,
                               boolean cruiseFallback) {
        this.cruiseFallback = cruiseFallback;
        this.engine = engine;
        this.signals = signals;
        this.speedSource = speedSource;
        this.gearSource = gearSource;
        this.positions = positions;
        this.traffic = traffic;
        this.liveTraffic = liveTraffic;
        this.callback = callback;
    }

    public void start() {
        if (running) return;
        running = true;
        signals.start();
        if (speedSource != null) {
            try {
                speedSource.start();
            } catch (Throwable t) { // 차량 라이브러리가 없는 기기
                Log.w(TAG, "차속 소스 시작 실패", t);
            }
        }
        if (gearSource != null) {
            try {
                gearSource.start();
            } catch (Throwable t) {
                Log.w(TAG, "기어 소스 시작 실패", t);
            }
        }
        if (positions != null) {
            try {
                positions.start();
            } catch (Throwable t) {
                Log.w(TAG, "위치 소스 시작 실패", t);
            }
        }
        handler.post(step);
    }

    public void stop() {
        if (!running) return;
        running = false;
        handler.removeCallbacks(step);
        signals.stop();
        if (speedSource != null) speedSource.stop();
        if (gearSource != null) gearSource.stop();
        if (positions != null) positions.stop();
        // traffic은 CoachRuntime이 앱 전체에서 공유하므로 여기서 닫지 않는다(최근 결과 재사용)
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isFlowLive() {
        return flowLive;
    }

    private String statusLine(boolean realSpeed, double cruiseKmh, boolean brake, Gear gear, long nowMs) {
        String speedFrom;
        if (realSpeed) {
            speedFrom = "차속: 실제 차량 신호";
        } else if (gear == Gear.PARK || gear == Gear.REVERSE) {
            speedFrom = "차속: 0km/h(" + gear.letter() + ", 차속 신호 없음)";
        } else {
            // 실제 차속을 못 쓰는 이유를 같이 보여준다(권한 없음, 아직 정지 상태 등)
            String why = speedSource == null ? "차량 API 없음" : speedSource.status();
            speedFrom = "차속: 크루즈 " + (Double.isNaN(cruiseKmh) ? "-" : String.format(Locale.KOREA, "%.0f", cruiseKmh))
                    + "km/h 따라감 [실제 차속: " + why + "]";
        }
        String flowFrom;
        if (flowLive) {
            flowFrom = "흐름: " + traffic.status();
        } else if (traffic != null && !liveTraffic.getAsBoolean()) {
            flowFrom = "흐름: 가정값 88km/h (실시간 끔)";
        } else {
            flowFrom = "흐름: 가정값 88km/h" + (traffic != null ? " [" + traffic.status() + "]" : "");
        }
        String where = positions != null ? positions.status(nowMs) : "위치: 사용 안 함 → 시연 위치";
        String gearText = gear != Gear.UNKNOWN ? "기어 " + gear.letter()
                + (gear.allowsCoaching() ? "" : "(코칭 쉼)")
                : "기어: " + (gearSource != null ? gearSource.status() : "차량 API 없음");
        return signals.statusText() + " · " + gearText + " · " + speedFrom + " · 브레이크 " + (brake ? "ON" : "OFF")
                + "\n" + where + " · " + flowFrom;
    }
}
