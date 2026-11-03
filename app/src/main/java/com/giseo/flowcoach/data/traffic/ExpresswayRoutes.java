package com.giseo.flowcoach.data.traffic;

import com.giseo.flowcoach.data.location.GeoMath;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 고속도로 이름 → ITS 노선번호(routeNo), 진행 방향 → 상행/하행.
 *
 * 노선번호: ITS는 고속도로 노선번호를 4자리로 쓴다(경부 1번 → "0010", 실제 호출로 확인).
 *   나머지는 같은 규칙(번호×10, 4자리)으로 만든 값이라, 틀리면 조회 결과가 비고
 *   TrafficFlowRepository가 방향 구분 없는 값으로 대신한다.
 *
 * 상행/하행: 국내 고속도로는 관례상 서울 방향을 상행이라 부른다.
 *   그래서 진행 방향이 서울 쪽을 향하면 상행, 반대면 하행으로 본다(경부: 북쪽 = 상행).
 *   서울 가까이(환상선·도시 안)나 서울과 직각으로 달릴 때는 판단하지 않는다(null).
 */
public final class ExpresswayRoutes {

    /** 서울시청 */
    static final double SEOUL_LAT = 37.5665;
    static final double SEOUL_LON = 126.9780;
    /** 서울 중심에서 이 거리 안이면 상·하행을 판단하지 않는다(m). */
    static final double NEAR_SEOUL_M = 12_000;
    /** 서울 방향과의 각도가 이 안이면 상행, (180-이 값) 밖이면 하행. 그 사이는 모름. */
    static final double DIRECTION_TOLERANCE_DEG = 70;

    public static final String UP = "상행";
    public static final String DOWN = "하행";

    /** 노선 정보. ring=true면 순환선이라 상·하행을 판단하지 않는다. */
    public static final class Route {
        public final String keyword;
        public final int number;
        public final boolean ring;
        /** 실제 API 호출로 노선번호를 확인했는지. */
        public final boolean verified;

        Route(String keyword, int number, boolean ring, boolean verified) {
            this.keyword = keyword;
            this.number = number;
            this.ring = ring;
            this.verified = verified;
        }

        /** ITS routeNo 형식(예: 1 → "0010"). */
        public String routeNo() {
            return String.format(Locale.ROOT, "%04d", number * 10);
        }
    }

    // 긴 이름부터 비교해야 "중부내륙"이 "중부"로 잘못 잡히지 않는다(아래에서 정렬).
    private static final List<Route> ROUTES = Arrays.asList(
            new Route("경부", 1, false, true),
            new Route("남해", 10, false, false),
            new Route("광주대구", 12, false, false),
            new Route("서해안", 15, false, false),
            new Route("울산", 16, false, false),
            new Route("평택시흥", 17, false, false),
            new Route("호남", 25, false, false),
            new Route("순천완주", 27, false, false),
            new Route("세종포천", 29, false, false),
            new Route("당진영덕", 30, false, false),
            new Route("통영대전", 35, false, false),
            new Route("중부내륙", 45, false, false),
            new Route("제2중부", 37, false, false),
            new Route("중부", 35, false, false),
            new Route("평택제천", 40, false, false),
            new Route("영동", 50, false, false),
            new Route("중앙", 55, false, false),
            new Route("서울양양", 60, false, false),
            new Route("동해", 65, false, false),
            new Route("수도권제1순환", 100, true, false),
            new Route("제2경인", 110, false, false),
            new Route("경인", 120, false, false));

    static {
        ROUTES.sort(Comparator.comparingInt((Route r) -> r.keyword.length()).reversed());
    }

    private ExpresswayRoutes() {
    }

    /** 도로 이름으로 노선을 찾는다(예: "경부고속도로" → 경부). 없으면 null. */
    public static Route find(String roadName) {
        if (roadName == null || roadName.isEmpty()) return null;
        for (Route r : ROUTES) {
            if (roadName.contains(r.keyword) && (roadName.contains("고속") || roadName.endsWith("선"))) return r;
        }
        return null;
    }

    public static boolean isExpressway(String roadName) {
        return roadName != null && (roadName.contains("고속도로") || find(roadName) != null);
    }

    /**
     * 위치와 진행 방향으로 상행/하행을 고른다. 판단할 수 없으면 null.
     */
    public static String direction(Route route, double lat, double lon, double headingDeg) {
        if (route == null || route.ring || Double.isNaN(headingDeg)) return null;
        if (GeoMath.distanceMeters(lat, lon, SEOUL_LAT, SEOUL_LON) < NEAR_SEOUL_M) return null;
        double toSeoul = GeoMath.bearingDeg(lat, lon, SEOUL_LAT, SEOUL_LON);
        double diff = GeoMath.angleDiff(headingDeg, toSeoul);
        if (diff <= DIRECTION_TOLERANCE_DEG) return UP;
        if (diff >= 180 - DIRECTION_TOLERANCE_DEG) return DOWN;
        return null;
    }
}
