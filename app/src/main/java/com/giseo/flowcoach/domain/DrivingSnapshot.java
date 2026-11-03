package com.giseo.flowcoach.domain;

/**
 * 한 시점의 주행 상태.
 * 위치·속도(LocationSource)와 차량 신호(VehicleSignalSource)를 합쳐 1초마다 하나씩 만든다.
 * 불변 객체라 여러 감지기에 그대로 넘겨도 안전하다.
 */
public final class DrivingSnapshot {

    /** 브레이크 페달 위치를 모를 때 쓰는 값. */
    public static final int PEDAL_UNKNOWN = -1;

    public final long timeMs;
    public final double speedKmh;
    /** 진행 방향(0~360도). 모르면 Double.NaN. */
    public final double headingDeg;
    public final TurnSignal turnSignal;
    public final boolean brakePressed;
    /** 브레이크 페달 위치(0~100%). 모르면 PEDAL_UNKNOWN. */
    public final int brakePedalPercent;
    /** 기어. 신호가 없으면 UNKNOWN(코칭은 계속). */
    public final Gear gear;

    public DrivingSnapshot(long timeMs, double speedKmh, double headingDeg,
                           TurnSignal turnSignal, boolean brakePressed, int brakePedalPercent) {
        this(timeMs, speedKmh, headingDeg, turnSignal, brakePressed, brakePedalPercent, Gear.UNKNOWN);
    }

    public DrivingSnapshot(long timeMs, double speedKmh, double headingDeg,
                           TurnSignal turnSignal, boolean brakePressed, int brakePedalPercent, Gear gear) {
        this.timeMs = timeMs;
        this.speedKmh = Math.max(0, speedKmh);
        this.headingDeg = headingDeg;
        this.turnSignal = turnSignal == null ? TurnSignal.OFF : turnSignal;
        this.brakePressed = brakePressed;
        this.brakePedalPercent = brakePedalPercent;
        this.gear = gear == null ? Gear.UNKNOWN : gear;
    }

    public boolean hasHeading() {
        return !Double.isNaN(headingDeg);
    }

    @Override
    public String toString() {
        return "Snapshot{t=" + timeMs + ", v=" + Math.round(speedKmh) + "km/h, hdg=" + headingDeg
                + ", signal=" + turnSignal + ", brake=" + brakePressed + "/" + brakePedalPercent + "%, gear=" + gear + "}";
    }
}
