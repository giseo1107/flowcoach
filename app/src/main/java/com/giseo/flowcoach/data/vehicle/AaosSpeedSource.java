package com.giseo.flowcoach.data.vehicle;

import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.util.Log;

/**
 * 실제 차속(PERF_VEHICLE_SPEED)을 Android Automotive 표준 차량 API로 읽는다.
 *
 * Pleos Vehicle SDK에는 차속 API가 없고, Pleos 문서도 Google 표준 속성 사용을 안내한다
 * (예: Gear API는 deprecated, "use the Google GEAR_SELECTION property").
 * 에뮬레이터에서는 Virtual Console의 가속(ACCEL)·브레이크 페달로 실제 차속이 바뀐다.
 *
 * 권한: android.car.permission.CAR_SPEED (런타임 요청 필요)
 */
public final class AaosSpeedSource {

    private static final String TAG = "AaosSpeed";
    private static final double MPS_TO_KMH = 3.6;
    /** 이 속도를 넘은 적이 있어야 '실제 차가 움직이는 중'으로 본다. */
    private static final double MOVING_KMH = 1.0;

    private final Context context;
    private Car car;
    private CarPropertyManager properties;

    private volatile double speedKmh = Double.NaN;
    private volatile boolean hasMoved = false;
    private volatile boolean polledOk = false;
    private volatile String status = "차속 연결 전";

    private final CarPropertyManager.CarPropertyEventCallback callback =
            new CarPropertyManager.CarPropertyEventCallback() {
                @Override
                public void onChangeEvent(CarPropertyValue value) {
                    Object v = value.getValue();
                    if (v instanceof Float) {
                        double kmh = Math.abs((Float) v) * MPS_TO_KMH; // 후진은 음수로 들어올 수 있음
                        speedKmh = kmh;
                        if (kmh > MOVING_KMH) hasMoved = true;
                    }
                }

                @Override
                public void onErrorEvent(int propertyId, int areaId) {
                    status = "차속 읽기 오류";
                    Log.w(TAG, "PERF_VEHICLE_SPEED 오류 area=" + areaId);
                }
            };

    public AaosSpeedSource(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start() {
        try {
            car = Car.createCar(context);
            if (car == null) {
                status = "차량 서비스 없음";
                return;
            }
            properties = (CarPropertyManager) car.getCarManager(Car.PROPERTY_SERVICE);
            boolean ok = properties.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED,
                    CarPropertyManager.SENSOR_RATE_NORMAL);
            status = ok ? "실제 차속 연결됨" : "차속 구독 실패";
        } catch (SecurityException e) {
            status = "차속 권한 없음";
            Log.w(TAG, status, e);
        } catch (RuntimeException e) {
            status = "차속 사용 불가";
            Log.w(TAG, status, e);
        }
    }

    public void stop() {
        try {
            if (properties != null) properties.unregisterCallback(callback);
        } catch (RuntimeException e) {
            Log.w(TAG, "구독 해제 실패", e);
        }
        if (car != null) car.disconnect();
        properties = null;
        car = null;
    }

    /**
     * 현재 차속(km/h). 모르면 Double.NaN.
     * 변경 알림(callback)이 안 오는 환경에 대비해, 호출될 때마다(1초) 직접 한 번 더 읽는다.
     */
    public double speedKmh() {
        CarPropertyManager pm = properties;
        if (pm != null) {
            try {
                float mps = pm.getFloatProperty(VehiclePropertyIds.PERF_VEHICLE_SPEED, 0);
                double kmh = Math.abs(mps) * MPS_TO_KMH;
                speedKmh = kmh;
                if (kmh > MOVING_KMH) hasMoved = true;
                polledOk = true;
            } catch (SecurityException e) {
                status = "차속 권한 없음";
            } catch (RuntimeException e) {
                if (!polledOk) status = "차속 읽기 실패(" + e.getClass().getSimpleName() + ")";
            }
        }
        return speedKmh;
    }

    /** 이번 주행에서 실제로 차가 움직인 적이 있는지. */
    public boolean hasMoved() {
        return hasMoved;
    }

    /** 화면 표시용 상태(예: "실제 차속 연결됨 · 146km/h"). */
    public String status() {
        double v = speedKmh;
        return Double.isNaN(v) ? status : status + " · " + Math.round(v) + "km/h";
    }
}
