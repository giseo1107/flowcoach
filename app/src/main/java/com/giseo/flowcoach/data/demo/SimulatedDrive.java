package com.giseo.flowcoach.data.demo;

/**
 * 에뮬레이터용 간단한 차량 속도 모델.
 * 에뮬레이터 Virtual Console에는 '현재 속도' 조작이 없어서,
 * 콘솔의 크루즈 목표 속도와 브레이크 신호(Pleos SDK로 읽은 실제 값)로 속도를 만들어 낸다.
 *
 *  - 브레이크를 밟으면 초당 12km/h 감속(급제동 수준)
 *  - 놓으면 목표 속도를 향해 초당 4km/h씩 가속/감속
 */
public final class SimulatedDrive {

    public static final double BRAKE_DECEL_KMH_PER_SEC = 12;
    public static final double CRUISE_RATE_KMH_PER_SEC = 4;

    private final double defaultTargetKmh;
    private double speedKmh;
    private long lastMs = -1;

    public SimulatedDrive(double startKmh, double defaultTargetKmh) {
        this.speedKmh = startKmh;
        this.defaultTargetKmh = defaultTargetKmh;
    }

    /** 현재 시각의 속도를 계산한다. targetKmh가 NaN이면 기본 목표 속도를 쓴다. */
    public double step(long nowMs, boolean braking, double targetKmh) {
        double dtSec = lastMs < 0 ? 0 : Math.min(2.0, Math.max(0, (nowMs - lastMs) / 1000.0));
        lastMs = nowMs;
        double target = Double.isNaN(targetKmh) ? defaultTargetKmh : targetKmh;

        if (braking) {
            speedKmh = Math.max(0, speedKmh - BRAKE_DECEL_KMH_PER_SEC * dtSec);
        } else if (speedKmh < target) {
            speedKmh = Math.min(target, speedKmh + CRUISE_RATE_KMH_PER_SEC * dtSec);
        } else {
            speedKmh = Math.max(target, speedKmh - CRUISE_RATE_KMH_PER_SEC * dtSec);
        }
        return speedKmh;
    }

    /** 주차(P)·후진(R): 이 모델은 앞으로 가는 속도만 만들므로 0으로 둔다. D로 바꾸면 0에서 다시 가속한다. */
    public double stop(long nowMs) {
        speedKmh = 0;
        lastMs = nowMs;
        return speedKmh;
    }

    public double getSpeedKmh() {
        return speedKmh;
    }
}
