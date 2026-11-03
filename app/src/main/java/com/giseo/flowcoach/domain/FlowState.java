package com.giseo.flowcoach.domain;

/** 내 속도가 흐름에 맞는지에 대한 판단 결과. */
public enum FlowState {
    /** 정차 중이거나 판단할 정보가 부족함. */
    UNKNOWN,
    /** 도로 자체가 정체. 이때는 "느리다"는 안내를 하지 않는다. */
    CONGESTED,
    /** 흐름보다 눈에 띄게 느림. */
    TOO_SLOW,
    /** 흐름에 맞음. */
    SMOOTH,
    /** 제한속도 초과. */
    OVER_LIMIT
}
