package com.giseo.flowcoach.domain;

/** FlowAnalyzer의 판단 결과. 화면 게이지와 음성 안내가 함께 쓴다. */
public final class FlowResult {

    public final FlowState state;
    public final double mySpeedKmh;
    /** 권장 속도 = min(흐름 속도, 제한속도). 항상 제한속도 이하. */
    public final double recommendedKmh;
    public final int speedLimitKmh;
    public final double flowSpeedKmh;

    public FlowResult(FlowState state, double mySpeedKmh, double recommendedKmh,
                      int speedLimitKmh, double flowSpeedKmh) {
        this.state = state;
        this.mySpeedKmh = mySpeedKmh;
        this.recommendedKmh = recommendedKmh;
        this.speedLimitKmh = speedLimitKmh;
        this.flowSpeedKmh = flowSpeedKmh;
    }

    /** 내 속도 - 권장 속도. 음수면 느림. */
    public double gapKmh() {
        return mySpeedKmh - recommendedKmh;
    }

    @Override
    public String toString() {
        return "Flow{" + state + ", my=" + Math.round(mySpeedKmh) + ", rec=" + Math.round(recommendedKmh)
                + ", limit=" + speedLimitKmh + ", flow=" + flowSpeedKmh + "}";
    }
}
