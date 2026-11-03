package com.giseo.flowcoach.data.route;

import com.giseo.flowcoach.data.demo.DemoScene;
import com.giseo.flowcoach.data.location.GeoMath;
import com.giseo.flowcoach.data.traffic.ExpresswayRoutes;
import com.giseo.flowcoach.data.traffic.MiniJson;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 출발지→목적지 실제 경로 좌표와 도로 이름을 받는다(OpenStreetMap 기반 OSRM 공개 서버, 키 없음).
 * 도로 이름으로 구간(RoadSpan: 고속도로·자동차전용도로·시내)을 나눠 '내 경로' 시연을 만든다.
 * 공개 데모 서버라 느리거나 막힐 수 있어서, 실패하면 화면에서 이유를 보여주고 미리 넣은 경로를 쓰게 한다.
 */
public final class OsrmRouter {

    public static final String ENDPOINT = "https://router.project-osrm.org/route/v1/driving/";
    public static final String CUSTOM_ID = "custom";

    /** 받은 경로. */
    public static final class Route {
        public final List<double[]> points;
        public final List<RoadSpan> spans;
        public final double meters;

        Route(List<double[]> points, List<RoadSpan> spans, double meters) {
            this.points = points;
            this.spans = spans;
            this.meters = meters;
        }
    }

    private OsrmRouter() {
    }

    /** 네트워크 스레드에서. */
    public static Route route(double fromLat, double fromLon, double toLat, double toLon) throws Exception {
        return parse(Http.get(buildUrl(fromLat, fromLon, toLat, toLon), null));
    }

    static String buildUrl(double fromLat, double fromLon, double toLat, double toLon) {
        return ENDPOINT + String.format(Locale.ROOT, "%.6f,%.6f;%.6f,%.6f", fromLon, fromLat, toLon, toLat)
                + "?overview=false&steps=true&geometries=geojson";
    }

    /** OSRM 응답 → 좌표 + 도로 구간. 같은 종류가 이어지는 구간은 합친다. */
    @SuppressWarnings("unchecked")
    static Route parse(String json) {
        Map<String, Object> root = (Map<String, Object>) MiniJson.parse(json);
        if (!"Ok".equals(root.get("code"))) {
            throw new IllegalStateException("경로를 찾지 못했어요(" + root.get("code") + ")");
        }
        List<Object> routes = (List<Object>) root.get("routes");
        Map<String, Object> leg = (Map<String, Object>) ((List<Object>) ((Map<String, Object>) routes.get(0)).get("legs")).get(0);
        List<double[]> points = new ArrayList<>();
        List<RoadSpan> spans = new ArrayList<>();
        double cum = 0;
        for (Object o : (List<Object>) leg.get("steps")) {
            Map<String, Object> step = (Map<String, Object>) o;
            List<Object> coords = (List<Object>) ((Map<String, Object>) step.get("geometry")).get("coordinates");
            double start = cum;
            for (Object c : coords) {
                List<Object> xy = (List<Object>) c;
                double lon = ((Number) xy.get(0)).doubleValue();
                double lat = ((Number) xy.get(1)).doubleValue();
                if (!points.isEmpty()) {
                    double[] last = points.get(points.size() - 1);
                    double d = GeoMath.distanceMeters(last[0], last[1], lat, lon);
                    if (d < 0.5) continue; // 단계 경계에서 겹치는 점
                    cum += d;
                }
                points.add(new double[]{lat, lon});
            }
            if (cum <= start) continue; // 길이가 없는 단계(도착 등)
            String name = step.get("name") == null ? "" : step.get("name").toString();
            String ref = step.get("ref") == null ? "" : step.get("ref").toString();
            add(spans, RoadSpan.fromRoadName(start, cum, name, ref));
        }
        if (points.size() < 2) throw new IllegalStateException("경로 좌표가 부족해요");
        return new Route(points, spans, cum);
    }

    /** 같은 종류·제한속도가 이어지면 하나로 합친다(이름은 더 긴 쪽). */
    private static void add(List<RoadSpan> spans, RoadSpan s) {
        if (!spans.isEmpty()) {
            RoadSpan last = spans.get(spans.size() - 1);
            if (last.type == s.type && last.limitKmh == s.limitKmh) {
                String name = last.lengthM() >= s.lengthM() || s.name.isEmpty() ? last.name : s.name;
                spans.set(spans.size() - 1, new RoadSpan(last.startM, s.endM, last.type, last.limitKmh,
                        last.assumedFlowKmh, name));
                return;
            }
        }
        spans.add(s);
    }

    /**
     * 받은 경로로 '내 경로' 시나리오를 만든다.
     * @param situations 넣을 상황(이 순서로 배치)
     */
    public static RouteScenario toScenario(Route route, String fromName, String toName, List<DemoScene> situations) {
        Polyline line = new Polyline(route.points);
        // 시나리오 대표값: 상황을 둘 수 있는 가장 긴 구간(없으면 첫 구간)
        RoadSpan main = null;
        double fast = 0;
        for (RoadSpan s : route.spans) {
            if (s.allowsSituations()) {
                fast += s.lengthM();
                if (main == null || s.lengthM() > main.lengthM()) main = s;
            }
        }
        RoadSpan rep = main != null ? main : route.spans.get(0);
        RouteScenario.Its its = null;
        ExpresswayRoutes.Route ex = main != null ? ExpresswayRoutes.find(main.name) : null;
        if (ex != null) {
            double[] mid = line.at((main.startM + main.endM) / 2);
            String dir = ExpresswayRoutes.direction(ex, mid[0], mid[1], mid[2]);
            if (dir != null) its = new RouteScenario.Its(ex.routeNo(), ex.keyword, dir);
        }
        String summary = String.format(Locale.KOREA, "%s → %s · 고속도로·자동차전용도로 %.1fkm%s",
                fromName, toName, fast / 1000.0, main != null ? " (" + main.name + ")" : "");
        return new RouteScenario(CUSTOM_ID, "custom", 0, "내 경로: " + fromName + " → " + toName, summary,
                rep.type, rep.limitKmh, rep.assumedFlowKmh, "경로: OpenStreetMap 기여자(OSRM) · 장소: 카카오",
                its, situations, line, route.spans);
    }
}
