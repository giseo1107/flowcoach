package com.giseo.flowcoach.session;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.giseo.flowcoach.BuildConfig;
import com.giseo.flowcoach.R;
import com.giseo.flowcoach.data.demo.DemoScene;
import com.giseo.flowcoach.data.demo.DemoScenario;
import com.giseo.flowcoach.data.location.PositionProvider;
import com.giseo.flowcoach.data.route.RoutePlanner;
import com.giseo.flowcoach.data.route.RouteScenario;
import com.giseo.flowcoach.data.traffic.TrafficFlowRepository;
import com.giseo.flowcoach.data.vehicle.AaosGearSource;
import com.giseo.flowcoach.data.vehicle.AaosSpeedSource;
import com.giseo.flowcoach.data.vehicle.PleosVehicleSignalSource;
import com.giseo.flowcoach.data.vehicle.VirtualConsoleBridge;
import com.giseo.flowcoach.domain.AlertPolicy;
import com.giseo.flowcoach.domain.AutoDrivePolicy;
import com.giseo.flowcoach.domain.CoachConfig;
import com.giseo.flowcoach.domain.CoachEngine;
import com.giseo.flowcoach.domain.CoachMode;
import com.giseo.flowcoach.domain.CoachTuning;
import com.giseo.flowcoach.domain.DrivingSnapshot;
import com.giseo.flowcoach.domain.FlowState;
import com.giseo.flowcoach.domain.Gear;
import com.giseo.flowcoach.domain.TickResult;
import com.giseo.flowcoach.domain.TripReport;
import com.giseo.flowcoach.voice.ChimePlayer;
import com.giseo.flowcoach.voice.SmartSpeaker;
import com.giseo.flowcoach.voice.Speaker;

/**
 * 앱 전체에서 하나만 존재하는 '주행 코칭 실행기'.
 *
 * 주행 세션·판단 엔진·음성을 화면(Activity)이 아니라 여기서 들고 있기 때문에,
 * 내비게이션 등 다른 앱이 앞에 떠 있어도 코칭이 계속된다(CoachService가 프로세스를 유지).
 * 화면은 보일 때만 attach()로 붙어서 상태를 그린다.
 */
public final class CoachRuntime implements DemoSession.Callback, ConsoleDriveSession.Callback,
        RouteDemoSession.Callback {

    /** 화면이 받는 이벤트. 모두 메인 스레드에서 호출된다. */
    public interface Listener {
        void onTick(DrivingSnapshot snapshot, TickResult result, String caption, float progress);

        void onMessage(String text);

        void onDriveEnded(TripReport report);

        /** 안내 한 건을 화면에서 어떻게 보여줄지(위험 경고면 강조). */
        default void onCoachAlert(String text, AlertPolicy.Category category) {
        }

        /** 시연 장면 하나가 끝났을 때(리포트 없이 장면 선택으로 돌아간다). */
        default void onDemoSceneFinished(DemoScene scene) {
        }

        /** 기어를 D로 바꿔서 주행이 저절로 시작됐을 때. */
        default void onDriveAutoStarted() {
        }

        /** 리포트 없이 주행이 끝났을 때(짧은 주행을 P로 끝냄). */
        default void onDriveStoppedQuietly() {
        }
    }

    private static final String TAG = "CoachRuntime";
    private static CoachRuntime instance;

    public static synchronized CoachRuntime get(Context context) {
        if (instance == null) instance = new CoachRuntime(context.getApplicationContext());
        return instance;
    }

    private final Context app;
    private final Speaker speaker;
    /** 조용한 모드의 위험 경고음. */
    private final ChimePlayer chime;

    private CoachMode mode = CoachMode.COACH;
    private CoachConfig config = CoachConfig.demo();
    private CoachEngine engine;
    private DemoSession demoSession;
    private ConsoleDriveSession consoleSession;
    /** 실제 도로 시연(실제 도로 좌표 + 실시간 교통). */
    private RouteDemoSession routeSession;
    /** 실제 도로 시연에 쓴 기준값 이름(화면 표시용). 예) "보통", "자동(보통)". */
    private String routeTuningLabel = "";
    private CoachTuning routeTuning;
    /** 이번 실제 도로 시연에서 실시간 교통을 쓰는지(시작 전 확인에서 고른 값). */
    private volatile boolean routeUseLive = true;

    /** 실시간 교통정보(앱 전체에서 하나, 호출 한도를 아끼기 위해 결과를 재사용). */
    private TrafficFlowRepository traffic;
    /** 실시간 교통정보 사용 여부. 시연 중 정체로 데모가 어려우면 가정값으로 바꿀 수 있다. */
    private volatile boolean liveTraffic = true;

    private Listener listener;
    private TripReport pendingReport; // 화면이 없을 때 끝난 주행의 리포트

    /** 지금 재생 중인 시연 장면(없으면 null). */
    private DemoScene currentScene;
    /** 시연 때 에뮬레이터 차를 함께 움직인다(에뮬레이터에서만). */
    private VirtualConsoleBridge bridge;
    /** 주행 중이 아닐 때 기어를 읽는다(시연은 P에서만, D 자동 시작). */
    private AaosGearSource idleGear;

    // D 자동 시작 · P 자동 종료(AutoDrivePolicy). 앱을 한 번 열면 켜지고, 1초마다 기어를 본다
    private static final long AUTO_POLL_MS = 1000;
    /** 시연이 시작·종료되며 에뮬레이터 차 기어를 직접 바꾸는 동안은 무시한다. */
    private static final long AUTO_SUPPRESS_AFTER_DEMO_MS = 4000;
    private final Handler autoHandler = new Handler(Looper.getMainLooper());
    private final AutoDrivePolicy autoPolicy = new AutoDrivePolicy();
    private boolean autoEnabled = false;
    private long autoSuppressUntilMs = 0;
    private final Runnable autoWatch = new Runnable() {
        @Override
        public void run() {
            watchGear();
            autoHandler.postDelayed(this, AUTO_POLL_MS);
        }
    };

    // 화면이 다시 붙을 때 바로 그리기 위한 마지막 상태
    private DrivingSnapshot lastSnapshot;
    private TickResult lastResult;
    private String lastCaption = "";
    private float lastProgress = -1;
    private String lastMessage;
    private String lastNotificationText;
    private long lastNotificationMs;

    private CoachRuntime(Context app) {
        this.app = app;
        this.speaker = new SmartSpeaker(app);
        this.chime = new ChimePlayer(app);
    }

    // ---------- 화면 연결 ----------

    public void attach(Listener l) {
        listener = l;
        if (isDriving() && lastSnapshot != null) {
            l.onTick(lastSnapshot, lastResult, lastCaption, lastProgress);
        }
        if (lastMessage != null) l.onMessage(lastMessage);
        if (pendingReport != null) {
            TripReport r = pendingReport;
            pendingReport = null;
            l.onDriveEnded(r);
        }
    }

    public void detach(Listener l) {
        if (listener == l) listener = null;
    }

    // ---------- 주행 시작/종료 ----------

    public void startDemo(String startText) {
        stopSessions();
        suppressAuto();
        config = CoachConfig.demo();
        engine = new CoachEngine(config, mode);
        demoSession = new DemoSession(engine, DemoScenario.highway(0), this);
        beginDrive("demo_start", startText);
        demoSession.start();
    }

    public void startConsole(String startText) {
        startConsole(startText, "console_start");
    }

    private void startConsole(String startText, String voiceKey) {
        stopSessions();
        config = CoachConfig.demo();
        engine = new CoachEngine(config, mode);
        AaosSpeedSource speed = null;
        try {
            speed = new AaosSpeedSource(app);
        } catch (Throwable t) { // android.car 라이브러리가 없는 기기
            Log.e(TAG, "차속 소스 생성 실패 — android.car 라이브러리 확인", t);
        }
        AaosGearSource gear = null;
        try {
            gear = new AaosGearSource(app);
        } catch (Throwable t) {
            Log.e(TAG, "기어 소스 생성 실패", t);
        }
        PositionProvider positions = null;
        try {
            positions = new PositionProvider(app);
        } catch (Throwable t) {
            Log.e(TAG, "위치 소스 생성 실패", t);
        }
        // 버튼으로 시작한 콘솔 주행만 크루즈 속도 대체 모델을 쓴다(D 자동 시작은 실제 차속만)
        boolean cruiseFallback = !"auto_start".equals(voiceKey);
        consoleSession = new ConsoleDriveSession(engine, new PleosVehicleSignalSource(app), speed, gear, positions,
                traffic(), () -> liveTraffic, this, cruiseFallback);
        beginDrive(voiceKey, startText);
        consoleSession.start();
        autoPolicy.onDriveStarted(currentGear());
    }

    /**
     * 시연 장면 하나를 재생한다. 주차(P) 중에만 시작할 수 있다(canStartDemo).
     * 장면마다 새 엔진을 써서 앞 장면의 쿨다운이 섞이지 않게 한다.
     */
    public void startDemoScene(DemoScene scene, String startText) {
        stopSessions();
        suppressAuto();
        config = CoachConfig.demo();
        engine = new CoachEngine(config, mode);
        currentScene = scene;
        demoSession = new DemoSession(engine, DemoScenario.scene(scene, 0), this);
        bridge().begin();
        beginDrive("demo_start", startText);
        demoSession.start();
    }

    /**
     * 실제 도로 시연을 시작한다(주차 중에만, canStartDemo).
     * 고른 기준값으로 상황을 배치하고, 실제 교통정보로 판단한다. 끝까지 달리면 리포트를 보여준다.
     * @param tuningLabel 화면에 보일 기준값 이름
     * @param useLive     실시간 교통으로 판단할지(시작 전 확인에서 '시나리오 흐름으로'를 고르면 false)
     */
    public void startRouteDemo(RouteScenario scenario, CoachTuning tuning, String tuningLabel, boolean useLive,
                               String startText) {
        stopSessions();
        suppressAuto();
        config = tuning.toConfig();
        engine = new CoachEngine(config, mode);
        RoutePlanner.Plan plan = RoutePlanner.plan(scenario.situations, config, scenario);
        routeTuningLabel = tuningLabel;
        routeTuning = tuning.copy();
        routeUseLive = useLive;
        routeSession = new RouteDemoSession(engine, scenario, plan, config, traffic(),
                () -> routeUseLive && liveTraffic, this);
        bridge().begin();
        beginDrive("demo_start", startText);
        routeSession.start();
    }

    /** 상황이 지금 기준값에서 언제 안내되는지 한 줄로. */
    public static String explain(DemoScene scene, CoachTuning tuning) {
        CoachTuning.Item item = RoutePlanner.itemFor(scene);
        if (item != null) return tuning.explain(item);
        CoachConfig c = tuning.toConfig();
        return CoachTuning.duration(c.frequentWindowMs) + " 안에 차로를 " + c.frequentCount + "번 바꾸면 안내";
    }

    private String explain(DemoScene scene) {
        return routeTuning != null ? explain(scene, routeTuning) : scene.expected;
    }

    /** 실제 도로 시연 중이면 그 시나리오, 아니면 null. */
    public RouteScenario getCurrentRoute() {
        return isRouteDemoRunning() ? routeSession.scenario() : null;
    }

    /** 실제 도로 시연의 상황 배치(미니맵용). 아니면 null. */
    public RoutePlanner.Plan getRoutePlan() {
        return isRouteDemoRunning() ? routeSession.plan() : null;
    }

    /** 실제 도로 시연에서 달린 거리(m). */
    public double routeMeters() {
        return isRouteDemoRunning() ? routeSession.meters() : 0;
    }

    /** 실제 도로 시연에서 지금 보여주는 상황(상황 사이면 null). */
    public DemoScene routeCurrentScene() {
        return isRouteDemoRunning() ? routeSession.currentScene() : null;
    }

    public boolean isRouteDemoRunning() {
        return routeSession != null && routeSession.isRunning();
    }

    /** 실제 도로 시연 화면용: 지금 상황, 다음 상황, 기준값. */
    public String routeDemoStatus() {
        if (!isRouteDemoRunning()) return "";
        StringBuilder sb = new StringBuilder();
        DemoScene now = routeSession.currentScene();
        sb.append(now != null ? "지금: " + now.title + " — " + explain(now) : "지금: 흐름에 맞춰 주행");
        RoutePlanner.Step next = routeSession.nextSituation();
        if (next != null) {
            sb.append("\n다음: ").append(next.scene.title).append(" (약 ")
                    .append(routeSession.secondsToNext()).append("초 뒤)");
        }
        sb.append("\n기준값: ").append(routeTuningLabel)
                .append(" · 흐름: ").append(routeSession.isFlowLive() ? "실시간 교통"
                        : (routeUseLive ? "가정값 " : "시나리오 흐름 ") + Math.round(routeSession.currentSpan().assumedFlowKmh) + "km/h");
        return sb.toString();
    }

    /** 시연을 시작해도 되는지: 이미 시연 중이거나, 기어가 P(또는 기어 신호 없음)일 때. */
    public boolean canStartDemo() {
        if (isDemoRunning()) return true;
        Gear g = currentGear();
        return g == Gear.PARK || g == Gear.UNKNOWN;
    }

    /** 지금 기어. 차량 API가 없으면 UNKNOWN. */
    public Gear currentGear() {
        if (idleGear == null) {
            try {
                idleGear = new AaosGearSource(app);
                idleGear.start();
            } catch (Throwable t) {
                Log.w(TAG, "기어 읽기 실패", t);
                return Gear.UNKNOWN;
            }
        }
        return idleGear.gear();
    }

    public DemoScene getCurrentScene() {
        return isDemoRunning() ? currentScene : null;
    }

    public String demoVehicleStatus() {
        return bridge().status();
    }

    /** 시연 중 에뮬레이터 차 연동에 문제가 있으면 그 이유, 없으면 null. */
    public String demoVehicleWarning() {
        return bridge().isOverwritten() ? bridge().status() : null;
    }

    private VirtualConsoleBridge bridge() {
        if (bridge == null) bridge = new VirtualConsoleBridge();
        return bridge;
    }

    /** local.properties의 TRAFFIC_API_KEY(국가교통정보센터 인증키)로 만든다. 키가 없으면 가정값만 쓴다. */
    private TrafficFlowRepository traffic() {
        if (traffic == null) traffic = new TrafficFlowRepository(BuildConfig.TRAFFIC_API_KEY);
        return traffic;
    }

    private void beginDrive(String voiceKey, String text) {
        lastSnapshot = null;
        lastResult = null;
        lastCaption = "";
        lastProgress = -1;
        lastNotificationText = null;
        announce(voiceKey, text); // 조용한 모드면 시작 안내도 화면 문장으로만
        CoachService.start(app, text);
    }

    /** 주행을 끝내고 리포트를 넘긴다. */
    public void endDrive() {
        if (isDemoRunning() && currentScene != null) { // 장면 시연 중단: 리포트 없이
            DemoScene scene = currentScene;
            stopSessions();
            releaseService();
            if (listener != null) listener.onDemoSceneFinished(scene);
            return;
        }
        boolean hadDrive = engine != null && engine.getReport().durationSec() > 0;
        stopSessions();
        releaseService();
        if (!hadDrive) return;

        TripReport report = engine.getReport();
        if (mode == CoachMode.COACH) speaker.speak("trip_end", report.spokenSummary()); // 조용한 모드: 리포트 화면·알림으로만
        if (listener != null) {
            listener.onDriveEnded(report);
        } else {
            // 화면이 안 보이면 알림 한 번 → 누르면 앱이 열리며 attach()에서 리포트를 보여준다
            pendingReport = report;
            CoachService.tripEnded(app, "흐름 점수 " + report.score() + "점 · 눌러서 리포트 보기");
        }
    }

    private void stopSessions() {
        if (isDemoRunning()) suppressAuto(); // 시연을 끝내며 에뮬레이터 차를 P로 바꾸는 것을 '주차'로 보지 않게
        if (demoSession != null) {
            demoSession.stop();
            bridge().end(); // 에뮬레이터 차를 세우고 P로
        }
        if (routeSession != null) {
            routeSession.stop();
            bridge().end();
        }
        routeSession = null;
        currentScene = null;
        if (consoleSession != null) consoleSession.stop();
        demoSession = null;
        consoleSession = null;
    }

    // ---------- D 자동 시작 · P 자동 종료 ----------

    /**
     * 기어 감시를 켠다(앱 화면이 열릴 때 MainActivity가 부른다. 여러 번 불러도 한 번만 켜진다).
     * 대기용 포그라운드 서비스를 함께 띄워, 내비가 앞에 있어도(앱이 뒤에 있어도) D로 바꾸면 시작할 수 있게 한다.
     */
    public void enableAutoDrive() {
        if (!isDriving()) CoachService.standby(app, app.getString(R.string.standby_notification));
        if (autoEnabled) return;
        autoEnabled = true;
        autoHandler.post(autoWatch);
    }

    /** D로 바꾸면 저절로 시작할 수 있는 상태인지(감시 켜짐 + 차량 권한 있음). 대기 화면 문구용. */
    public boolean canAutoStart() {
        return autoEnabled && hasCarPermissions();
    }

    private void suppressAuto() {
        autoSuppressUntilMs = SystemClock.elapsedRealtime() + AUTO_SUPPRESS_AFTER_DEMO_MS;
    }

    private void watchGear() {
        long now = SystemClock.elapsedRealtime();
        Gear gear = currentGear();
        boolean demo = isDemoRunning() || now < autoSuppressUntilMs;
        double km = isConsoleRunning() && engine != null ? engine.getReport().getDistanceKm() : 0;
        switch (autoPolicy.onGear(gear, now, isConsoleRunning(), demo, km)) {
            case START:
                if (!hasCarPermissions()) {
                    Log.i(TAG, "D 감지 — 차량 권한이 없어 자동 시작 안 함(처음 한 번은 [콘솔 연동 주행]으로 권한 허용)");
                    return;
                }
                Log.i(TAG, "D 감지 → 주행 자동 시작");
                startConsole(app.getString(R.string.auto_start_message), "auto_start");
                if (listener != null) listener.onDriveAutoStarted();
                break;
            case END_WITH_REPORT:
                Log.i(TAG, "P " + AutoDrivePolicy.ENDS_AFTER_PARK_MS / 1000 + "초 → 주행 자동 종료(리포트)");
                endDrive();
                break;
            case END_QUIET:
                Log.i(TAG, "P " + AutoDrivePolicy.ENDS_AFTER_PARK_MS / 1000 + "초, 짧은 주행 → 리포트 없이 종료");
                stopSessions();
                releaseService();
                if (listener != null) listener.onDriveStoppedQuietly(); // 화면을 대기 상태로 먼저 돌리고
                show(app.getString(R.string.auto_end_short));             // 그 위에 안내 문장
                break;
            default:
                break;
        }
    }

    /** 자동 시작에 필요한 차량 권한(방향지시등·브레이크·차속·기어). 처음 한 번은 화면에서 허용받는다. */
    private boolean hasCarPermissions() {
        String[] needed = {"pleos.car.permission.CAR_LIGHTS", "pleos.car.permission.CAR_DRIVE",
                "android.car.permission.CAR_SPEED", "android.car.permission.CAR_POWERTRAIN"};
        for (String p : needed) {
            if (app.checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) return false;
        }
        return true;
    }

    /** 주행이 끝나면: 자동 시작을 켜 두었으면 대기 알림으로 돌아가고, 아니면 서비스를 내린다. */
    private void releaseService() {
        if (autoEnabled) CoachService.standby(app, app.getString(R.string.standby_notification));
        else CoachService.stop(app);
    }

    /** 시연(기능별 장면 또는 실제 도로 시연) 중인지. */
    public boolean isDemoRunning() {
        return (demoSession != null && demoSession.isRunning()) || isRouteDemoRunning();
    }

    public boolean isConsoleRunning() {
        return consoleSession != null && consoleSession.isRunning();
    }

    public boolean isDriving() {
        return isDemoRunning() || isConsoleRunning();
    }

    // ---------- 세션 콜백 ----------

    @Override
    public void onTick(DemoScenario.Frame frame, TickResult result) {
        bridge().apply(frame.snapshot);
        dispatch(frame.snapshot, result, frame.caption, demoSession != null ? demoSession.progress() : -1);
    }

    @Override
    public void onRouteTick(DrivingSnapshot snapshot, TickResult result, String caption, float progress) {
        bridge().apply(snapshot);
        dispatch(snapshot, result, caption, progress);
    }

    /** 실제 도로 시연을 끝까지 달렸다: 실제 주행처럼 리포트를 보여준다. */
    @Override
    public void onRouteFinished() {
        endDrive();
    }

    @Override
    public void onFinished() {
        DemoScene scene = currentScene;
        if (scene == null) {
            endDrive();
            return;
        }
        // 장면 시연은 리포트 없이 끝내고 장면 선택으로 돌아간다
        stopSessions();
        releaseService();
        currentScene = null;
        if (listener != null) listener.onDemoSceneFinished(scene);
    }

    @Override
    public void onTick(DrivingSnapshot snapshot, TickResult result, String status) {
        dispatch(snapshot, result, status, -1);
    }

    private void dispatch(DrivingSnapshot s, TickResult r, String caption, float progress) {
        lastSnapshot = s;
        lastResult = r;
        lastCaption = caption;
        lastProgress = progress;
        if (r.message != null) {
            // 알림 세분화(AlertPolicy): 코치 모드는 말로, 조용한 모드는 말 없이(위험만 경고음).
            // 위험 경고는 화면이 보이면 강조, 안 보이면 상단 팝업
            AlertPolicy.Delivery d = AlertPolicy.delivery(r.message.type, listener != null, mode);
            if (d.voice) say(r.message.voiceKey, r.message.text);
            else show(r.message.text);
            if (d.chime) chime.play();
            if (d.popup) CoachService.alert(app, r.message.text);
            if (listener != null) listener.onCoachAlert(r.message.text, AlertPolicy.categoryOf(r.message.type));
        }
        if (listener != null) listener.onTick(s, r, caption, progress);
        refreshNotification(false);
    }

    /** 상시 알림 내용을 갱신한다. 내용이 바뀔 때만, 너무 자주는 아니게(조용한 채널이라 화면을 가리지 않음). */
    public void refreshNotification() {
        refreshNotification(true);
    }

    private void refreshNotification(boolean force) {
        if (!isDriving() || lastSnapshot == null || lastResult == null) return;
        String text = notificationText(lastSnapshot, lastResult);
        long now = android.os.SystemClock.elapsedRealtime();
        if (!force && (text.equals(lastNotificationText) || now - lastNotificationMs < 2_000)) return;
        lastNotificationText = text;
        lastNotificationMs = now;
        CoachService.update(app, text);
    }

    /** 상시 알림: "상태 · 권장 속도 · 도로". 예) 흐름에 맞음 · 권장 98 · 경부고속도로 상행 */
    private String notificationText(DrivingSnapshot s, TickResult r) {
        String state;
        if (!s.gear.allowsCoaching()) {
            state = s.gear == Gear.REVERSE ? "후진 중 · 코칭 쉼" : s.gear == Gear.PARK ? "주차 중 · 코칭 쉼" : "중립 · 코칭 쉼";
        } else {
            switch (r.flow.state) {
                case SMOOTH: state = "흐름에 맞음"; break;
                case TOO_SLOW: state = "흐름보다 느림"; break;
                case OVER_LIMIT: state = "제한속도 초과"; break;
                case CONGESTED: state = "정체 구간"; break;
                default: state = "판단 대기"; break;
            }
        }
        StringBuilder sb = new StringBuilder(state);
        if (s.gear.allowsCoaching() && r.flow.recommendedKmh > 0 && r.flow.state != FlowState.UNKNOWN) {
            sb.append(" · 권장 ").append(Math.round(r.flow.recommendedKmh));
        }
        String road = roadLabel();
        if (road != null && !road.isEmpty()) sb.append(" · ").append(road);
        return sb.toString();
    }

    /** 지금 판단에 쓰는 도로 이름(상시 알림·화면용). */
    public String roadLabel() {
        if (isRouteDemoRunning()) {
            String r = routeSession.isFlowLive() && traffic != null ? traffic.roadLabel() : null;
            if (r != null) return r;
            String span = routeSession.currentSpan().name;
            return span != null && !span.isEmpty() ? span : routeSession.scenario().title;
        }
        if (isDemoRunning()) return "시연 주행";
        if (consoleSession != null && consoleSession.isFlowLive() && traffic != null) {
            String r = traffic.roadLabel();
            return r != null ? r : "실시간 교통";
        }
        return isConsoleRunning() ? "가정값" : "";
    }

    private void say(String voiceKey, String text) {
        lastMessage = text;
        speaker.speak(voiceKey, text);
        if (listener != null) listener.onMessage(text);
    }

    /** 소리 없이 화면 문장으로만. */
    private void show(String text) {
        lastMessage = text;
        if (listener != null) listener.onMessage(text);
    }

    /** 코치 모드면 말하고, 조용한 모드면 화면에만 보인다. */
    private void announce(String voiceKey, String text) {
        if (mode == CoachMode.COACH) say(voiceKey, text);
        else show(text);
    }

    // ---------- 기타 ----------

    /** "지금 어때?"에 답하고(음성), 화면에 쓸 문장을 돌려준다. */
    public String answerNow(String notDrivingText) {
        if (engine == null || !isDriving()) {
            speaker.speak("now_unknown", notDrivingText);
            return notDrivingText;
        }
        String reply = engine.describeNow();
        speaker.speak(engine.describeNowKey(), reply);
        return reply;
    }

    /** 실시간 교통정보 ↔ 가정값 전환. 바뀐 상태(true=실시간)를 돌려준다. */
    public boolean toggleLiveTraffic() {
        liveTraffic = !liveTraffic;
        return liveTraffic;
    }

    public boolean isLiveTraffic() {
        return liveTraffic;
    }

    /** 흐름 속도의 출처. 콘솔 주행이면 "실시간"/"가정", 시연 주행이면 "시연". */
    public String flowSourceLabel() {
        if (isConsoleRunning()) return consoleSession.isFlowLive() ? "실시간" : "가정";
        if (isRouteDemoRunning()) return routeSession.isFlowLive() ? "실시간" : "가정";
        if (isDemoRunning()) return "시연";
        return "";
    }

    public CoachMode toggleMode() {
        mode = (mode == CoachMode.COACH) ? CoachMode.QUIET : CoachMode.COACH;
        if (engine != null) engine.setMode(mode);
        return mode;
    }

    public CoachMode getMode() {
        return mode;
    }

    public CoachConfig getConfig() {
        return config;
    }

    public String voiceStatus() {
        return speaker.status();
    }
}
