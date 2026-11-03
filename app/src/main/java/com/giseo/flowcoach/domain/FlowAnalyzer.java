package com.giseo.flowcoach.domain;

/**
 * 실시간 흐름 진단.
 * 내 속도를 '권장 속도 = min(구간 흐름 속도, 제한속도)'와 비교한다.
 * 권장 속도는 절대 제한속도를 넘지 않으므로, 흐름이 과속 중이어도 과속을 권하지 않는다.
 */
public final class FlowAnalyzer {

    private final CoachConfig config;

    public FlowAnalyzer(CoachConfig config) {
        this.config = config;
    }

    public FlowResult analyze(double mySpeedKmh, RoadContext road) {
        int limit = road.speedLimitKmh;
        double recommended = road.hasFlowSpeed() ? Math.min(road.flowSpeedKmh, limit) : limit;

        FlowState state;
        if (mySpeedKmh > limit + config.overLimitToleranceKmh) {
            // 과속은 정체 여부와 상관없이 가장 먼저 본다.
            state = FlowState.OVER_LIMIT;
        } else if (road.hasFlowSpeed() && road.flowSpeedKmh < config.congestionFlowKmh) {
            // 도로 전체가 막혀 있으면 내 탓이 아니다.
            state = FlowState.CONGESTED;
        } else if (mySpeedKmh < config.minMovingKmh) {
            state = FlowState.UNKNOWN;
        } else if (mySpeedKmh < recommended - config.slowMarginKmh) {
            state = FlowState.TOO_SLOW;
        } else {
            state = FlowState.SMOOTH;
        }
        return new FlowResult(state, mySpeedKmh, recommended, limit, road.flowSpeedKmh);
    }
}
