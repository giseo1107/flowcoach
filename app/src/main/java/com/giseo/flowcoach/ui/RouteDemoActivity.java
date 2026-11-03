package com.giseo.flowcoach.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.os.SystemClock;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.giseo.flowcoach.BuildConfig;
import com.giseo.flowcoach.R;
import com.giseo.flowcoach.data.demo.DemoScene;
import com.giseo.flowcoach.data.location.GeoPosition;
import com.giseo.flowcoach.data.location.PositionProvider;
import com.giseo.flowcoach.data.route.KakaoPlaceSearch;
import com.giseo.flowcoach.data.route.OsrmRouter;
import com.giseo.flowcoach.data.route.RoutePlanner;
import com.giseo.flowcoach.data.route.RouteScenario;
import com.giseo.flowcoach.data.route.RouteScenarios;
import com.giseo.flowcoach.data.route.RouteTrafficCheck;
import com.giseo.flowcoach.data.traffic.ItsTrafficClient;
import com.giseo.flowcoach.domain.CoachConfig;
import com.giseo.flowcoach.domain.CoachMode;
import com.giseo.flowcoach.domain.CoachTuning;
import com.giseo.flowcoach.session.CoachRuntime;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Locale;
import java.util.List;

/**
 * 실제 도로 시연 준비 화면(주차 중에만).
 *
 * 1. 경로 고르기
 * 2. 판단 기준값: 자동 / 실제 운전처럼 / 보통 / 빠르게 보기 / 직접 조절(안내마다 빠르게·보통·실제 운전)
 * 3. 이 경로에서 나올 수 있는 안내: 고른 기준값으로 실제로 배치해 보고 ○/△와 이유를 보여준다
 * [시연 시작]을 누르면 경로의 지금 실제 교통을 확인한다. 시나리오와 맞으면 바로, 다르면
 * '실제 교통대로 / 시나리오 흐름으로' 중 고르게 한 뒤 MainActivity에 돌려준다(권한 확인 후 시작).
 */
public class RouteDemoActivity extends AppCompatActivity {

    public static final String EXTRA_ROUTE_ID = "route_id";
    /** CoachTuning.Item 순서대로 Level.ordinal(). */
    public static final String EXTRA_LEVELS = "levels";
    public static final String EXTRA_LABEL = "label";
    /** 실시간 교통으로 판단할지(false면 시나리오 흐름 가정값). */
    public static final String EXTRA_USE_LIVE = "use_live";
    /** 맞다고 확인된 뒤 시작하기까지 결과를 보여주는 시간. */
    private static final long OK_SHOW_MS = 1_500;

    private final List<RouteScenario> routes = new ArrayList<>();
    private RouteScenario selected;
    private CoachTuning.Preset preset = CoachTuning.Preset.AUTO;
    private CoachTuning custom = CoachTuning.of(CoachTuning.Level.NORMAL);

    private LinearLayout routeList;
    private LinearLayout presetRow;
    private LinearLayout customBox;
    private LinearLayout previewBox;
    private TextView presetDesc;
    private TextView notes;
    private Button startButton;
    private View checkBox;
    private TextView checkText;
    private View checkRow;
    private Button checkLive;
    private Button checkAssumed;
    /** 교통 확인 중(버튼 잠금). */
    private boolean checking = false;

    // ---------- 내 경로 만들기 ----------
    private LinearLayout customRouteBox;
    private EditText customQuery;
    private LinearLayout customResults;
    private LinearLayout customChips;
    private TextView customFrom;
    private TextView customTo;
    private TextView customStatus;
    private Button customBuild;
    /** 검색 결과를 출발지에 쓸지(false면 도착지). */
    private boolean pickingFrom = false;
    private KakaoPlaceSearch.Place fromPlace; // null이면 현재 위치
    private KakaoPlaceSearch.Place toPlace;
    private final EnumSet<DemoScene> customScenes = EnumSet.of(DemoScene.SMOOTH, DemoScene.PASSING,
            DemoScene.TOO_SLOW, DemoScene.OVER_LIMIT, DemoScene.HARD_BRAKE, DemoScene.LANE_CHANGES);
    private PositionProvider positions;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_route_demo);
        routeList = findViewById(R.id.route_list);
        presetRow = findViewById(R.id.preset_row);
        customBox = findViewById(R.id.custom_box);
        previewBox = findViewById(R.id.preview_box);
        presetDesc = findViewById(R.id.preset_desc);
        notes = findViewById(R.id.route_notes);
        startButton = findViewById(R.id.route_start);
        checkBox = findViewById(R.id.check_box);
        checkText = findViewById(R.id.check_text);
        checkRow = findViewById(R.id.check_row);
        checkLive = findViewById(R.id.check_live);
        checkAssumed = findViewById(R.id.check_assumed);
        findViewById(R.id.route_close).setOnClickListener(v -> finish());
        startButton.setOnClickListener(v -> checkTrafficThenStart());
        checkLive.setOnClickListener(v -> start(true));
        checkAssumed.setOnClickListener(v -> start(false));

        RouteScenario mine = RouteScenarios.custom();
        if (mine != null) routes.add(mine);
        routes.addAll(RouteScenarios.all(this));
        if (!routes.isEmpty()) selected = routes.get(0);
        try { // 출발지 기본값 = 현재 위치(내비 → GPS)
            positions = new PositionProvider(this);
            positions.start();
        } catch (Throwable t) {
            positions = null;
        }
        buildCustomBox();
        render();
    }

    @Override
    protected void onDestroy() {
        if (positions != null) positions.stop();
        super.onDestroy();
    }

    // ---------- 내 경로 만들기 ----------

    /** 한 번만 만든다(검색어 입력 상태를 지키려고 render() 때 다시 만들지 않는다). */
    private void buildCustomBox() {
        LinearLayout outer = findViewById(R.id.custom_route_box);
        outer.removeAllViews();
        // 제목을 누르면 펼치고 접는다(처음엔 접어서 경로 목록이 바로 보이게)
        TextView title = text(getString(R.string.custom_title) + " ▾", R.dimen.text_body, R.color.text_primary, true);
        outer.addView(title);
        customRouteBox = new LinearLayout(this);
        customRouteBox.setOrientation(LinearLayout.VERTICAL);
        customRouteBox.setVisibility(RouteScenarios.custom() != null ? View.VISIBLE : View.GONE);
        outer.addView(customRouteBox);
        title.setOnClickListener(v -> {
            boolean open = customRouteBox.getVisibility() != View.VISIBLE;
            customRouteBox.setVisibility(open ? View.VISIBLE : View.GONE);
            title.setText(getString(R.string.custom_title) + (open ? " ▴" : " ▾"));
        });
        if (BuildConfig.KAKAO_REST_API_KEY == null || BuildConfig.KAKAO_REST_API_KEY.isEmpty()) {
            customRouteBox.addView(text(getString(R.string.custom_no_key), R.dimen.text_label, R.color.state_slow, false));
            return;
        }
        customFrom = text("", R.dimen.text_label, R.color.text_secondary, false);
        customTo = text("", R.dimen.text_label, R.color.text_secondary, false);
        customRouteBox.addView(customFrom);
        customRouteBox.addView(customTo);

        LinearLayout targetRow = new LinearLayout(this);
        targetRow.setOrientation(LinearLayout.HORIZONTAL);
        customRouteBox.addView(targetRow, matchWrap(px(R.dimen.gap) / 2));

        LinearLayout searchRow = new LinearLayout(this);
        searchRow.setOrientation(LinearLayout.HORIZONTAL);
        searchRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        customQuery = new EditText(this);
        customQuery.setSingleLine(true);
        customQuery.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(R.dimen.text_body));
        customQuery.setTextColor(color(R.color.text_primary));
        customQuery.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        customQuery.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchPlaces();
                return true;
            }
            return false;
        });
        searchRow.addView(customQuery, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2));
        Button search = choiceButton(searchRow, getString(R.string.custom_search), true);
        search.setOnClickListener(v -> searchPlaces());
        customRouteBox.addView(searchRow, matchWrap(px(R.dimen.gap) / 2));

        customResults = new LinearLayout(this);
        customResults.setOrientation(LinearLayout.VERTICAL);
        customRouteBox.addView(customResults);

        customRouteBox.addView(text(getString(R.string.custom_situations), R.dimen.text_label, R.color.text_primary, true),
                matchWrap(px(R.dimen.gap) / 2));
        customChips = new LinearLayout(this);
        customChips.setOrientation(LinearLayout.VERTICAL);
        customRouteBox.addView(customChips);

        customBuild = (Button) LayoutInflater.from(this).inflate(R.layout.item_choice_button, customRouteBox, false);
        customBuild.setText(R.string.custom_build);
        customBuild.setBackgroundTintList(ColorStateList.valueOf(color(R.color.accent)));
        customBuild.setTextColor(color(R.color.on_accent));
        customBuild.setOnClickListener(v -> buildRoute());
        LinearLayout.LayoutParams lp = matchWrap(px(R.dimen.gap) / 2);
        customRouteBox.addView(customBuild, lp);
        customStatus = text("", R.dimen.text_caption, R.color.text_secondary, false);
        customRouteBox.addView(customStatus);

        renderTargets(targetRow);
        renderCustomChips();
        renderFromTo();
    }

    private void renderTargets(LinearLayout row) {
        row.removeAllViews();
        Button to = choiceButton(row, getString(R.string.custom_target_to), !pickingFrom);
        Button from = choiceButton(row, getString(R.string.custom_target_from), pickingFrom);
        to.setOnClickListener(v -> {
            pickingFrom = false;
            renderTargets(row);
        });
        from.setOnClickListener(v -> {
            pickingFrom = true;
            renderTargets(row);
        });
        customQuery.setHint(pickingFrom ? R.string.custom_hint_from : R.string.custom_hint_to);
    }

    private void renderFromTo() {
        GeoPosition here = positions != null ? positions.current(SystemClock.elapsedRealtime()) : null;
        String from = fromPlace != null ? fromPlace.name
                : getString(R.string.custom_here) + (here != null
                ? String.format(Locale.KOREA, " (%.4f, %.4f)", here.lat, here.lon) : "");
        customFrom.setText(getString(R.string.custom_from, from));
        customTo.setText(getString(R.string.custom_to, toPlace != null ? toPlace.name : getString(R.string.custom_none)));
    }

    /** 넣을 상황 고르기(여러 개). 내 경로가 이미 있으면 바로 다시 배치한다. */
    private void renderCustomChips() {
        customChips.removeAllViews();
        DemoScene[] order = {DemoScene.SMOOTH, DemoScene.PASSING, DemoScene.TOO_SLOW,
                DemoScene.OVER_LIMIT, DemoScene.HARD_BRAKE, DemoScene.LANE_CHANGES};
        LinearLayout row = null;
        for (int i = 0; i < order.length; i++) {
            if (i % 3 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                customChips.addView(row, matchWrap(4));
            }
            DemoScene scene = order[i];
            Button b = choiceButton(row, scene.title, customScenes.contains(scene));
            b.setOnClickListener(v -> {
                if (customScenes.contains(scene)) customScenes.remove(scene);
                else customScenes.add(scene);
                renderCustomChips();
                RouteScenario mine = RouteScenarios.custom();
                if (mine != null && !customScenes.isEmpty()) {
                    replaceCustom(mine.withSituations(chosenScenes()));
                    render();
                }
            });
        }
    }

    private List<DemoScene> chosenScenes() {
        List<DemoScene> list = new ArrayList<>();
        for (DemoScene s : new DemoScene[]{DemoScene.SMOOTH, DemoScene.PASSING, DemoScene.TOO_SLOW,
                DemoScene.OVER_LIMIT, DemoScene.HARD_BRAKE, DemoScene.LANE_CHANGES}) {
            if (customScenes.contains(s)) list.add(s);
        }
        return list;
    }

    private void searchPlaces() {
        String q = customQuery.getText().toString().trim();
        if (q.isEmpty() || busy) return;
        hideKeyboard();
        busy = true;
        customStatus.setText(R.string.custom_searching);
        GeoPosition here = positions != null ? positions.current(SystemClock.elapsedRealtime()) : null;
        double lat = here != null ? here.lat : Double.NaN;
        double lon = here != null ? here.lon : Double.NaN;
        final boolean forFrom = pickingFrom;
        new Thread(() -> {
            List<KakaoPlaceSearch.Place> found;
            String error = null;
            try {
                found = new KakaoPlaceSearch(BuildConfig.KAKAO_REST_API_KEY).search(q, lat, lon);
            } catch (Exception e) {
                found = new ArrayList<>();
                error = e.getMessage();
            }
            final List<KakaoPlaceSearch.Place> places = found;
            final String err = error;
            runOnUiThread(() -> {
                busy = false;
                customResults.removeAllViews();
                if (err != null) {
                    customStatus.setText(getString(R.string.custom_fail, getString(R.string.custom_search), err));
                    return;
                }
                customStatus.setText(places.isEmpty() ? getString(R.string.custom_no_result) : "");
                for (KakaoPlaceSearch.Place p : places) {
                    TextView t = text("· " + p.label(), R.dimen.text_label, R.color.text_primary, false);
                    t.setPadding(0, px(R.dimen.gap) / 3, 0, px(R.dimen.gap) / 3);
                    t.setOnClickListener(v -> {
                        if (forFrom) fromPlace = p;
                        else toPlace = p;
                        customResults.removeAllViews();
                        customQuery.setText("");
                        customStatus.setText("");
                        renderFromTo();
                    });
                    customResults.addView(t);
                }
            });
        }, "kakao-search").start();
    }

    /** 출발(현재 위치 또는 검색)→도착 실제 경로를 받아 '내 경로'를 만든다. */
    private void buildRoute() {
        if (busy) return;
        if (toPlace == null) {
            customStatus.setText(R.string.custom_need_to);
            return;
        }
        if (customScenes.isEmpty()) {
            customStatus.setText(R.string.custom_need_situation);
            return;
        }
        GeoPosition here = positions != null ? positions.current(SystemClock.elapsedRealtime()) : null;
        if (fromPlace == null && here == null) {
            customStatus.setText(R.string.custom_no_start);
            return;
        }
        final double fromLat = fromPlace != null ? fromPlace.lat : here.lat;
        final double fromLon = fromPlace != null ? fromPlace.lon : here.lon;
        final String fromName = fromPlace != null ? fromPlace.name : getString(R.string.custom_here);
        final KakaoPlaceSearch.Place to = toPlace;
        final List<DemoScene> scenes = chosenScenes();
        busy = true;
        customStatus.setText(R.string.custom_routing);
        new Thread(() -> {
            RouteScenario made = null;
            String error = null;
            try {
                OsrmRouter.Route route = OsrmRouter.route(fromLat, fromLon, to.lat, to.lon);
                made = OsrmRouter.toScenario(route, fromName, to.name, scenes);
            } catch (Exception e) {
                error = e.getMessage();
            }
            final RouteScenario result = made;
            final String err = error;
            runOnUiThread(() -> {
                busy = false;
                if (result == null) {
                    customStatus.setText(getString(R.string.custom_fail, getString(R.string.custom_build), err));
                    return;
                }
                replaceCustom(result);
                selected = result;
                hideCheck();
                customStatus.setText(getString(R.string.custom_done, result.lengthLabel(),
                        result.uninterruptedMeters() / 1000.0));
                render();
            });
        }, "osrm-route").start();
    }

    /** 목록 맨 앞의 내 경로를 바꾼다(없으면 넣는다). */
    private void replaceCustom(RouteScenario r) {
        RouteScenarios.setCustom(r);
        boolean wasSelected = selected != null && selected.isCustom();
        if (!routes.isEmpty() && routes.get(0).isCustom()) routes.set(0, r);
        else routes.add(0, r);
        if (wasSelected) selected = r;
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(customQuery.getWindowToken(), 0);
    }

    private LinearLayout.LayoutParams matchWrap(int topMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = topMargin;
        return lp;
    }

    // ---------- 그리기 ----------

    private void render() {
        renderRoutes();
        renderPresets();
        renderCustom();
        renderPreview();
    }

    private void renderRoutes() {
        routeList.removeAllViews();
        if (routes.isEmpty()) {
            routeList.addView(text(getString(R.string.route_none), R.dimen.text_body, R.color.text_secondary, false));
            startButton.setEnabled(false);
            return;
        }
        String group = null;
        for (RouteScenario r : routes) {
            if (!r.group.equals(group)) { // 묶음 제목: 한 번에 보기 / 기능별 짧은 경로
                group = r.group;
                int headerRes = r.isCustom() ? R.string.route_group_custom
                        : r.isFeatureRoute() ? R.string.route_group_feature : R.string.route_group_full;
                TextView header = text(getString(headerRes), R.dimen.text_label, R.color.text_secondary, true);
                header.setPadding(0, px(R.dimen.gap) / 2, 0, px(R.dimen.gap) / 2);
                routeList.addView(header);
            }
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundResource(R.drawable.bg_card);
            int pad = px(R.dimen.card_padding);
            card.setPadding(pad, pad / 2, pad, pad / 2);
            boolean on = r == selected;
            card.addView(text((on ? "● " : "○ ") + r.title, R.dimen.text_body, R.color.text_primary, true));
            CoachTuning.Level level = levelFor(r);
            RoutePlanner.Plan plan = plan(r, CoachTuning.of(level).toConfig());
            card.addView(text(getString(R.string.route_card, r.lengthLabel(),
                    CoachTuning.duration(plan.estimatedSec * 1000L), r.situations.size()),
                    R.dimen.text_label, R.color.text_secondary, false));
            // 설명·출처는 고른 경로에만(목록을 짧게 해서 스크롤 없이 보이게)
            if (on && !r.summary.isEmpty()) card.addView(text(r.summary, R.dimen.text_label, R.color.text_secondary, false));
            if (on && !r.source.isEmpty()) card.addView(text(r.source, R.dimen.text_caption, R.color.text_secondary, false));
            if (on) card.setBackgroundTintList(ColorStateList.valueOf(color(R.color.surface_high)));
            card.setOnClickListener(v -> {
                if (checking) return;
                selected = r;
                hideCheck();
                render();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = px(R.dimen.gap) / 2;
            routeList.addView(card, lp);
        }
    }

    private void renderPresets() {
        presetRow.removeAllViews();
        for (CoachTuning.Preset p : CoachTuning.Preset.values()) {
            Button b = choiceButton(presetRow, p.label, p == preset);
            b.setOnClickListener(v -> {
                if (checking) return;
                preset = p;
                hideCheck();
                render();
            });
        }
        String desc = preset.label + ": " + preset.description;
        if (preset == CoachTuning.Preset.AUTO && selected != null) {
            desc = getString(R.string.route_auto_result, autoLevel(selected).label);
        }
        presetDesc.setText(desc);
    }

    private void renderCustom() {
        customBox.removeAllViews();
        customBox.setVisibility(preset == CoachTuning.Preset.CUSTOM ? View.VISIBLE : View.GONE);
        if (preset != CoachTuning.Preset.CUSTOM) return;
        for (CoachTuning.Item item : CoachTuning.Item.values()) {
            customBox.addView(text(item.label, R.dimen.text_label, R.color.text_primary, true));
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (CoachTuning.Level level : CoachTuning.Level.values()) {
                Button b = choiceButton(row, level.label, custom.get(item) == level);
                b.setOnClickListener(v -> {
                    custom.set(item, level);
                    render();
                });
            }
            customBox.addView(row);
            TextView explain = text(custom.explain(item), R.dimen.text_caption, R.color.text_secondary, false);
            explain.setPadding(0, 4, 0, px(R.dimen.gap));
            customBox.addView(explain);
        }
    }

    private void renderPreview() {
        previewBox.removeAllViews();
        if (selected == null) return;
        CoachTuning tuning = tuning();
        RoutePlanner.Plan plan = plan(selected, tuning.toConfig());
        boolean quiet = CoachRuntime.get(this).getMode() == CoachMode.QUIET;
        for (RoutePlanner.Check c : plan.checks) {
            boolean silent = quiet && (c.scene == DemoScene.SMOOTH || c.scene == DemoScene.TOO_SLOW);
            String mark = !c.fits ? "△ " : silent ? "– " : "○ ";
            String line = mark + c.scene.title + " — " + (c.fits ? CoachRuntime.explain(c.scene, tuning) : c.reason);
            int colorRes = c.fits && !silent ? R.color.text_primary : R.color.state_slow;
            TextView t = text(line, R.dimen.text_label, colorRes, false);
            t.setPadding(0, 4, 0, 4);
            previewBox.addView(t);
        }
        previewBox.addView(text(getString(R.string.route_estimate, CoachTuning.duration(plan.estimatedSec * 1000L),
                plan.fitCount(), plan.checks.size()), R.dimen.text_label, R.color.text_secondary, true));

        StringBuilder sb = new StringBuilder();
        if (selected.situations.contains(DemoScene.PASSING)) sb.append(getString(R.string.route_note_passing)).append('\n');
        if (quiet) sb.append(getString(R.string.route_note_quiet)).append('\n');
        sb.append(getString(R.string.route_note_traffic, Math.round(selected.assumedFlowKmh)));
        notes.setText(sb.toString());
        startButton.setEnabled(plan.fitCount() > 0);
    }

    // ---------- 기준값 ----------

    private CoachTuning.Level autoLevel(RouteScenario r) {
        return RoutePlanner.autoLevel(r);
    }

    private CoachTuning.Level levelFor(RouteScenario r) {
        switch (preset) {
            case REAL: return CoachTuning.Level.REAL;
            case NORMAL: return CoachTuning.Level.NORMAL;
            case FAST: return CoachTuning.Level.FAST;
            default: return autoLevel(r);
        }
    }

    private CoachTuning tuning() {
        if (preset == CoachTuning.Preset.CUSTOM) return custom.copy();
        return CoachTuning.of(levelFor(selected));
    }

    private String tuningLabel() {
        switch (preset) {
            case AUTO: return preset.label + "(" + autoLevel(selected).label + ")";
            case CUSTOM: return preset.label;
            default: return preset.label;
        }
    }

    private static RoutePlanner.Plan plan(RouteScenario r, CoachConfig c) {
        return RoutePlanner.plan(r.situations, c, r);
    }

    // ---------- 시작 전 실제 교통 확인 ----------

    /** 고속도로 노선 조회로 경로의 지금 흐름을 보고, 시나리오와 맞으면 바로 시작, 다르면 고르게 한다. */
    private void checkTrafficThenStart() {
        if (selected == null || checking) return;
        int assumed = (int) Math.round(selected.assumedFlowKmh);
        checkAssumed.setText(getString(R.string.check_assumed, assumed));
        String key = BuildConfig.TRAFFIC_API_KEY;
        if (key == null || key.isEmpty()) {
            showCheck(getString(R.string.check_no_key, assumed), false);
            checkBox.postDelayed(() -> start(true), OK_SHOW_MS);
            return;
        }
        checking = true;
        startButton.setEnabled(false);
        showCheck(getString(R.string.check_running), false);
        final RouteScenario route = selected;
        final CoachConfig config = tuning().toConfig();
        new Thread(() -> {
            ItsTrafficClient client = new ItsTrafficClient(key);
            RouteTrafficCheck.Result result = RouteTrafficCheck.run(route, client, client::fetch, config);
            runOnUiThread(() -> onChecked(route, result));
        }, "route-traffic-check").start();
    }

    private void onChecked(RouteScenario route, RouteTrafficCheck.Result result) {
        checking = false;
        startButton.setEnabled(true);
        if (isFinishing() || route != selected) return;
        String road = route.its != null ? route.its.keyword + " " + route.its.direction : route.title;
        if (!result.gotData()) {
            showCheck(getString(R.string.check_fail, result.firstError(), Math.round(route.assumedFlowKmh)), true);
            checkLive.setText(R.string.check_start);
            return;
        }
        if (result.suits()) {
            showCheck(getString(R.string.check_ok, road, result.summary()), false);
            checkBox.postDelayed(() -> {
                if (!isFinishing() && route == selected) start(true);
            }, OK_SHOW_MS);
            return;
        }
        StringBuilder sb = new StringBuilder(getString(R.string.check_mismatch, road, result.summary()));
        for (String p : result.problems) sb.append("\n· ").append(p);
        sb.append("\n").append(getString(R.string.check_question));
        showCheck(sb.toString(), true);
        checkLive.setText(R.string.check_live);
    }

    private void showCheck(String text, boolean choose) {
        checkBox.setVisibility(View.VISIBLE);
        checkText.setText(text);
        checkRow.setVisibility(choose ? View.VISIBLE : View.GONE);
    }

    private void hideCheck() {
        checkBox.setVisibility(View.GONE);
    }

    private void start(boolean useLive) {
        if (selected == null) return;
        CoachTuning t = tuning();
        int[] levels = new int[CoachTuning.Item.values().length];
        for (CoachTuning.Item item : CoachTuning.Item.values()) levels[item.ordinal()] = t.get(item).ordinal();
        setResult(RESULT_OK, new Intent()
                .putExtra(EXTRA_ROUTE_ID, selected.id)
                .putExtra(EXTRA_LEVELS, levels)
                .putExtra(EXTRA_LABEL, tuningLabel())
                .putExtra(EXTRA_USE_LIVE, useLive));
        finish();
    }

    /** MainActivity에서 돌려받은 값을 기준값으로. */
    public static CoachTuning tuningFrom(int[] levels) {
        CoachTuning t = CoachTuning.of(CoachTuning.Level.NORMAL);
        if (levels == null) return t;
        CoachTuning.Item[] items = CoachTuning.Item.values();
        CoachTuning.Level[] all = CoachTuning.Level.values();
        for (int i = 0; i < items.length && i < levels.length; i++) {
            if (levels[i] >= 0 && levels[i] < all.length) t.set(items[i], all[levels[i]]);
        }
        return t;
    }

    // ---------- 작은 도우미 ----------

    private Button choiceButton(LinearLayout parent, String label, boolean selectedState) {
        Button b = (Button) LayoutInflater.from(this).inflate(R.layout.item_choice_button, parent, false);
        b.setText(label);
        b.setBackgroundTintList(ColorStateList.valueOf(color(selectedState ? R.color.accent : R.color.surface_high)));
        b.setTextColor(color(selectedState ? R.color.on_accent : R.color.text_primary));
        parent.addView(b);
        return b;
    }

    private TextView text(String s, int sizeRes, int colorRes, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimension(sizeRes));
        t.setTextColor(color(colorRes));
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private int px(int dimenRes) {
        return getResources().getDimensionPixelSize(dimenRes);
    }

    private int color(int res) {
        return ContextCompat.getColor(this, res);
    }
}
