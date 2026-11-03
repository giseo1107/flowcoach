package com.giseo.flowcoach.data.route;

import com.giseo.flowcoach.data.demo.DemoScene;
import com.giseo.flowcoach.data.traffic.MiniJson;
import com.giseo.flowcoach.domain.RoadType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 실제 도로 시연 한 편: 실제 도로 좌표 + 그 위에서 보여줄 상황 목록.
 *
 * 앱 assets/routes/*.json 에 들어 있다. 예)
 * {
 *   "id": "gyeongbu_pangyo_yangjae", "title": "경부고속도로 판교→양재",
 *   "summary": "...", "roadType": "EXPRESSWAY", "limitKmh": 100, "assumedFlowKmh": 92,
 *   "situations": ["SMOOTH", "PASSING", ...],          // DemoScene 이름, 이 순서로 배치
 *   "points": [[37.355472, 127.103391], ...]            // 위도, 경도
 * }
 * 상황을 몇 km 지점에 둘지는 정하지 않는다. 고른 기준값에 맞춰 RoutePlanner가 배치한다.
 */
public final class RouteScenario {

    public final String id;
    /** "full"(한 경로에 상황 여러 개) 또는 "feature"(상황 하나짜리 짧은 경로). 목록 묶음용. */
    public final String group;
    /** 목록 순서. */
    public final int order;
    public final String title;
    public final String summary;
    public final RoadType roadType;
    /** 제한속도(실시간 교통에 제한속도가 없어서 쓰는 값). */
    public final int limitKmh;
    /** 실시간 교통을 못 받을 때 쓰는 흐름 속도. */
    public final double assumedFlowKmh;
    /** 경로 좌표 출처(화면 표시용). */
    public final String source;
    /** 고속도로면 노선번호·도로 이름·방향(시작 전 교통 확인용). 아니면 null. */
    public final Its its;
    public final List<DemoScene> situations;
    public final Polyline line;
    /** 도로 구간(출발점부터 순서대로, 경로 전체를 덮는다). 파일로 만든 경로는 구간 하나. */
    public final List<RoadSpan> spans;

    /** 국가교통정보센터 고속도로 조회 정보. */
    public static final class Its {
        public final String routeNo;
        public final String keyword;
        public final String direction;

        public Its(String routeNo, String keyword, String direction) {
            this.routeNo = routeNo;
            this.keyword = keyword;
            this.direction = direction;
        }
    }

    public RouteScenario(String id, String group, int order, String title, String summary, RoadType roadType,
                         int limitKmh, double assumedFlowKmh, String source, Its its,
                         List<DemoScene> situations, List<double[]> points) {
        this(id, group, order, title, summary, roadType, limitKmh, assumedFlowKmh, source, its, situations,
                new Polyline(points), null);
    }

    /** @param spans null이면 경로 전체를 roadType·limitKmh·assumedFlowKmh 구간 하나로 본다. */
    public RouteScenario(String id, String group, int order, String title, String summary, RoadType roadType,
                         int limitKmh, double assumedFlowKmh, String source, Its its,
                         List<DemoScene> situations, Polyline line, List<RoadSpan> spans) {
        this.id = id;
        this.group = group;
        this.order = order;
        this.its = its;
        this.title = title;
        this.summary = summary;
        this.roadType = roadType;
        this.limitKmh = limitKmh;
        this.assumedFlowKmh = assumedFlowKmh;
        this.source = source;
        this.situations = Collections.unmodifiableList(new ArrayList<>(situations));
        this.line = line;
        List<RoadSpan> list = new ArrayList<>();
        if (spans == null || spans.isEmpty()) {
            list.add(new RoadSpan(0, line.lengthMeters(), roadType, limitKmh, assumedFlowKmh, title));
        } else {
            list.addAll(spans);
        }
        this.spans = Collections.unmodifiableList(list);
    }

    /** 넣을 상황만 바꾼 복사본(내 경로에서 상황을 다시 고를 때). */
    public RouteScenario withSituations(List<DemoScene> newSituations) {
        return new RouteScenario(id, group, order, title, summary, roadType, limitKmh, assumedFlowKmh, source, its,
                newSituations, line, spans);
    }

    public boolean isCustom() {
        return "custom".equals(group);
    }

    /** meters 지점의 도로 구간. */
    public RoadSpan spanAt(double meters) {
        for (RoadSpan s : spans) if (meters < s.endM) return s;
        return spans.get(spans.size() - 1);
    }

    /** 시작 전 교통 확인에 쓸 구간: 상황을 둘 수 있는 가장 긴 구간(없으면 첫 구간). */
    public RoadSpan checkSpan() {
        RoadSpan best = null;
        for (RoadSpan s : spans) {
            if (s.allowsSituations() && (best == null || s.lengthM() > best.lengthM())) best = s;
        }
        return best != null ? best : spans.get(0);
    }

    /** 신호 없는 도로(고속도로·자동차전용도로) 길이 합(m). */
    public double uninterruptedMeters() {
        double m = 0;
        for (RoadSpan s : spans) if (s.allowsSituations()) m += s.lengthM();
        return m;
    }

    public double lengthKm() {
        return line.lengthMeters() / 1000.0;
    }

    public boolean isFeatureRoute() {
        return "feature".equals(group);
    }

    /** "11.3km" */
    public String lengthLabel() {
        return String.format(Locale.KOREA, "%.1fkm", lengthKm());
    }

    @SuppressWarnings("unchecked")
    public static RouteScenario parse(String json) {
        Map<String, Object> o = (Map<String, Object>) MiniJson.parse(json);
        List<DemoScene> situations = new ArrayList<>();
        for (Object s : (List<Object>) o.get("situations")) situations.add(DemoScene.valueOf((String) s));
        List<double[]> points = new ArrayList<>();
        for (Object p : (List<Object>) o.get("points")) {
            List<Object> xy = (List<Object>) p;
            points.add(new double[]{((Number) xy.get(0)).doubleValue(), ((Number) xy.get(1)).doubleValue()});
        }
        Its its = null;
        if (o.get("its") instanceof Map) {
            Map<String, Object> m = (Map<String, Object>) o.get("its");
            its = new Its(str(m.get("routeNo")), str(m.get("keyword")), str(m.get("direction")));
        }
        Object order = o.get("order");
        return new RouteScenario(
                (String) o.get("id"),
                str(o.get("group"), "full"),
                order instanceof Number ? ((Number) order).intValue() : 100,
                (String) o.get("title"),
                str(o.get("summary")),
                RoadType.valueOf(str(o.get("roadType"), "EXPRESSWAY")),
                ((Number) o.get("limitKmh")).intValue(),
                ((Number) o.get("assumedFlowKmh")).doubleValue(),
                str(o.get("source")),
                its, situations, points);
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }

    private static String str(Object v, String fallback) {
        return v == null ? fallback : v.toString();
    }
}
