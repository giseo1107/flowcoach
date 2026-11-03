package com.giseo.flowcoach.data.vehicle;

import com.giseo.flowcoach.domain.TurnSignal;

/**
 * 차량 신호 출처.
 * 실제 차량/에뮬레이터는 PleosVehicleSignalSource(Pleos Vehicle SDK)로 읽는다.
 * domain 계층은 이 인터페이스만 알기 때문에 SDK가 바뀌어도 판단 로직은 그대로다.
 */
public interface VehicleSignalSource {

    void start();

    void stop();

    TurnSignal currentTurnSignal();

    boolean isBrakePressed();

    /** 크루즈 컨트롤 목표 속도(km/h). 모르면 Double.NaN. */
    double cruiseTargetKmh();

    /** 화면에 보여줄 연결 상태. */
    String statusText();
}
