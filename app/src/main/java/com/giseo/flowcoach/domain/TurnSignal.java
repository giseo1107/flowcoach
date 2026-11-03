package com.giseo.flowcoach.domain;

/** 방향지시등 상태. 차량 SDK 값은 data 계층에서 이 enum으로 변환한다. */
public enum TurnSignal {
    OFF,
    LEFT,
    RIGHT,
    /** 비상등. 차로 변경으로 해석하지 않는다. */
    HAZARD
}
