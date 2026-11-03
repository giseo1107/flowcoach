package com.giseo.flowcoach.domain;

/** 도로 종류. 신호 없는 도로(고속도로·자동차전용도로)를 1차 서비스 대상으로 한다. */
public enum RoadType {
    EXPRESSWAY(100),
    MOTORWAY(90),
    NATIONAL(80),
    URBAN(50),
    UNKNOWN(80);

    /** 제한속도 정보를 받지 못했을 때 쓰는 기본값(km/h). */
    public final int defaultLimitKmh;

    RoadType(int defaultLimitKmh) {
        this.defaultLimitKmh = defaultLimitKmh;
    }

    /** 신호 없는 연속류 도로인지. 추월차로·흐름 코칭은 이 도로에서만 적용한다. */
    public boolean isUninterruptedFlow() {
        return this == EXPRESSWAY || this == MOTORWAY;
    }
}
