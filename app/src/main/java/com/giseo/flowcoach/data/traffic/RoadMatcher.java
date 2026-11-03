package com.giseo.flowcoach.data.traffic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 현재 위치 주변(약 400m)의 ITS 링크들 중에서 '지금 달리는 도로'를 고른다.
 *
 * ITS 응답에는 도로 좌표가 없어서 정확한 맵매칭은 못 한다. 대신
 *  1) 바로 전에 고른 도로가 아직 주변에 있으면 그대로 유지(도로가 자꾸 바뀌지 않게)
 *  2) 빠르게 달리는 중(60km/h 이상 또는 제한 90km/h 이상)이면 고속도로를 우선
 *  3) 그 밖에는 구간(링크)이 가장 많은 도로 — 같은 수면 고속도로 우선
 * 정확한 매칭은 국토부 표준노드링크(도로 좌표)를 쓰는 확장 단계에서 한다.
 */
public final class RoadMatcher {

    /** 이 속도 이상이면 고속도로를 달리고 있을 가능성이 높다고 본다(km/h). */
    static final double FAST_KMH = 60;
    static final int FAST_LIMIT_KMH = 90;

    /** 고른 도로. */
    public static final class Candidate {
        public final String roadName;
        public final int linkCount;
        public final boolean expressway;
        /** 고속도로면 노선 정보, 아니면 null. */
        public final ExpresswayRoutes.Route route;

        Candidate(String roadName, int linkCount) {
            this.roadName = roadName;
            this.linkCount = linkCount;
            this.route = ExpresswayRoutes.find(roadName);
            this.expressway = ExpresswayRoutes.isExpressway(roadName);
        }

        @Override
        public String toString() {
            return roadName + "(" + linkCount + ")";
        }
    }

    private RoadMatcher() {
    }

    /** 도로 이름별로 묶는다(속도 0 = 수집 안 됨, 이름 없음은 제외). 순서는 처음 나온 순서. */
    public static List<Candidate> candidates(List<LinkSpeed> links) {
        Map<String, Integer> count = new LinkedHashMap<>();
        for (LinkSpeed l : links) {
            if (l.speedKmh <= 0 || l.roadName.trim().isEmpty()) continue;
            count.merge(l.roadName, 1, Integer::sum);
        }
        List<Candidate> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : count.entrySet()) out.add(new Candidate(e.getKey(), e.getValue()));
        return out;
    }

    /**
     * @param previousRoad 바로 전에 고른 도로 이름(없으면 null)
     * @param vehicleKmh   현재 차속(모르면 NaN)
     * @param limitKmh     내비가 알려준 현재 도로 제한속도(모르면 0)
     * @return 고른 도로, 후보가 없으면 null
     */
    public static Candidate pick(List<LinkSpeed> links, String previousRoad, double vehicleKmh, int limitKmh) {
        return pick(links, previousRoad, vehicleKmh, limitKmh, null, true);
    }

    /**
     * 달리는 도로를 미리 아는 경우(실제 도로 시연: 경로의 지금 구간).
     * @param roadHint        지금 구간의 도로 이름(없으면 null). 같은 이름의 후보가 있으면 그것을 고른다
     * @param allowExpressway false면 고속도로 후보는 빼고 고른다(시내 구간에서 옆 고속도로를 잡지 않게)
     */
    public static Candidate pick(List<LinkSpeed> links, String previousRoad, double vehicleKmh, int limitKmh,
                                 String roadHint, boolean allowExpressway) {
        List<Candidate> cs = candidates(links);
        if (!allowExpressway) cs.removeIf(c -> c.expressway);
        if (cs.isEmpty()) return null;
        if (roadHint != null && !roadHint.trim().isEmpty()) {
            // 이름이 똑같은 도로 → 없으면 이름이 겹치는 도로("판교로"가 "대왕판교로"에 잡히지 않게 같은 이름 먼저)
            String hint = roadHint.replace(" ", "");
            for (Candidate c : cs) {
                if (c.roadName.replace(" ", "").equals(hint)) return c;
            }
            Candidate hinted = null;
            for (Candidate c : cs) {
                if (sameRoad(c.roadName, roadHint) && (hinted == null || c.linkCount > hinted.linkCount)) hinted = c;
            }
            if (hinted != null) return hinted;
        }
        boolean fast = (!Double.isNaN(vehicleKmh) && vehicleKmh >= FAST_KMH) || limitKmh >= FAST_LIMIT_KMH;

        if (previousRoad != null) {
            for (Candidate c : cs) {
                // 빠르게 달리는데 이전 도로가 일반도로이고 주변에 고속도로가 있으면 유지하지 않는다
                if (c.roadName.equals(previousRoad) && !(fast && !c.expressway && hasExpressway(cs))) return c;
            }
        }
        Candidate best = null;
        for (Candidate c : cs) {
            if (fast && !c.expressway && hasExpressway(cs)) continue;
            if (best == null || c.linkCount > best.linkCount
                    || (c.linkCount == best.linkCount && c.expressway && !best.expressway)) {
                best = c;
            }
        }
        return best;
    }

    /** 띄어쓰기를 빼고 한쪽 이름이 다른 쪽을 품으면 같은 도로로 본다(예: "경부고속도로" ↔ "경부 고속도로"). */
    static boolean sameRoad(String a, String b) {
        String x = a == null ? "" : a.replace(" ", "");
        String y = b == null ? "" : b.replace(" ", "");
        if (x.length() < 2 || y.length() < 2) return false;
        return x.contains(y) || y.contains(x);
    }

    private static boolean hasExpressway(List<Candidate> cs) {
        for (Candidate c : cs) if (c.expressway) return true;
        return false;
    }
}
