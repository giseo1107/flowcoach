package com.giseo.flowcoach.data.traffic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 주변 링크들의 속도로 '지금 이 구간의 흐름 속도'를 추정한다.
 *
 * API는 좌표 범위 안의 링크를 상·하행 구분 없이 돌려주므로,
 *  1) 원하는 도로 이름(예: "경부")이 있으면 그 도로만 남기고
 *  2) 속도 0(수집 안 됨)은 빼고
 *  3) 중앙값을 쓴다 — 반대 방향이 막혀 있거나 튀는 값이 섞여도 덜 흔들린다.
 */
public final class FlowEstimator {

    /** 추정 결과. */
    public static final class Estimate {
        public final double speedKmh;
        public final String roadName;
        public final int linkCount;

        Estimate(double speedKmh, String roadName, int linkCount) {
            this.speedKmh = speedKmh;
            this.roadName = roadName;
            this.linkCount = linkCount;
        }
    }

    private FlowEstimator() {
    }

    /**
     * 지정한 도로 이름이 들어간 링크만 쓴다. 없으면 null(다른 도로로 대신하지 않음).
     * type=all 응답에는 시내 도로가 섞여 있어서, 고속도로 흐름을 시내 도로 속도로 착각하지 않게 한다.
     */
    public static Estimate estimateStrict(List<LinkSpeed> links, String roadNameContains) {
        return estimateStrict(links, roadNameContains, null);
    }

    /**
     * 도로 이름 + 방향(예: "상행")까지 맞는 링크만 쓴다. 없으면 null.
     * 같은 고속도로라도 상행·하행 흐름이 크게 다르므로(예: 상행 91, 하행 정체) 섞지 않는다.
     */
    public static Estimate estimateStrict(List<LinkSpeed> links, String roadNameContains, String direction) {
        List<LinkSpeed> picked = new ArrayList<>();
        for (LinkSpeed l : links) {
            if (l.speedKmh <= 0) continue;
            if (roadNameContains != null && !roadNameContains.isEmpty() && !l.roadName.contains(roadNameContains)) continue;
            if (direction != null && !direction.isEmpty() && !direction.equals(l.direction)) continue;
            picked.add(l);
        }
        return picked.isEmpty() ? null : estimate(picked, null);
    }

    /** 쓸 만한 링크가 없으면 null. 지정한 도로가 없으면 전체 링크로 다시 계산한다. */
    public static Estimate estimate(List<LinkSpeed> links, String roadNameContains) {
        List<LinkSpeed> picked = new ArrayList<>();
        for (LinkSpeed l : links) {
            if (l.speedKmh <= 0 || l.speedKmh > 200) continue;
            if (roadNameContains != null && !roadNameContains.isEmpty()
                    && !l.roadName.contains(roadNameContains)) continue;
            picked.add(l);
        }
        // 지정한 도로가 범위 안에 없으면 전체로 다시
        if (picked.isEmpty() && roadNameContains != null && !roadNameContains.isEmpty()) {
            return estimate(links, null);
        }
        if (picked.isEmpty()) return null;

        List<Double> speeds = new ArrayList<>();
        Map<String, Integer> nameCount = new HashMap<>();
        for (LinkSpeed l : picked) {
            speeds.add(l.speedKmh);
            nameCount.merge(l.roadName, 1, Integer::sum);
        }
        Collections.sort(speeds);
        int n = speeds.size();
        double median = n % 2 == 1 ? speeds.get(n / 2) : (speeds.get(n / 2 - 1) + speeds.get(n / 2)) / 2.0;

        String topName = "";
        int top = -1;
        for (Map.Entry<String, Integer> e : nameCount.entrySet()) {
            if (e.getValue() > top) {
                top = e.getValue();
                topName = e.getKey();
            }
        }
        return new Estimate(median, topName, n);
    }
}
