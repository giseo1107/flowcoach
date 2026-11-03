package com.giseo.flowcoach.session;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.giseo.flowcoach.data.demo.DemoScene;
import com.giseo.flowcoach.data.location.GeoPosition;
import com.giseo.flowcoach.data.route.RoadSpan;
import com.giseo.flowcoach.data.route.RouteDriver;
import com.giseo.flowcoach.data.route.RoutePlanner;
import com.giseo.flowcoach.data.route.RouteScenario;
import com.giseo.flowcoach.data.traffic.TrafficFlowRepository;
import com.giseo.flowcoach.domain.CoachConfig;
import com.giseo.flowcoach.domain.CoachEngine;
import com.giseo.flowcoach.domain.DrivingSnapshot;
import com.giseo.flowcoach.domain.Gear;
import com.giseo.flowcoach.domain.RoadContext;
import com.giseo.flowcoach.domain.RoadType;
import com.giseo.flowcoach.domain.TickResult;

import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * 실제 도로 시연: 실제 도로 좌표 위를 1초씩 달리며, 그 위치의 실시간 교통정보로 판단한다.
 *
 *  - 위치·진행 방향: 시나리오 경로(RouteDriver) → 교통 조회(주변 도로·상하행 선택)에 그대로 쓴다
 *  - 흐름 속도: 국가교통정보센터 실시간 교통. 못 받으면 시나리오 가정값
 *  - 제한속도: 시나리오 값(실시간 교통에는 제한속도가 없다)
 *  - 운전(속도·방향지시등·급제동): RoutePlanner가 배치한 상황대로, 그때의 권장 속도를 기준으로
 * 판단은 실제 주행과 같은 CoachEngine.tick()이다.
 */
public final class RouteDemoSession {

    public interface Callback {
        void onRouteTick(DrivingSnapshot snapshot, TickResult result, String caption, float progress);

        void onRouteFinished();
    }

    private static final long TICK_MS = 1000;
    /** 차로 변경 판단이 도로 곡선을 '회전'으로 오해하지 않게, 엔진에는 일정한 진행 방향을 준다. */
    private static final double ENGINE_HEADING = 90.0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CoachEngine engine;
    private final RouteScenario scenario;
    private final RoutePlanner.Plan plan;
    private final RouteDriver driver;
    private final TrafficFlowRepository traffic; // null이면 가정값만
    private final BooleanSupplier liveTraffic;
    private final Callback callback;

    private boolean running = false;
    private volatile boolean flowLive = false;
    private volatile DemoScene currentScene;
    private volatile double lastSpeed;
    private volatile RoadSpan currentSpan;

    private final Runnable step = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            long now = SystemClock.elapsedRealtime();
            double[] here = scenario.line.at(driver.meters());
            // 지금 구간의 도로 종류·제한속도·가정 흐름(사용자 경로는 시내·고속도로가 섞인다)
            RoadSpan span = scenario.spanAt(driver.meters());
            RoadContext fallback = new RoadContext(span.type, span.limitKmh, span.assumedFlowKmh);

            currentSpan = span;

            RoadContext road = fallback;
            if (traffic != null && liveTraffic.getAsBoolean()) {
                GeoPosition position = new GeoPosition(here[0], here[1], here[2], "시연 경로", now);
                traffic.maybeRefresh(TrafficFlowRepository.Query.onRoute(position, lastSpeed, span.limitKmh,
                        span.name, span.type == RoadType.EXPRESSWAY), now);
                RoadContext live = traffic.roadOr(fallback, now);
                // 구간이 바뀐 직후 등, 받아 둔 실시간 값이 지금 구간과 다른 종류의 도로면 쓰지 않는다
                // (예: 시내 구간인데 옆 고속도로 흐름 89km/h)
                if (live != fallback && live.roadType.isUninterruptedFlow() == span.type.isUninterruptedFlow()) {
                    road = live;
                }
            }
            flowLive = road != fallback;
            // 실시간 교통에는 제한속도가 없어서 구간 값을 쓴다(도로 종류도 위에서 맞춰 본 구간 값으로)
            road = new RoadContext(span.type, span.limitKmh, road.flowSpeedKmh);
            double rec = road.hasFlowSpeed() ? Math.min(road.flowSpeedKmh, road.speedLimitKmh) : road.speedLimitKmh;

            RouteDriver.Output o = driver.step(rec, road.speedLimitKmh);
            currentScene = o.scene;
            lastSpeed = o.speedKmh;
            DrivingSnapshot snapshot = new DrivingSnapshot(now, o.speedKmh, ENGINE_HEADING, o.signal,
                    o.pedalPercent > 0, o.pedalPercent, Gear.DRIVE);
            TickResult result = engine.tick(snapshot, road);
            String caption = o.caption + String.format(Locale.KOREA, " · %.1f/%.1fkm",
                    o.meters / 1000.0, scenario.lengthKm());
            callback.onRouteTick(snapshot, result, caption, progress());
            if (o.finished) {
                running = false;
                callback.onRouteFinished();
                return;
            }
            handler.postDelayed(this, TICK_MS);
        }
    };

    public RouteDemoSession(CoachEngine engine, RouteScenario scenario, RoutePlanner.Plan plan, CoachConfig config,
                            TrafficFlowRepository traffic, BooleanSupplier liveTraffic, Callback callback) {
        this.engine = engine;
        this.scenario = scenario;
        this.plan = plan;
        this.driver = new RouteDriver(scenario.line, plan, config);
        this.traffic = traffic;
        this.liveTraffic = liveTraffic;
        this.callback = callback;
    }

    public void start() {
        if (running) return;
        running = true;
        handler.post(step);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(step);
    }

    public boolean isRunning() {
        return running;
    }

    public boolean isFlowLive() {
        return flowLive;
    }

    /** 지금 보여주는 상황(상황 사이를 달리는 중이면 null). */
    public DemoScene currentScene() {
        return currentScene;
    }

    /** 다음 상황. 없으면 null. */
    public RoutePlanner.Step nextSituation() {
        return driver.nextSituation();
    }

    /** 다음 상황까지 남은 시간(초, 지금 속도 기준). */
    public int secondsToNext() {
        return driver.secondsToNext();
    }

    public int elapsedSec() {
        return driver.elapsedSec();
    }

    /** 지금 달리는 구간(아직 시작 전이면 첫 구간). */
    public RoadSpan currentSpan() {
        RoadSpan s = currentSpan;
        return s != null ? s : scenario.spanAt(0);
    }

    /** 출발점에서 달린 거리(m). */
    public double meters() {
        return driver.meters();
    }

    public float progress() {
        return (float) (driver.meters() / scenario.line.lengthMeters());
    }

    public RouteScenario scenario() {
        return scenario;
    }

    public RoutePlanner.Plan plan() {
        return plan;
    }
}
