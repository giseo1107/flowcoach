package com.giseo.flowcoach.data.route;

import com.giseo.flowcoach.data.traffic.ExpresswayRoutes;
import com.giseo.flowcoach.domain.RoadType;

/**
 * 경로의 한 도로 구간(출발점에서 startM~endM 미터): 도로 종류·제한속도·가정 흐름.
 *
 * 시내 도로는 신호·교차로 때문에 흐름 판단이 의미가 적어서, 상황(추월·과속 등)은
 * 신호 없는 도로(고속도로·자동차전용도로) 구간에만 배치한다.
 */
public final class RoadSpan {

    public final double startM;
    public final double endM;
    public final RoadType type;
    public final int limitKmh;
    public final double assumedFlowKmh;
    public final String name;

    public RoadSpan(double startM, double endM, RoadType type, int limitKmh, double assumedFlowKmh, String name) {
        this.startM = startM;
        this.endM = endM;
        this.type = type;
        this.limitKmh = limitKmh;
        this.assumedFlowKmh = assumedFlowKmh;
        this.name = name == null ? "" : name;
    }

    public double lengthM() {
        return endM - startM;
    }

    /** 상황을 배치할 수 있는 도로(신호 없는 도로)인지. */
    public boolean allowsSituations() {
        return type.isUninterruptedFlow();
    }

    /** 권장 속도 예상값(흐름과 제한 중 낮은 값). */
    public double expectedRecKmh() {
        return Math.min(assumedFlowKmh, limitKmh);
    }

    public RoadSpan withRange(double start, double end) {
        return new RoadSpan(start, end, type, limitKmh, assumedFlowKmh, name);
    }

    /** 신호 없는 도시고속도로·자동차전용도로(일부). 교통 조회 쪽 목록과 같다. */
    private static final String[] URBAN_MOTORWAYS = {
            "올림픽대로", "강변북로", "내부순환", "동부간선", "서부간선", "북부간선", "분당수서", "분당내곡",
            "자동차전용", "도시고속"
    };

    /**
     * 도로 이름(OpenStreetMap)으로 구간을 만든다.
     * 고속도로 = 제한 100 · 흐름 92 / 자동차전용도로 = 제한 80 · 흐름 70 / 국도(번호 있음) = 제한 80 · 흐름 60
     * / 그 밖(시내) = 제한 50 · 흐름 42 를 가정값으로 둔다(실제 흐름은 실시간 교통으로 바뀐다).
     */
    public static RoadSpan fromRoadName(double startM, double endM, String name, String ref) {
        String n = name == null ? "" : name;
        if (ExpresswayRoutes.isExpressway(n)) return new RoadSpan(startM, endM, RoadType.EXPRESSWAY, 100, 92, n);
        for (String m : URBAN_MOTORWAYS) {
            if (n.contains(m)) return new RoadSpan(startM, endM, RoadType.MOTORWAY, 80, 70, n);
        }
        if (ref != null && !ref.isEmpty() && n.contains("국도")) {
            return new RoadSpan(startM, endM, RoadType.NATIONAL, 80, 60, n);
        }
        return new RoadSpan(startM, endM, RoadType.URBAN, 50, 42, n);
    }
}
