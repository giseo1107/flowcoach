package com.giseo.flowcoach.domain;

/**
 * 지금 달리는 도로 구간의 정보.
 * 흐름 속도는 공공 교통소통정보(구간 평균 통행속도)에서 가져온다.
 */
public final class RoadContext {

    public final RoadType roadType;
    public final int speedLimitKmh;
    /** 구간 평균 통행속도. 모르면 Double.NaN. */
    public final double flowSpeedKmh;

    public RoadContext(RoadType roadType, int speedLimitKmh, double flowSpeedKmh) {
        this.roadType = roadType == null ? RoadType.UNKNOWN : roadType;
        this.speedLimitKmh = speedLimitKmh > 0 ? speedLimitKmh : this.roadType.defaultLimitKmh;
        this.flowSpeedKmh = flowSpeedKmh;
    }

    public static RoadContext unknown() {
        return new RoadContext(RoadType.UNKNOWN, 0, Double.NaN);
    }

    public boolean hasFlowSpeed() {
        return !Double.isNaN(flowSpeedKmh);
    }

    @Override
    public String toString() {
        return "Road{" + roadType + ", limit=" + speedLimitKmh + ", flow=" + flowSpeedKmh + "}";
    }
}
