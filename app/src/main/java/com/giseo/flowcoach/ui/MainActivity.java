package com.giseo.flowcoach.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.giseo.flowcoach.R;
import com.giseo.flowcoach.data.demo.DemoScene;
import com.giseo.flowcoach.data.route.RoadSpan;
import com.giseo.flowcoach.data.route.RouteScenario;
import com.giseo.flowcoach.data.route.RouteScenarios;
import com.giseo.flowcoach.data.vehicle.VirtualConsoleBridge;
import com.giseo.flowcoach.domain.AlertPolicy;
import com.giseo.flowcoach.domain.CoachEvent;
import com.giseo.flowcoach.domain.CoachMode;
import com.giseo.flowcoach.domain.DrivingSnapshot;
import com.giseo.flowcoach.domain.FlowResult;
import com.giseo.flowcoach.domain.FlowState;
import com.giseo.flowcoach.domain.Gear;
import com.giseo.flowcoach.domain.RoadType;
import com.giseo.flowcoach.domain.TickResult;
import com.giseo.flowcoach.domain.TripReport;
import com.giseo.flowcoach.domain.TurnSignal;
import com.giseo.flowcoach.session.CoachRuntime;
import com.giseo.flowcoach.voice.VoiceQuery;

import java.util.ArrayList;
import java.util.List;

/**
 * 코치 패널(주행 화면). 내비 옆 1:1 반쪽 세로 화면이 기본이고, 앱 혼자 띄우면 가로 배치를 쓴다
 * (layout/ = 세로, layout-land/ = 가로. 창 모양이 바뀌면 시스템이 화면을 다시 만들고,
 * 주행 상태는 CoachRuntime이 갖고 있어서 attach() 때 그대로 다시 그린다).
 *
 * 한눈에 보는 정보만: 원형 흐름 게이지(지금 속도 + 상태 문장) · 권장/제한/흐름 숫자 · 최근 안내.
 * 주행 중 버튼은 코치/조용한 모드 하나. 게이지를 누르면 "지금 어때?"(지금 상태를 음성으로).
 * 시연·콘솔 주행·주행 끝내기 버튼은 주행 전, 주차 중, 시연 중에만 보인다.
 *
 * 주행 자체(판단·음성)는 CoachRuntime이 하고, 이 화면은 보일 때만 붙어서 그린다.
 * 그래서 내비게이션 앱을 띄워도 코칭은 계속된다.
 *
 * 주행 방식 두 가지
 *  - 시연 주행      : 기능별 장면 6개(주차 중에만 시작)
 *  - 콘솔 연동 주행 : Pleos Vehicle SDK + 실제 차속으로 Virtual Console 조작에 실시간 반응
 */
public class MainActivity extends AppCompatActivity implements CoachRuntime.Listener {

    private static final String TAG = "MainActivity";
    private static final int REQ_DRIVE = 100;
    private static final int REQ_MIC = 101;
    private static final int ACTION_DEMO = 1;
    private static final int ACTION_CONSOLE = 2;
    private static final int ACTION_ROUTE = 3;
    private static final int REQ_ROUTE = 102;

    private static final String PERM_CAR_LIGHTS = "pleos.car.permission.CAR_LIGHTS";
    private static final String PERM_CAR_DRIVE = "pleos.car.permission.CAR_DRIVE";
    private static final String PERM_CAR_SPEED = "android.car.permission.CAR_SPEED";
    private static final String PERM_CAR_POWERTRAIN = "android.car.permission.CAR_POWERTRAIN";

    private TextView modeText;
    private TextView roadText;
    private TextView laneText;
    private View flowInfoRow;
    private TextView infoRecValue;
    private TextView infoLimitValue;
    private TextView infoFlowValue;
    private TextView infoFlowLabel;
    private TextView messageText;
    private TextView captionText;
    private TextView voiceStatusText;
    private FlowGaugeView gauge;
    /** 시연 중에만: 차로 뷰(계기판 대신)와 경로 미니맵(실제 도로 시연). */
    private DemoLaneView laneView;
    private RouteMiniMapView routeMap;
    /** 지금 차로 뷰가 보여주는 시연(경로 또는 장면). 바뀌면 처음 상태로. */
    private Object shownDemo;
    private String shownRoad;
    private ProgressBar progress;
    private Button demoButton;
    private Button consoleButton;
    private Button modeButton;
    private View actionRow;
    private TextView demoBanner;
    private TextView demoExpected;
    private View scenePanel;
    private TextView scenePanelStatus;
    private DemoScene pendingScene;
    // 실제 도로 시연 준비 화면에서 고른 값(권한 확인 뒤 시작)
    private String pendingRouteId;
    private int[] pendingRouteLevels;
    private String pendingRouteLabel;
    private boolean pendingRouteLive = true;
    /** 마지막으로 받은 기어. 콘솔 주행 중 주차(P)면 [주행 끝내기]를 보여준다. */
    private Gear lastGear = Gear.UNKNOWN;
    private CoachMode renderedMode;

    private CoachRuntime runtime;
    private VoiceQuery voiceQuery;
    private int pendingAction = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        modeText = findViewById(R.id.mode_text);
        roadText = findViewById(R.id.road_text);
        laneText = findViewById(R.id.lane_text);
        flowInfoRow = findViewById(R.id.flow_info_row);
        infoRecValue = findViewById(R.id.info_rec_value);
        infoLimitValue = findViewById(R.id.info_limit_value);
        infoFlowValue = findViewById(R.id.info_flow_value);
        infoFlowLabel = findViewById(R.id.info_flow_label);
        messageText = findViewById(R.id.message_text);
        captionText = findViewById(R.id.caption_text);
        voiceStatusText = findViewById(R.id.voice_status_text);
        gauge = findViewById(R.id.flow_gauge);
        laneView = findViewById(R.id.lane_view);
        routeMap = findViewById(R.id.route_map);
        progress = findViewById(R.id.demo_progress);
        demoButton = findViewById(R.id.demo_button);
        consoleButton = findViewById(R.id.console_button);
        modeButton = findViewById(R.id.mode_button);
        actionRow = findViewById(R.id.action_row);
        demoBanner = findViewById(R.id.demo_banner);
        demoExpected = findViewById(R.id.demo_expected);
        scenePanel = findViewById(R.id.scene_panel);
        scenePanelStatus = findViewById(R.id.scene_panel_status);

        runtime = CoachRuntime.get(this);
        voiceQuery = new VoiceQuery(this);

        // 시연 주행: 장면(기능)별 버튼. 주차(P) 중에만 열린다
        int[] sceneButtonIds = {R.id.scene_btn_1, R.id.scene_btn_2, R.id.scene_btn_3,
                R.id.scene_btn_4, R.id.scene_btn_5, R.id.scene_btn_6};
        DemoScene[] scenes = DemoScene.values();
        for (int i = 0; i < sceneButtonIds.length && i < scenes.length; i++) {
            Button b = findViewById(sceneButtonIds[i]);
            DemoScene scene = scenes[i];
            b.setText(sceneLabel(i + 1, scene));
            b.setOnClickListener(v -> {
                if (!runtime.canStartDemo()) {
                    hideScenePanel();
                    messageText.setText(R.string.demo_only_parked);
                    return;
                }
                pendingScene = scene;
                hideScenePanel();
                startWithPermissions(ACTION_DEMO);
            });
        }
        findViewById(R.id.scene_close_button).setOnClickListener(v -> hideScenePanel());
        // 실제 도로 시연: 준비 화면(경로·기준값)에서 고르고 돌아온다
        findViewById(R.id.scene_btn_route).setOnClickListener(v -> {
            hideScenePanel();
            if (!runtime.canStartDemo()) {
                messageText.setText(R.string.demo_only_parked);
                return;
            }
            startActivityForResult(new Intent(this, RouteDemoActivity.class), REQ_ROUTE);
        });

        demoButton.setOnClickListener(v -> {
            if (!runtime.canStartDemo()) {
                messageText.setText(R.string.demo_only_parked);
                return;
            }
            showScenePanel();
        });
        consoleButton.setOnClickListener(v -> {
            if (runtime.isDriving()) runtime.endDrive(); // 콘솔 주행 종료 또는 시연 종료
            else startWithPermissions(ACTION_CONSOLE);
            renderButtons();
        });
        modeButton.setOnClickListener(v -> {
            CoachMode mode = runtime.toggleMode();
            renderMode();
            // 바뀐 모드가 무엇을 하는지 한 줄로(안내 문장 자리에 잠깐)
            messageText.setText(mode == CoachMode.COACH ? R.string.mode_changed_coach : R.string.mode_changed_quiet);
        });
        // 게이지(가장 큰 터치 영역)를 누르면 "지금 어때?": 지금 상태를 음성으로 알려준다
        gauge.setOnClickListener(v -> onAsk());
        // 개발용: 숫자 칸을 길게 누르면 실시간 교통정보 ↔ 가정값 전환(주행 중 실수로 바뀌지 않게 길게 누르기)
        flowInfoRow.setOnLongClickListener(v -> {
            boolean live = runtime.toggleLiveTraffic();
            Log.i(TAG, "흐름 출처 전환: " + (live ? "실시간" : "가정값"));
            messageText.setText(live ? R.string.live_traffic_on : R.string.live_traffic_off);
            return true;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!runtime.isDriving()) renderIdle();
        renderMode();
        renderButtons();
        runtime.attach(this); // 주행 중이면 마지막 상태를 바로 다시 그려 준다
        // D로 바꾸면 저절로 시작·P에 두면 저절로 끝(시연 중에는 쉼). 화면이 보일 때 대기 서비스를 띄워 둔다
        runtime.enableAutoDrive();
        if (!runtime.isDriving()) renderIdleMessage();
        renderVoiceStatus();
        messageText.postDelayed(this::renderVoiceStatus, 1500); // TTS 초기화는 비동기
    }

    @Override
    protected void onStop() {
        runtime.detach(this); // 화면만 떨어지고 주행은 계속
        super.onStop();
    }

    // ---------- 권한 ----------

    private void startWithPermissions(int action) {
        List<String> missing = missingPermissions(action);
        if (missing.isEmpty()) {
            start(action);
        } else {
            pendingAction = action;
            ActivityCompat.requestPermissions(this, missing.toArray(new String[0]), REQ_DRIVE);
        }
    }

    private List<String> missingPermissions(int action) {
        List<String> wanted = new ArrayList<>();
        if (action == ACTION_CONSOLE) {
            wanted.add(PERM_CAR_LIGHTS);   // 방향지시등
            wanted.add(PERM_CAR_DRIVE);    // 브레이크·크루즈
            wanted.add(PERM_CAR_SPEED);    // 실제 차속
            wanted.add(PERM_CAR_POWERTRAIN); // 기어(P·R·N이면 코칭 쉼)
        }
        wanted.add(Manifest.permission.ACCESS_FINE_LOCATION); // 백그라운드(포그라운드 서비스) 유지
        if (Build.VERSION.SDK_INT >= 33) {
            wanted.add(Manifest.permission.POST_NOTIFICATIONS); // "주행 중" 알림
        }
        List<String> missing = new ArrayList<>();
        for (String p : wanted) {
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        }
        return missing;
    }

    private boolean granted(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_ROUTE || resultCode != RESULT_OK || data == null) return;
        pendingRouteId = data.getStringExtra(RouteDemoActivity.EXTRA_ROUTE_ID);
        pendingRouteLevels = data.getIntArrayExtra(RouteDemoActivity.EXTRA_LEVELS);
        pendingRouteLabel = data.getStringExtra(RouteDemoActivity.EXTRA_LABEL);
        pendingRouteLive = data.getBooleanExtra(RouteDemoActivity.EXTRA_USE_LIVE, true);
        startWithPermissions(ACTION_ROUTE);
    }

    private void start(int action) {
        if (action == ACTION_ROUTE) {
            RouteScenario route = pendingRouteId != null ? RouteScenarios.byId(this, pendingRouteId) : null;
            if (route == null || !runtime.canStartDemo()) {
                messageText.setText(route == null ? R.string.route_none : R.string.demo_only_parked);
                return;
            }
            String label = pendingRouteLabel != null ? pendingRouteLabel : "";
            runtime.startRouteDemo(route, RouteDemoActivity.tuningFrom(pendingRouteLevels), label, pendingRouteLive,
                    getString(R.string.route_demo_start, route.title, label));
            progress.setVisibility(View.VISIBLE);
            renderDemo();
            renderButtons();
            return;
        }
        if (action == ACTION_DEMO) {
            DemoScene scene = pendingScene != null ? pendingScene : DemoScene.SMOOTH;
            pendingScene = null;
            runtime.startDemoScene(scene, getString(R.string.demo_scene_start, scene.title, scene.description));
            progress.setVisibility(View.VISIBLE);
            renderDemo();
        } else {
            lastGear = Gear.UNKNOWN;
            runtime.startConsole(getString(R.string.console_start_message));
            progress.setVisibility(View.GONE);
        }
        renderButtons();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_DRIVE) {
            int action = pendingAction;
            pendingAction = 0;
            // 콘솔 연동은 방향지시등·브레이크 권한이 꼭 필요. 나머지(차속·위치·알림)는 없어도 동작.
            if (action == ACTION_CONSOLE && (!granted(PERM_CAR_LIGHTS) || !granted(PERM_CAR_DRIVE))) {
                messageText.setText(R.string.vehicle_permission_needed);
                return;
            }
            if (action != 0) start(action);
            runtime.enableAutoDrive(); // 권한을 막 받았으면 대기 서비스도 이제 띄울 수 있다
        } else if (requestCode == REQ_MIC) {
            if (granted(Manifest.permission.RECORD_AUDIO)) onAsk();
            else answer(null);
        }
    }

    // ---------- "지금 어때?" ----------

    private void onAsk() {
        if (!VoiceQuery.isAvailable(this)) {
            answer(null); // 음성 인식 서비스가 없으면 바로 답
            return;
        }
        if (!granted(Manifest.permission.RECORD_AUDIO)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_MIC);
            return;
        }
        messageText.setText(R.string.listening);
        voiceQuery.listen(this::answer);
    }

    private void answer(String heard) {
        String reply = runtime.answerNow(getString(R.string.ask_before_drive));
        messageText.setText(heard != null ? "“" + heard + "”\n" + reply : reply);
        renderVoiceStatus();
    }

    // ---------- CoachRuntime.Listener ----------

    @Override
    public void onTick(DrivingSnapshot snapshot, TickResult result, String caption, float demoProgress) {
        render(snapshot, result);
        renderDemo();
        renderDemoViews(snapshot, result);
        renderMode(); // 상시 알림의 [조용한 모드] 버튼으로 바뀌었을 수 있다
        captionText.setText(caption);
        if (demoProgress >= 0) {
            progress.setVisibility(View.VISIBLE);
            progress.setProgress(Math.round(demoProgress * 100));
        }
    }

    @Override
    public void onMessage(String text) {
        messageText.setText(text);
        renderVoiceStatus();
    }

    @Override
    public void onDriveEnded(TripReport report) {
        renderButtons();
        captionText.setText("");
        startActivity(ReportActivity.intent(this, report));
    }

    // ---------- 화면 그리기 ----------

    private void render(DrivingSnapshot s, TickResult r) {
        FlowResult f = r.flow;
        boolean paused = !s.gear.allowsCoaching(); // 주차·후진·중립: 코칭 쉼
        int color = paused ? ContextCompat.getColor(this, R.color.state_unknown) : colorFor(f.state);
        String status = paused ? labelFor(s.gear) : labelFor(f.state);
        gauge.setValues(s.speedKmh, f.recommendedKmh, f.speedLimitKmh, runtime.getConfig().slowMarginKmh, color, status);
        gauge.setSignal(s.turnSignal);

        infoRecValue.setText(f.recommendedKmh > 0 ? String.valueOf(Math.round(f.recommendedKmh)) : "-");
        infoLimitValue.setText(f.speedLimitKmh > 0 ? String.valueOf(f.speedLimitKmh) : "-");
        infoFlowValue.setText(f.flowSpeedKmh > 0 ? String.valueOf(Math.round(f.flowSpeedKmh)) : "-");
        String source = runtime.flowSourceLabel();
        infoFlowLabel.setText(source.isEmpty() ? getString(R.string.info_flow) : getString(R.string.info_flow_source, source));

        String road = runtime.roadLabel();
        if ("가정값".equals(road)) roadText.setText(R.string.road_assumed);
        else roadText.setText(road.isEmpty() ? getString(R.string.road_idle) : road);

        if (paused) {
            laneText.setText(R.string.coaching_paused);
        } else {
            laneText.setText(r.passing ? R.string.lane_passing : R.string.lane_keep);
        }
        laneText.setTextColor(ContextCompat.getColor(this, r.passing && !paused ? R.color.state_slow : R.color.text_secondary));

        if (s.gear != lastGear) {
            lastGear = s.gear;
            renderButtons(); // 주차하면 [주행 끝내기]가 나타나고, D로 가면 숨는다
        }
    }

    /**
     * 시연 중이면 게이지 자리를 나눠 차로 뷰(+ 실제 도로 시연이면 경로 미니맵)를 보여준다.
     * 왼쪽 계기판은 Pleos 시스템 화면이라 그릴 수 없어서, 차로·주변 차는 여기서 보여준다.
     */
    private void renderDemoViews(DrivingSnapshot s, TickResult r) {
        boolean demo = runtime.isDemoRunning();
        RouteScenario route = runtime.getCurrentRoute();
        if (!demo) {
            hideDemoViews();
            return;
        }
        Object key = route != null ? route : runtime.getCurrentScene();
        if (laneView.getVisibility() != View.VISIBLE || key != shownDemo) {
            shownDemo = key;
            shownRoad = null;
            laneView.setVisibility(View.VISIBLE);
            laneView.reset(3, getString(R.string.lane_default_road));
            routeMap.setVisibility(route != null ? View.VISIBLE : View.GONE);
            if (route != null) routeMap.setRoute(route, runtime.getRoutePlan());
        }
        if (route != null) {
            double m = runtime.routeMeters();
            RoadSpan span = route.spanAt(m);
            String road = span.name.isEmpty() ? route.title : span.name;
            if (!road.equals(shownRoad)) {
                shownRoad = road;
                laneView.setRoad(lanesFor(span.type), road);
            }
            routeMap.setProgress(m, runtime.routeCurrentScene());
        }
        double flow = r.flow.flowSpeedKmh > 0 ? r.flow.flowSpeedKmh : r.flow.recommendedKmh;
        laneView.update(s.speedKmh, flow, s.turnSignal, s.brakePressed);
    }

    private void hideDemoViews() {
        if (laneView.getVisibility() == View.GONE) return;
        laneView.stop();
        laneView.setVisibility(View.GONE);
        routeMap.setVisibility(View.GONE);
        shownDemo = null;
    }

    /** 도로 종류별 차로 수(시연 그림용 기본값). */
    private static int lanesFor(RoadType type) {
        switch (type) {
            case EXPRESSWAY: return 4;
            case MOTORWAY: return 3;
            case NATIONAL: return 3;
            default: return 3;
        }
    }

    private void renderIdle() {
        hideDemoViews();
        demoBanner.setVisibility(View.GONE);
        demoExpected.setVisibility(View.GONE);
        gauge.setValues(0, 0, 100, runtime.getConfig().slowMarginKmh,
                ContextCompat.getColor(this, R.color.state_unknown), getString(R.string.state_unknown));
        gauge.setSignal(TurnSignal.OFF);
        infoRecValue.setText("-");
        infoLimitValue.setText("-");
        infoFlowValue.setText("-");
        infoFlowLabel.setText(R.string.info_flow);
        roadText.setText(R.string.road_idle);
        laneText.setText("");
        renderIdleMessage();
        captionText.setText("");
        progress.setProgress(0);
        progress.setVisibility(View.GONE);
    }

    /**
     * 아래 버튼 줄. 운전 중 터치를 줄이려고 주행 중(D)에는 숨긴다.
     *  - 주행 전        : [시연 주행] [콘솔 연동 주행]
     *  - 시연 중        : [다른 장면] [시연 종료]
     *  - 콘솔 주행·주차 : [주행 끝내기] (기어 신호가 없을 때도 끝낼 수 있게 보여준다)
     */
    private void renderButtons() {
        boolean demo = runtime.isDemoRunning();
        boolean console = runtime.isConsoleRunning();
        if (demo) {
            actionRow.setVisibility(View.VISIBLE);
            demoButton.setVisibility(View.VISIBLE);
            demoButton.setText(R.string.demo_other_scene);
            consoleButton.setText(R.string.demo_stop);
        } else if (console) {
            boolean parked = lastGear == Gear.PARK || lastGear == Gear.UNKNOWN;
            actionRow.setVisibility(parked ? View.VISIBLE : View.GONE);
            demoButton.setVisibility(View.GONE);
            consoleButton.setText(R.string.end_drive);
        } else {
            actionRow.setVisibility(View.VISIBLE);
            demoButton.setVisibility(View.VISIBLE);
            demoButton.setText(R.string.start_demo);
            consoleButton.setText(R.string.start_console);
        }
    }

    // ---------- 시연 주행 ----------

    /** 시연 중이면 '시연 주행입니다' 표시와 이 장면에서 나와야 하는 반응을 보여준다. */
    private void renderDemo() {
        RouteScenario route = runtime.getCurrentRoute();
        if (route != null) { // 실제 도로 시연: 지금 상황·다음 상황·기준값
            demoBanner.setVisibility(View.VISIBLE);
            demoExpected.setVisibility(View.VISIBLE);
            String text = route.title + "\n" + runtime.routeDemoStatus();
            String warning = runtime.demoVehicleWarning();
            demoExpected.setText(warning == null ? text : text + "\n⚠ " + warning);
            return;
        }
        DemoScene scene = runtime.getCurrentScene();
        if (scene == null) {
            demoBanner.setVisibility(View.GONE);
            demoExpected.setVisibility(View.GONE);
            return;
        }
        demoBanner.setVisibility(View.VISIBLE);
        demoExpected.setVisibility(View.VISIBLE);
        String text = getString(R.string.demo_expected, scene.title, scene.description, scene.expected);
        if (runtime.getMode() == CoachMode.QUIET) text = text + "\n" + quietDemoNote(scene);
        String warning = runtime.demoVehicleWarning();
        demoExpected.setText(warning == null ? text : text + "\n⚠ " + warning);
    }

    /** 조용한 모드에서 이 장면이 어떻게 보이는지(말 대신 경고음 / 문장만 / 안내 없음). */
    private String quietDemoNote(DemoScene scene) {
        switch (AlertPolicy.categoryOf(scene.expectedEvent)) {
            case DANGER: return getString(R.string.demo_quiet_danger);
            case HABIT: return scene.expectedEvent == CoachEvent.Type.TOO_SLOW
                    ? getString(R.string.demo_quiet_none) : getString(R.string.demo_quiet_habit);
            default: return getString(R.string.demo_quiet_none);
        }
    }

    private void showScenePanel() {
        String vehicle = runtime.isDemoRunning() || !"에뮬레이터 차 연동 확인 전".equals(runtime.demoVehicleStatus())
                ? runtime.demoVehicleStatus()
                : (VirtualConsoleBridge.looksLikeEmulator() ? "에뮬레이터 차도 함께 움직여요" : "앱 화면만 시연해요");
        scenePanelStatus.setText(getString(R.string.demo_banner) + " · " + vehicle);
        scenePanel.setVisibility(View.VISIBLE);
    }

    private void hideScenePanel() {
        scenePanel.setVisibility(View.GONE);
    }

    /** 버튼 글자: "① 흐름에 맞음" + 작은 글씨 설명. */
    private static CharSequence sceneLabel(int number, DemoScene scene) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(String.valueOf((char) ('\u2460' + number - 1))).append(' ').append(scene.title).append('\n');
        int start = sb.length();
        sb.append(scene.description);
        sb.setSpan(new RelativeSizeSpan(0.7f), start, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /** 위험 경고(과속·급제동 반복)는 화면이 보일 때 안내 문장을 빨간색으로 몇 초 강조한다(팝업 대신). */
    @Override
    public void onCoachAlert(String text, AlertPolicy.Category category) {
        messageText.removeCallbacks(clearHighlight);
        if (category == AlertPolicy.Category.DANGER) {
            messageText.setTextColor(ContextCompat.getColor(this, R.color.state_over));
            messageText.postDelayed(clearHighlight, 5_000);
        } else {
            clearHighlight.run();
        }
    }

    private final Runnable clearHighlight = () ->
            messageText.setTextColor(ContextCompat.getColor(this, R.color.text_primary));

    /** 대기 문장: D 자동 시작이 되는 상태면 그걸 알려준다. */
    private void renderIdleMessage() {
        boolean coach = runtime.getMode() == CoachMode.COACH;
        if (runtime.canAutoStart()) {
            messageText.setText(coach ? R.string.idle_message_auto : R.string.idle_message_quiet_auto);
        } else {
            messageText.setText(coach ? R.string.idle_message : R.string.idle_message_quiet);
        }
    }

    @Override
    public void onDriveAutoStarted() {
        lastGear = Gear.UNKNOWN;
        hideDemoViews();
        progress.setVisibility(View.GONE);
        renderButtons();
    }

    @Override
    public void onDriveStoppedQuietly() {
        renderIdle();
        renderButtons();
    }

    @Override
    public void onDemoSceneFinished(DemoScene scene) {
        renderIdle();
        renderButtons();
        messageText.setText(getString(R.string.demo_scene_finished, scene.title));
        showScenePanel(); // 다음 장면을 바로 고를 수 있게
    }

    /** 위쪽 표시 = 지금 모드, 버튼 = 누르면 바뀔 모드(상시 알림 버튼과 같은 말). */
    private void renderMode() {
        if (runtime.getMode() == renderedMode) return; // 매 틱 다시 그리지 않게
        renderedMode = runtime.getMode();
        boolean coach = renderedMode == CoachMode.COACH;
        modeText.setText(coach ? R.string.mode_chip_coach : R.string.mode_chip_quiet);
        modeButton.setText(coach ? R.string.mode_to_quiet : R.string.mode_to_coach);
    }

    /** 개발용 상태 줄: 음성이 안 나올 때 이유 + 지금 창 크기(dp). 화면 설계 기준을 실제 값으로 확인하려고 둔다. */
    private void renderVoiceStatus() {
        Configuration c = getResources().getConfiguration();
        String window = getString(R.string.dev_window, c.screenWidthDp, c.screenHeightDp, c.densityDpi);
        voiceStatusText.setText("🔊 " + runtime.voiceStatus() + " · " + window);
    }

    private int colorFor(FlowState state) {
        int res;
        switch (state) {
            case SMOOTH: res = R.color.state_smooth; break;
            case TOO_SLOW: res = R.color.state_slow; break;
            case OVER_LIMIT: res = R.color.state_over; break;
            case CONGESTED: res = R.color.state_congested; break;
            default: res = R.color.state_unknown; break;
        }
        return ContextCompat.getColor(this, res);
    }

    private String labelFor(Gear gear) {
        switch (gear) {
            case PARK: return getString(R.string.gear_park);
            case REVERSE: return getString(R.string.gear_reverse);
            case NEUTRAL: return getString(R.string.gear_neutral);
            default: return getString(R.string.state_unknown);
        }
    }

    private String labelFor(FlowState state) {
        switch (state) {
            case SMOOTH: return getString(R.string.state_smooth);
            case TOO_SLOW: return getString(R.string.state_slow);
            case OVER_LIMIT: return getString(R.string.state_over);
            case CONGESTED: return getString(R.string.state_congested);
            default: return getString(R.string.state_unknown);
        }
    }

    @Override
    protected void onDestroy() {
        voiceQuery.destroy(); // 주행(CoachRuntime)은 멈추지 않는다
        super.onDestroy();
    }
}
