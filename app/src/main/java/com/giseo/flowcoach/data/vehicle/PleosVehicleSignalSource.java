package com.giseo.flowcoach.data.vehicle;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.giseo.flowcoach.domain.TurnSignal;

import ai.pleos.playground.vehicle.Vehicle;
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue;
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener;
import ai.pleos.playground.vehicle.listener.CruiseControlTargetSpeedListener;
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener;
import kotlin.Unit;

/**
 * Pleos Vehicle SDK로 차량 신호를 읽는다.
 *
 *  - TurnSignal  : 방향지시등 (권한 pleos.car.permission.CAR_LIGHTS)
 *  - Brake       : 브레이크 페달 밟음 여부 (CAR_DRIVE)
 *  - DrivingMode : 크루즈 컨트롤 목표 속도 (CAR_DRIVE)
 *
 * SDK 콜백은 백그라운드 스레드에서 올 수 있으므로 최신 값만 volatile 필드에 저장하고,
 * 1초마다 도는 세션이 그 값을 읽어 간다.
 * SDK는 Kotlin으로 작성되어 있어, Java에서 람다 콜백은 Unit.INSTANCE를 반환해야 한다.
 */
public final class PleosVehicleSignalSource implements VehicleSignalSource {

    private static final String TAG = "PleosVehicle";
    /** 방향지시등이 깜빡이며 OFF가 잠깐 섞여 들어와도 이 시간 동안은 켜진 것으로 본다. */
    private static final long SIGNAL_LATCH_MS = 1200;
    private static final double MPS_TO_KMH = 3.6;

    private final Vehicle vehicle;

    private volatile TurnSignal rawSignal = TurnSignal.OFF;
    private volatile TurnSignal lastDirectional = TurnSignal.OFF;
    private volatile long lastDirectionalMs = 0;
    private volatile boolean brakePressed = false;
    private volatile double cruiseTargetKmh = Double.NaN;
    private volatile boolean receivedAny = false;
    private volatile String error;

    private final TurnSignalStateListener turnSignalListener = new TurnSignalStateListener() {
        @Override
        public void onTurnSignalStateUpdated(@Nullable TurnSignalValue state) {
            onTurnSignal(state);
        }

        @Override
        public void onFailed(@NonNull Exception e) {
            onError("방향지시등", e);
        }
    };

    private final BrakePedalStatusListener brakeListener = new BrakePedalStatusListener() {
        @Override
        public void onBrakePedalStatusUpdated(@Nullable Boolean status) {
            onBrake(status);
        }

        @Override
        public void onFailed(@NonNull Exception e) {
            onError("브레이크", e);
        }
    };

    private final CruiseControlTargetSpeedListener cruiseListener = new CruiseControlTargetSpeedListener() {
        @Override
        public void onCruiseControlTargetSpeedUpdated(@Nullable Float speed) {
            onCruiseTarget(speed);
        }

        @Override
        public void onFailed(@NonNull Exception e) {
            onError("크루즈", e);
        }
    };

    public PleosVehicleSignalSource(Context context) {
        vehicle = new Vehicle(context.getApplicationContext());
    }

    @Override
    public void start() {
        vehicle.initialize();
        try {
            // 변화가 생길 때마다 받기
            vehicle.getTurnSignal().registerTurnSignalState(turnSignalListener);
            vehicle.getBrake().registerBrakePedalStatus(brakeListener);
            vehicle.getDrivingMode().registerCruiseControlTargetSpeed(cruiseListener);

            // 시작 시점의 현재 값 한 번 읽기
            vehicle.getTurnSignal().getCurrentTurnSignalState(
                    v -> { onTurnSignal(v); return Unit.INSTANCE; },
                    e -> { onError("방향지시등", e); return Unit.INSTANCE; });
            vehicle.getBrake().getCurrentBrakePedalStatus(
                    v -> { onBrake(v); return Unit.INSTANCE; },
                    e -> { onError("브레이크", e); return Unit.INSTANCE; });
            vehicle.getDrivingMode().getCruiseControlTargetSpeed(
                    v -> { onCruiseTarget(v); return Unit.INSTANCE; },
                    e -> { onError("크루즈", e); return Unit.INSTANCE; });
        } catch (SecurityException e) {
            // Manifest 선언 또는 런타임 권한이 없을 때 SDK가 던짐
            error = "권한 없음";
            Log.e(TAG, "Pleos 권한 오류", e);
        }
    }

    @Override
    public void stop() {
        try {
            vehicle.getTurnSignal().unregisterTurnSignalState(turnSignalListener);
            vehicle.getBrake().unregisterBrakePedalStatus(brakeListener);
            vehicle.getDrivingMode().unregisterCruiseControlTargetSpeed(cruiseListener);
        } catch (RuntimeException e) {
            Log.w(TAG, "리스너 해제 실패", e);
        }
        vehicle.release();
    }

    // ---------- SDK 콜백 처리 ----------

    private void onTurnSignal(@Nullable TurnSignalValue value) {
        receivedAny = true;
        TurnSignal mapped = TurnSignal.OFF;
        if (value == TurnSignalValue.LEFT) mapped = TurnSignal.LEFT;
        else if (value == TurnSignalValue.RIGHT) mapped = TurnSignal.RIGHT;
        rawSignal = mapped;
        if (mapped != TurnSignal.OFF) {
            lastDirectional = mapped;
            lastDirectionalMs = SystemClock.elapsedRealtime();
        }
    }

    private void onBrake(@Nullable Boolean status) {
        receivedAny = true;
        brakePressed = status != null && status;
    }

    private void onCruiseTarget(@Nullable Float speedMps) {
        receivedAny = true;
        cruiseTargetKmh = speedMps == null ? Double.NaN : speedMps * MPS_TO_KMH;
    }

    private void onError(String what, Exception e) {
        error = what + " 읽기 실패";
        Log.w(TAG, what + " 오류: " + e.getMessage());
    }

    // ---------- VehicleSignalSource ----------

    @Override
    public TurnSignal currentTurnSignal() {
        long now = SystemClock.elapsedRealtime();
        TurnSignal raw = rawSignal;
        if (raw != TurnSignal.OFF) {
            lastDirectional = raw;
            lastDirectionalMs = now;
            return raw;
        }
        if (lastDirectional != TurnSignal.OFF && now - lastDirectionalMs < SIGNAL_LATCH_MS) {
            return lastDirectional;
        }
        return TurnSignal.OFF;
    }

    @Override
    public boolean isBrakePressed() {
        return brakePressed;
    }

    @Override
    public double cruiseTargetKmh() {
        return cruiseTargetKmh;
    }

    @Override
    public String statusText() {
        if (error != null && !receivedAny) return "Pleos " + error;
        return receivedAny ? "Pleos 차량 신호 연결됨" : "Pleos 연결 중…";
    }
}
