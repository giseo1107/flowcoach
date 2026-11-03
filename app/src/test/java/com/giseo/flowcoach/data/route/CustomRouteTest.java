package com.giseo.flowcoach.data.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.giseo.flowcoach.data.demo.DemoScene;
import com.giseo.flowcoach.domain.CoachConfig;
import com.giseo.flowcoach.domain.CoachTuning;
import com.giseo.flowcoach.domain.RoadType;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** '내 경로': 카카오 장소 검색 응답, OSRM 경로 응답 → 도로 구간, 구간별 상황 배치. */
public class CustomRouteTest {

    @Test
    public void 카카오_장소_검색_응답을_읽는다() throws Exception {
        String json = "{\"documents\":[{\"place_name\":\"판교역 신분당선\",\"address_name\":\"경기 성남시 분당구 삼평동 646\","
                + "\"road_address_name\":\"경기 성남시 분당구 판교역로 160\",\"x\":\"127.111202\",\"y\":\"37.394726\","
                + "\"category_name\":\"교통,수송 > 지하철,전철\",\"distance\":\"1234\"},"
                + "{\"place_name\":\"좌표없음\",\"x\":\"\",\"y\":\"\"}],\"meta\":{\"total_count\":2}}";
        List<KakaoPlaceSearch.Place> places = KakaoPlaceSearch.parse(json);
        assertEquals(1, places.size());
        KakaoPlaceSearch.Place p = places.get(0);
        assertEquals("판교역 신분당선", p.name);
        assertEquals(37.394726, p.lat, 1e-6);
        assertEquals(127.111202, p.lon, 1e-6);
        assertEquals("판교역 신분당선 · 경기 성남시 분당구 판교역로 160 · 1.2km", p.label());
        assertTrue(KakaoPlaceSearch.buildUrl("판교역", 37.4, 127.1).contains("query=%ED%8C%90%EA%B5%90%EC%97%AD"));
    }

    /** 시내 1km → 경부고속도로 북쪽 6km → 시내 0.5km 인 OSRM 응답. */
    private static String osrmJson() {
        StringBuilder ex = new StringBuilder();
        for (int i = 0; i <= 12; i++) {
            if (i > 0) ex.append(',');
            ex.append('[').append(127.10).append(',').append(37.309 + 0.0045 * i).append(']');
        }
        return "{\"code\":\"Ok\",\"routes\":[{\"legs\":[{\"steps\":["
                + "{\"name\":\"판교역로\",\"ref\":\"\",\"geometry\":{\"coordinates\":[[127.10,37.300],[127.10,37.309]]}},"
                + "{\"name\":\"\",\"geometry\":{\"coordinates\":[[127.10,37.309],[127.10,37.309]]}},"
                + "{\"name\":\"경부고속도로\",\"ref\":\"1\",\"geometry\":{\"coordinates\":[" + ex + "]}},"
                + "{\"name\":\"양재대로\",\"ref\":\"47\",\"geometry\":{\"coordinates\":[[127.10,37.363],[127.10,37.3675]]}},"
                + "{\"name\":\"\",\"geometry\":{\"coordinates\":[[127.10,37.3675]]}}"
                + "]}]}]}";
    }

    @Test
    public void OSRM_경로를_도로_구간으로_나눈다() {
        OsrmRouter.Route route = OsrmRouter.parse(osrmJson());
        assertEquals(3, route.spans.size());
        assertEquals(RoadType.URBAN, route.spans.get(0).type);
        assertEquals(RoadType.EXPRESSWAY, route.spans.get(1).type);
        assertEquals("경부고속도로", route.spans.get(1).name);
        assertEquals(100, route.spans.get(1).limitKmh);
        assertEquals(RoadType.URBAN, route.spans.get(2).type);
        assertEquals(route.meters, route.spans.get(2).endM, 1e-6);
        assertEquals(6.0, route.spans.get(1).lengthM() / 1000.0, 0.05);

        RouteScenario r = OsrmRouter.toScenario(route, "판교", "양재",
                Arrays.asList(DemoScene.PASSING, DemoScene.OVER_LIMIT));
        assertTrue(r.isCustom());
        assertEquals(route.meters, r.line.lengthMeters(), 1e-6);
        assertNotNull("북쪽(서울 쪽)으로 가는 경부 = 상행", r.its);
        assertEquals("0010", r.its.routeNo);
        assertEquals("상행", r.its.direction);
        assertEquals(RoadType.EXPRESSWAY, r.spanAt(3000).type);
        assertEquals(RoadType.URBAN, r.spanAt(500).type);
    }

    @Test
    public void 상황은_고속도로_구간_안에만_놓인다() {
        RouteScenario r = OsrmRouter.toScenario(OsrmRouter.parse(osrmJson()), "판교", "양재",
                Arrays.asList(DemoScene.PASSING, DemoScene.OVER_LIMIT));
        CoachConfig c = CoachTuning.of(CoachTuning.Level.FAST).toConfig();
        RoutePlanner.Plan plan = RoutePlanner.plan(r.situations, c, r);
        assertTrue(plan.allFit());
        RoadSpan ex = r.spans.get(1);
        for (RoutePlanner.Step s : plan.steps) {
            assertTrue(s.scene + " 시작 " + s.startMeters, s.startMeters >= ex.startM && s.startMeters < ex.endM);
            assertEquals("경부고속도로", s.roadName);
        }
    }

    @Test
    public void 시내만_있으면_이유를_알려준다() {
        RoadSpan city = new RoadSpan(0, 5000, RoadType.URBAN, 50, 42, "판교역로");
        CoachConfig c = CoachTuning.of(CoachTuning.Level.FAST).toConfig();
        RoutePlanner.Plan plan = RoutePlanner.plan(Arrays.asList(DemoScene.SMOOTH), c, 5000, Arrays.asList(city));
        assertFalse(plan.checks.get(0).fits);
        assertTrue(plan.checks.get(0).reason, plan.checks.get(0).reason.contains("고속도로·자동차전용도로 구간이 없어서"));
    }

    @Test
    public void 추월은_흐름_80_미만_구간에는_놓지_않는다() {
        RoadSpan motorway = new RoadSpan(0, 8000, RoadType.MOTORWAY, 80, 70, "분당내곡간도시고속화도로");
        CoachConfig c = CoachTuning.of(CoachTuning.Level.FAST).toConfig();
        RoutePlanner.Plan plan = RoutePlanner.plan(Arrays.asList(DemoScene.PASSING, DemoScene.OVER_LIMIT), c, 8000,
                Arrays.asList(motorway));
        assertFalse(plan.checks.get(0).fits);
        assertTrue(plan.checks.get(0).reason, plan.checks.get(0).reason.contains("추월차로"));
        assertTrue(plan.checks.get(1).fits);
    }

    @Test
    public void 시내에서_고속도로로_들어가면_속도를_올린_뒤_시작한다() {
        RoadSpan city = new RoadSpan(0, 1000, RoadType.URBAN, 50, 42, "창업로");
        RoadSpan ex = new RoadSpan(1000, 9000, RoadType.EXPRESSWAY, 100, 92, "경부고속도로");
        CoachConfig c = CoachTuning.of(CoachTuning.Level.FAST).toConfig();
        RoutePlanner.Plan plan = RoutePlanner.plan(Arrays.asList(DemoScene.PASSING), c, 9000, Arrays.asList(city, ex));
        assertTrue(plan.allFit());
        double lead = RoutePlanner.ENTRY_SEC * (92 - 2) / 3.6; // 진입 후 25초 정도 달린 거리
        assertTrue("시작 " + plan.steps.get(0).startMeters, plan.steps.get(0).startMeters >= 1000 + lead - 1);
        // 앞 구간이 없으면(빠른 구간끼리 이어지거나 경로 처음) 미루지 않는다
        assertEquals(1000, RoutePlanner.entryStart(Arrays.asList(ex), ex), 1e-9);
    }

    @Test
    public void 흐름에_맞음은_출발이나_앞_상황에_붙는다() {
        RoadSpan ex = new RoadSpan(0, 11300, RoadType.EXPRESSWAY, 100, 92, "경부고속도로");
        CoachConfig c = CoachTuning.of(CoachTuning.Level.NORMAL).toConfig();
        // 첫 상황이면 출발부터(엔진은 출발부터 흐름에 맞춘 시간을 세므로 칭찬 시점이 상황과 맞는다)
        RoutePlanner.Plan a = RoutePlanner.plan(Arrays.asList(DemoScene.SMOOTH, DemoScene.PASSING), c, 11300,
                Arrays.asList(ex));
        assertTrue(a.allFit());
        assertEquals(0, a.steps.get(0).startMeters, 1e-9);
        assertTrue(a.steps.get(1).startMeters > 0);
        // 다른 상황 뒤면 그 상황이 끝나자마자(사이를 벌리지 않는다)
        RoutePlanner.Plan b = RoutePlanner.plan(Arrays.asList(DemoScene.TOO_SLOW, DemoScene.SMOOTH), c, 11300,
                Arrays.asList(ex));
        assertTrue(b.allFit());
        RoutePlanner.Step slow = b.steps.get(0);
        double slowEnd = slow.startMeters + slow.durationSec
                * RoutePlanner.speedFor(DemoScene.TOO_SLOW, c, ex.expectedRecKmh(), ex.limitKmh) / 3.6;
        assertEquals(slowEnd, b.steps.get(1).startMeters, 1.0);
    }
}
