package com.giseo.flowcoach.data.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.giseo.flowcoach.data.demo.DemoScene;
import com.giseo.flowcoach.domain.CoachConfig;
import com.giseo.flowcoach.domain.CoachEngine;
import com.giseo.flowcoach.domain.CoachEvent;
import com.giseo.flowcoach.domain.CoachMode;
import com.giseo.flowcoach.domain.CoachTuning;
import com.giseo.flowcoach.domain.DrivingSnapshot;
import com.giseo.flowcoach.domain.Gear;
import com.giseo.flowcoach.domain.RoadContext;
import com.giseo.flowcoach.domain.RoadType;
import com.giseo.flowcoach.domain.TickResult;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** 실제 도로 시연: 기준값 단계, 상황 배치, 재생했을 때 실제로 안내가 나오는지. */
public class RouteDemoTest {

    private static final List<DemoScene> ALL = Arrays.asList(DemoScene.SMOOTH, DemoScene.PASSING,
            DemoScene.TOO_SLOW, DemoScene.OVER_LIMIT, DemoScene.HARD_BRAKE, DemoScene.LANE_CHANGES);

    /** 북쪽으로 곧게 뻗은 가상의 도로(km). */
    private static Polyline straight(double km) {
        List<double[]> pts = new ArrayList<>();
        int n = 50;
        for (int i = 0; i <= n; i++) pts.add(new double[]{37.30 + (km / 111.0) * i / n, 127.10});
        return new Polyline(pts);
    }

    @Test
    public void 단계는_시연값과_실사용값을_그대로_재현한다() {
        CoachConfig fast = CoachTuning.of(CoachTuning.Level.FAST).toConfig();
        CoachConfig demo = CoachConfig.demo();
        assertEquals(demo.smoothPraiseMs, fast.smoothPraiseMs);
        assertEquals(demo.passingHoldMs, fast.passingHoldMs);
        assertEquals(demo.tooSlowPersistMs, fast.tooSlowPersistMs);
        assertEquals(demo.overLimitPersistMs, fast.overLimitPersistMs);
        assertEquals(demo.repeatedBrakeCount, fast.repeatedBrakeCount);
        assertEquals(demo.globalGapMs, fast.globalGapMs);

        CoachConfig real = CoachTuning.of(CoachTuning.Level.REAL).toConfig();
        CoachConfig def = CoachConfig.defaults();
        assertEquals(def.smoothPraiseMs, real.smoothPraiseMs);
        assertEquals(def.passingHoldMs, real.passingHoldMs);
        assertEquals(def.repeatedBrakeWindowMs, real.repeatedBrakeWindowMs);
        assertEquals(def.sameTypeCooldownMs, real.sameTypeCooldownMs);
    }

    @Test
    public void 짧은_경로에서는_실사용_칭찬이_안_들어가고_이유를_알려준다() {
        CoachConfig real = CoachTuning.of(CoachTuning.Level.REAL).toConfig();
        RoutePlanner.Plan p = RoutePlanner.plan(ALL, real, 3_000, 92, 100);
        RoutePlanner.Check praise = p.checks.get(0);
        assertEquals(DemoScene.SMOOTH, praise.scene);
        assertFalse(praise.fits);
        assertTrue(praise.reason, praise.reason.contains("5분"));
    }

    @Test
    public void 자동은_다_들어가는_가장_실제에_가까운_단계() {
        assertEquals(CoachTuning.Level.REAL, RoutePlanner.autoLevel(ALL, 40_000, 92, 100));
        assertEquals(CoachTuning.Level.FAST, RoutePlanner.autoLevel(ALL, 4_000, 92, 100));
        CoachTuning.Level eleven = RoutePlanner.autoLevel(ALL, 11_300, 92, 100);
        assertTrue(eleven + "", eleven != CoachTuning.Level.REAL); // 11km에 실사용 5분 칭찬까지는 못 넣는다
    }

    /** 경로를 끝까지 재생하며 나온 안내 종류. */
    private static Set<CoachEvent.Type> play(Polyline line, CoachTuning.Level level, double flow, int limit) {
        return play(line, ALL, level, flow, limit);
    }

    private static Set<CoachEvent.Type> play(Polyline line, List<DemoScene> situations, CoachTuning.Level level,
                                             double flow, int limit) {
        CoachConfig c = CoachTuning.of(level).toConfig();
        RoutePlanner.Plan plan = RoutePlanner.plan(situations, c, line.lengthMeters(), flow, limit);
        RouteDriver driver = new RouteDriver(line, plan, c);
        CoachEngine engine = new CoachEngine(c, CoachMode.COACH);
        RoadContext road = new RoadContext(RoadType.EXPRESSWAY, limit, flow);
        Set<CoachEvent.Type> said = EnumSet.noneOf(CoachEvent.Type.class);
        long t = 0;
        double lastLat = 0;
        for (int i = 0; i < 3600 && !driver.isFinished(); i++) {
            RouteDriver.Output o = driver.step(Math.min(flow, limit), limit);
            assertTrue("앞으로만 간다", o.lat >= lastLat);
            lastLat = o.lat;
            DrivingSnapshot s = new DrivingSnapshot(t, o.speedKmh, 90, o.signal, o.pedalPercent > 0,
                    o.pedalPercent, Gear.DRIVE);
            TickResult r = engine.tick(s, road);
            if (r.message != null) said.add(r.message.type);
            t += 1000;
        }
        assertTrue("경로 끝까지 간다", driver.isFinished());
        return said;
    }

    @Test
    public void 빠르게_단계로_7km를_달리면_여섯_가지_안내가_모두_나온다() {
        Polyline line = straight(7.0);
        assertTrue(RoutePlanner.plan(ALL, CoachTuning.of(CoachTuning.Level.FAST).toConfig(),
                line.lengthMeters(), 92, 100).allFit());
        Set<CoachEvent.Type> said = play(line, CoachTuning.Level.FAST, 92, 100);
        for (DemoScene s : ALL) assertTrue(s + " 안내 없음: " + said, said.contains(s.expectedEvent));
    }

    @Test
    public void 실사용_단계로_긴_경로를_달려도_모두_나온다() {
        Polyline line = straight(30.0);
        Set<CoachEvent.Type> said = play(line, CoachTuning.Level.REAL, 92, 100);
        for (DemoScene s : ALL) assertTrue(s + " 안내 없음: " + said, said.contains(s.expectedEvent));
    }

    @Test
    public void 판교_양재_경로_파일을_읽는다() throws Exception {
        File f = find("src/main/assets/routes/gyeongbu_pangyo_yangjae.json");
        RouteScenario r = RouteScenario.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
        assertEquals("gyeongbu_pangyo_yangjae", r.id);
        assertEquals(6, r.situations.size());
        assertTrue(r.lengthKm() > 10 && r.lengthKm() < 12);
        double[] start = r.line.at(0);
        double[] end = r.line.at(r.line.lengthMeters());
        assertTrue("서울 쪽(북쪽)으로 간다", end[0] > start[0]);
        // 실제 경로 위에서 보통 단계면 여섯 상황이 다 들어간다
        CoachTuning.Level auto = RoutePlanner.autoLevel(r.situations, r.line.lengthMeters(), r.assumedFlowKmh, r.limitKmh);
        assertTrue(auto + "", auto == CoachTuning.Level.NORMAL || auto == CoachTuning.Level.FAST);
        Set<CoachEvent.Type> said = play(r.line, r.situations, auto, r.assumedFlowKmh, r.limitKmh);
        for (DemoScene s : r.situations) assertTrue(s + " 안내 없음: " + said, said.contains(s.expectedEvent));
    }

    private static RouteScenario load(String id) throws Exception {
        File f = find("src/main/assets/routes/" + id + ".json");
        return RouteScenario.parse(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void 기능별_짧은_경로_6개는_자동_기준값으로_그_안내가_나온다() throws Exception {
        String[] ids = {"gyeongbu_1_smooth", "gyeongbu_2_passing", "gyeongbu_3_too_slow",
                "gyeongbu_4_hard_brake", "gyeongbu_5_lane_changes", "gyeongbu_6_over_limit"};
        Set<DemoScene> covered = EnumSet.noneOf(DemoScene.class);
        for (String id : ids) {
            RouteScenario r = load(id);
            assertEquals(id, r.id);
            assertTrue(r.isFeatureRoute());
            assertEquals("0010", r.its.routeNo);
            assertEquals(1, r.situations.size());
            assertTrue(id + " " + r.lengthKm(), r.lengthKm() > 2.5 && r.lengthKm() < 4.5);
            CoachTuning.Level auto = RoutePlanner.autoLevel(r.situations, r.line.lengthMeters(), r.assumedFlowKmh, r.limitKmh);
            Set<CoachEvent.Type> said = play(r.line, r.situations, auto, r.assumedFlowKmh, r.limitKmh);
            DemoScene scene = r.situations.get(0);
            assertTrue(id + " (" + auto + ") 안내 없음: " + said, said.contains(scene.expectedEvent));
            covered.add(scene);
        }
        assertEquals("여섯 상황이 하나씩", 6, covered.size());
        assertEquals(1, load("gyeongbu_pangyo_yangjae").order); // 긴 경로가 목록 맨 앞
    }

    @Test
    public void 시작_전_교통_확인은_맞지_않는_상황을_알려준다() throws Exception {
        RouteScenario full = load("gyeongbu_pangyo_yangjae");
        CoachConfig c = CoachTuning.of(CoachTuning.Level.NORMAL).toConfig();
        List<RouteTrafficCheck.Point> good = Arrays.asList(new RouteTrafficCheck.Point("출발", 87, null),
                new RouteTrafficCheck.Point("중간", 85, null), new RouteTrafficCheck.Point("도착", 84, null));
        RouteTrafficCheck.Result ok = RouteTrafficCheck.evaluate(full, good, c, 3);
        assertTrue(ok.problems.toString(), ok.suits());
        assertEquals("출발 87 · 중간 85 · 도착 84km/h", ok.summary());

        List<RouteTrafficCheck.Point> slow = Arrays.asList(new RouteTrafficCheck.Point("출발", 87, null),
                new RouteTrafficCheck.Point("도착", 62, null));
        RouteTrafficCheck.Result passing = RouteTrafficCheck.evaluate(full, slow, c, 2);
        assertFalse(passing.suits());
        assertEquals(1, passing.problems.size());
        assertTrue(passing.problems.get(0), passing.problems.get(0).startsWith(DemoScene.PASSING.title));

        List<RouteTrafficCheck.Point> jam = Arrays.asList(new RouteTrafficCheck.Point("구간", 25, null));
        RouteTrafficCheck.Result jammed = RouteTrafficCheck.evaluate(full, jam, c, 1);
        assertEquals("과속만 빼고 모두 안내 불가", full.situations.size() - 1, jammed.problems.size());

        List<RouteTrafficCheck.Point> none = Arrays.asList(new RouteTrafficCheck.Point("구간", Double.NaN, "HTTP 500"));
        RouteTrafficCheck.Result failed = RouteTrafficCheck.evaluate(full, none, c, 1);
        assertFalse(failed.gotData());
        assertEquals("HTTP 500", failed.firstError());
    }

    @Test
    public void 교통_확인_지점은_경로_길이에_따라_1에서_3곳() throws Exception {
        List<String> labels = new ArrayList<>();
        assertEquals(3, RouteTrafficCheck.places(load("gyeongbu_pangyo_yangjae"), labels).size());
        labels.clear();
        assertEquals(2, RouteTrafficCheck.places(load("gyeongbu_2_passing"), labels).size());
        assertEquals(Arrays.asList("출발", "도착"), labels);
    }

    static File find(String relative) {
        File dir = new File(System.getProperty("user.dir")).getAbsoluteFile();
        for (int i = 0; i < 6 && dir != null; i++, dir = dir.getParentFile()) {
            for (String prefix : new String[]{"", "app/", "flowcoach/app/"}) {
                File f = new File(dir, prefix + relative);
                if (f.exists()) return f;
            }
        }
        throw new AssertionError("파일 없음: " + relative + " (" + System.getProperty("user.dir") + ")");
    }
}
