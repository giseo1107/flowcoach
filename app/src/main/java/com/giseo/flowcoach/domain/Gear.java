package com.giseo.flowcoach.domain;

/**
 * 기어 상태. 흐름 코칭은 앞으로 달릴 때(D)만 한다.
 * P(주차)·R(후진)·N(중립)에서는 판단·음성·리포트를 멈춘다(주차 중 브레이크를 급제동으로 세지 않도록).
 */
public enum Gear {
    /** 기어 신호가 없음(시연 주행, 차량 API가 없는 기기). 예전처럼 코칭한다. */
    UNKNOWN,
    PARK,
    REVERSE,
    NEUTRAL,
    DRIVE;

    /** 이 기어에서 흐름 코칭을 하는지. */
    public boolean allowsCoaching() {
        return this == DRIVE || this == UNKNOWN;
    }

    /**
     * AAOS 표준 속성 GEAR_SELECTION 값(android.car.VehicleGear)을 변환한다.
     * NEUTRAL=1, REVERSE=2, PARK=4, DRIVE=8, 수동 1~9단=16~4096(앞으로 가는 기어라 DRIVE로 본다).
     * 도메인을 Android에 의존하지 않게 하려고 상수 값을 그대로 쓴다.
     */
    public static Gear fromAaos(int vehicleGear) {
        switch (vehicleGear) {
            case 1: return NEUTRAL;
            case 2: return REVERSE;
            case 4: return PARK;
            case 8: return DRIVE;
            default:
                return vehicleGear >= 16 && vehicleGear <= 4096 ? DRIVE : UNKNOWN;
        }
    }

    /** 화면 표시용 한 글자. */
    public String letter() {
        switch (this) {
            case PARK: return "P";
            case REVERSE: return "R";
            case NEUTRAL: return "N";
            case DRIVE: return "D";
            default: return "-";
        }
    }
}
