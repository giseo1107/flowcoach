package com.giseo.flowcoach.data.traffic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.giseo.flowcoach.data.location.GeoPosition;
import com.giseo.flowcoach.domain.RoadContext;
import com.giseo.flowcoach.domain.RoadType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 위치 기반 도로 선택 + 호출 아끼기. */
public class RoadSelectionTest {

    // ---------- 노선표·상하행 ----------

    @Test
    public void 도로_이름으로_노선번호() {
        assertEquals("0010", ExpresswayRoutes.find("경부고속도로").routeNo());
        assertEquals("중부내륙", ExpresswayRoutes.find("중부내륙고속도로").keyword); // "중부"로 잘못 잡지 않음
        assertEquals(35, ExpresswayRoutes.find("중부고속도로").number);
        assertEquals(110, ExpresswayRoutes.find("제2경인고속도로").number);
        assertEquals(120, ExpresswayRoutes.find("경인고속도로").number);
        assertEquals("1000", ExpresswayRoutes.find("수도권제1순환고속도로").routeNo());
        assertNull(ExpresswayRoutes.find("대왕판교로"));
        assertNull(ExpresswayRoutes.find("경부로")); // 이름에 '경부'만 있는 일반도로
        assertTrue(ExpresswayRoutes.isExpressway("용인서울고속도로")); // 표에 없어도 이름으로
    }

    @Test
    public void 서울_방향이면_상행() {
        ExpresswayRoutes.Route gyeongbu = ExpresswayRoutes.find("경부고속도로");
        assertEquals("상행", ExpresswayRoutes.direction(gyeongbu, 37.40, 127.10, 0));     // 판교, 북쪽
        assertEquals("하행", ExpresswayRoutes.direction(gyeongbu, 37.40, 127.10, 170));   // 판교, 남쪽
        assertNull(ExpresswayRoutes.direction(gyeongbu, 37.40, 127.10, 60));              // 서울과 직각 → 모름
        assertNull(ExpresswayRoutes.direction(gyeongbu, 37.40, 127.10, Double.NaN));      // 방향 모름

        ExpresswayRoutes.Route yeongdong = ExpresswayRoutes.find("영동고속도로");
        assertEquals("상행", ExpresswayRoutes.direction(yeongdong, 37.25, 127.20, 270));  // 용인, 인천 방향
        assertEquals("하행", ExpresswayRoutes.direction(yeongdong, 37.25, 127.20, 90));   // 용인, 강릉 방향

        assertNull(ExpresswayRoutes.direction(gyeongbu, 37.55, 126.99, 0)); // 서울 안
        assertNull(ExpresswayRoutes.direction(ExpresswayRoutes.find("수도권제1순환고속도로"), 37.40, 127.10, 0)); // 순환선
    }

    // ---------- 도로 고르기 ----------

    private static List<LinkSpeed> nearPangyo() {
        List<LinkSpeed> l = new ArrayList<>();
        for (int i = 0; i < 4; i++) l.add(new LinkSpeed("경부고속도로", "e" + i, 80 + i, ""));
        for (int i = 0; i < 5; i++) l.add(new LinkSpeed("대왕판교로", "u" + i, 40 + i, ""));
        l.add(new LinkSpeed("판교로", "p0", 35, ""));
        l.add(new LinkSpeed("", "x", 50, ""));         // 이름 없음 → 제외
        l.add(new LinkSpeed("판교역로", "z", 0, ""));   // 속도 0(수집 안 됨) → 제외
        return l;
    }

    @Test
    public void 빠르게_달리면_고속도로를_고름() {
        RoadMatcher.Candidate c = RoadMatcher.pick(nearPangyo(), null, 95, 0);
        assertEquals("경부고속도로", c.roadName);
        assertTrue(c.expressway);
        assertEquals("0010", c.route.routeNo());
        // 내비 제한속도 100이면 정지 중이어도 고속도로
        assertEquals("경부고속도로", RoadMatcher.pick(nearPangyo(), null, 0, 100).roadName);
    }

    @Test
    public void 느리면_구간이_가장_많은_도로() {
        RoadMatcher.Candidate c = RoadMatcher.pick(nearPangyo(), null, 20, 0);
        assertEquals("대왕판교로", c.roadName);
        assertFalse(c.expressway);
        assertEquals(3, RoadMatcher.candidates(nearPangyo()).size());
    }

    @Test
    public void 이전_도로가_주변에_있으면_유지() {
        assertEquals("판교로", RoadMatcher.pick(nearPangyo(), "판교로", 20, 0).roadName);
        // 하지만 고속 주행인데 이전 도로가 일반도로면 고속도로로 바꾼다
        assertEquals("경부고속도로", RoadMatcher.pick(nearPangyo(), "판교로", 100, 0).roadName);
        assertNull(RoadMatcher.pick(new ArrayList<LinkSpeed>(), null, 100, 0));
    }

    // ---------- 저장소: 호출 순서와 횟수 ----------

    private static final String ALL_JSON = json(
            item("경부고속도로", "", "82"), item("경부고속도로", "", "84"), item("경부고속도로", "", "20"),
            item("경부고속도로", "", "86"), item("대왕판교로", "", "41"), item("대왕판교로", "", "45"),
            item("대왕판교로", "", "43"), item("대왕판교로", "", "47"), item("대왕판교로", "", "40"));
    private static final String EX_JSON = json(
            item("경부고속도로", "상행", "93.9"), item("경부고속도로", "상행", "90.9"), item("경부고속도로", "상행", "74.3"),
            item("경부고속도로", "하행", "18.0"), item("경부고속도로", "하행", "79.5"));
    private static final String EMPTY_JSON = json();

    private static String item(String road, String dir, String speed) {
        return "{\"roadName\":\"" + road + "\",\"roadDrcType\":\"" + dir + "\",\"speed\":\"" + speed + "\"}";
    }

    private static String json(String... items) {
        return "{\"header\":{\"resultCode\":0,\"resultMsg\":\"SUCCESS\"},\"body\":{\"items\":["
                + String.join(",", items) + "]}}";
    }

    /** URL을 기록하고 type에 맞는 가짜 응답을 준다. */
    private static final class FakeIts implements TrafficFlowRepository.Fetcher {
        final List<String> urls = new ArrayList<>();
        String all = ALL_JSON;
        String ex = EX_JSON;

        @Override
        public String fetch(String url) {
            urls.add(url);
            return url.contains("type=ex") ? ex : all;
        }

        long count(String part) {
            return urls.stream().filter(u -> u.contains(part)).count();
        }
    }

    private static final RoadContext FALLBACK = new RoadContext(RoadType.MOTORWAY, 90, 88);
    private static final long MIN = 60_000;

    private static TrafficFlowRepository repo(FakeIts its) {
        return new TrafficFlowRepository("KEY", 10 * MIN, its, Runnable::run); // 바로 실행(동기)
    }

    private static TrafficFlowRepository.Query at(double lat, double lon, double heading, double kmh) {
        return TrafficFlowRepository.Query.located(new GeoPosition(lat, lon, heading, "내비", 0), kmh, 0);
    }

    @Test
    public void 고속도로_주행_중이면_내_방향_구간만() {
        FakeIts its = new FakeIts();
        TrafficFlowRepository r = repo(its);
        r.maybeRefresh(at(37.40, 127.10, 0, 95), 0);

        assertEquals(2, its.urls.size());                     // 주변 도로 찾기 + 노선별 조회
        assertTrue(its.urls.get(0).contains("type=all"));
        assertTrue(its.urls.get(1).contains("type=ex&routeNo=0010"));
        RoadContext road = r.roadOr(FALLBACK, 1);
        assertEquals(RoadType.EXPRESSWAY, road.roadType);
        assertEquals(90.9, road.flowSpeedKmh, 0.001);          // 상행 3개 구간 중앙값(하행 정체 18은 안 섞임)
        assertEquals("경부고속도로 상행", r.currentRoad());
        assertTrue(r.status(), r.status().contains("경부고속도로 상행 91km/h"));
    }

    @Test
    public void 호출을_아낀다() {
        FakeIts its = new FakeIts();
        TrafficFlowRepository r = repo(its);
        TrafficFlowRepository.Query q = at(37.40, 127.10, 0, 95);
        r.maybeRefresh(q, 0);
        assertEquals(2, its.urls.size());

        for (long t = 1_000; t < 10 * MIN; t += 1_000) r.maybeRefresh(q, t); // 1초마다 불러도
        assertEquals(2, its.urls.size());                                     // 10분 안에는 다시 안 부름

        r.maybeRefresh(q, 10 * MIN + 1_000);  // 10분 지남 → 이동이 없으니 도로 찾기는 생략, 노선별 조회만
        assertEquals(3, its.urls.size());
        assertEquals(1, its.count("type=all"));

        r.maybeRefresh(at(37.43, 127.10, 0, 95), 12 * MIN); // 북쪽으로 약 3.3km 이동 → 주변 도로 다시 찾기
        assertEquals(2, its.count("type=all"));
    }

    @Test
    public void 정지_중엔_일반도로_속도가_오르면_고속도로로() {
        FakeIts its = new FakeIts();
        TrafficFlowRepository r = repo(its);
        r.maybeRefresh(at(37.40, 127.10, 0, 0), 0);
        assertEquals(1, its.urls.size());                      // 일반도로는 주변 조회 하나로 끝
        assertEquals("대왕판교로", r.currentRoad());
        assertEquals(43, r.roadOr(FALLBACK, 1).flowSpeedKmh, 0.001);
        assertTrue(r.status(), r.status().contains("대왕판교로(양방향)"));

        r.maybeRefresh(at(37.40, 127.10, 0, 95), 30_000);      // 1분이 안 지났으면 안 부름
        assertEquals(1, its.urls.size());
        r.maybeRefresh(at(37.40, 127.10, 0, 95), MIN + 1);     // 도로가 바뀌었으니 노선별 조회 하나만
        assertEquals(2, its.urls.size());
        assertEquals("경부고속도로 상행", r.currentRoad());
        assertEquals(90.9, r.roadOr(FALLBACK, MIN + 2).flowSpeedKmh, 0.001);
    }

    @Test
    public void 방향을_모르면_양방향_값() {
        FakeIts its = new FakeIts();
        TrafficFlowRepository r = repo(its);
        r.maybeRefresh(at(37.40, 127.10, Double.NaN, 95), 0);
        assertEquals(1, its.urls.size());                     // 방향을 모르면 노선별 조회는 하지 않는다
        assertEquals(83, r.roadOr(FALLBACK, 1).flowSpeedKmh, 0.001); // 경부 4개(20,82,84,86) 중앙값
        assertEquals(RoadType.EXPRESSWAY, r.roadOr(FALLBACK, 1).roadType);
    }

    @Test
    public void 노선번호가_틀려서_결과가_없으면_양방향_값으로() {
        FakeIts its = new FakeIts();
        its.ex = EMPTY_JSON;
        TrafficFlowRepository r = repo(its);
        r.maybeRefresh(at(37.40, 127.10, 0, 95), 0);
        assertEquals(83, r.roadOr(FALLBACK, 1).flowSpeedKmh, 0.001);
        assertTrue(r.status(), r.status().contains("양방향"));
    }

    @Test
    public void 주변에_도로가_없으면_이동_전까지_다시_안_부름() {
        FakeIts its = new FakeIts();
        its.all = EMPTY_JSON;
        TrafficFlowRepository r = repo(its);
        TrafficFlowRepository.Query q = at(37.40, 127.10, 0, 95);
        r.maybeRefresh(q, 0);
        r.maybeRefresh(q, 5 * MIN);
        r.maybeRefresh(q, 30 * MIN);
        assertEquals(2, its.urls.size()); // 400m에서 없으면 1km로 한 번 더, 그 뒤로는 이동 전까지 안 부름
        assertTrue(its.urls.get(1).contains("minY=37.390000"));
        assertSame(FALLBACK, r.roadOr(FALLBACK, 30 * MIN));
        assertTrue(r.status(), r.status().contains("주변 1km"));
    }

    @Test
    public void 위치를_모르면_시연_도로_고정_조회() {
        FakeIts its = new FakeIts();
        TrafficFlowRepository r = repo(its);
        r.maybeRefresh(TrafficFlowRepository.Query.fixed(37.40, 127.10, "0010", "경부", "상행"), 0);
        assertEquals(Arrays.asList(1L, 0L), Arrays.asList(its.count("routeNo=0010"), its.count("type=all")));
        assertEquals(90.9, r.roadOr(FALLBACK, 1).flowSpeedKmh, 0.001);
    }

    @Test
    public void 키가_없으면_부르지_않음() {
        FakeIts its = new FakeIts();
        TrafficFlowRepository r = new TrafficFlowRepository("", 10 * MIN, its, Runnable::run);
        r.maybeRefresh(at(37.40, 127.10, 0, 95), 0);
        assertTrue(its.urls.isEmpty());
        assertFalse(r.isEnabled());
        assertNotNull(r.status());
    }

    // ---------- 경로 위를 달릴 때(실제 도로 시연): 지금 구간의 도로를 먼저 ----------

    @Test
    public void 시내_구간이면_옆_고속도로를_고르지_않는다() {
        List<LinkSpeed> l = nearPangyo();
        for (int i = 0; i < 6; i++) l.add(new LinkSpeed("경부고속도로", "x" + i, 85, "")); // 고속도로 링크가 더 많음
        assertEquals("경부고속도로", RoadMatcher.pick(l, null, 50, 50).roadName);          // 힌트 없으면 고속도로
        assertEquals("대왕판교로", RoadMatcher.pick(l, null, 50, 50, null, false).roadName);
        assertEquals("대왕판교로", RoadMatcher.pick(l, "경부고속도로", 50, 50, "", false).roadName); // 이전 값도 무시
    }

    @Test
    public void 구간_이름이_있으면_그_도로() {
        List<LinkSpeed> l = nearPangyo();
        assertEquals("판교로", RoadMatcher.pick(l, null, 40, 50, "판교로", false).roadName);   // "대왕판교로"가 아니라 같은 이름
        assertEquals("경부고속도로", RoadMatcher.pick(l, null, 90, 100, "경부 고속도로", true).roadName);
        assertEquals("대왕판교로", RoadMatcher.pick(l, null, 40, 50, "없는길", false).roadName); // 못 찾으면 고속도로 뺀 기본 규칙
    }

    @Test
    public void 고속도로를_빼면_후보가_없을_수_있다() {
        List<LinkSpeed> l = new ArrayList<>();
        l.add(new LinkSpeed("경부고속도로", "e0", 90, ""));
        assertNull(RoadMatcher.pick(l, null, 40, 50, "판교역로", false));
    }
}
