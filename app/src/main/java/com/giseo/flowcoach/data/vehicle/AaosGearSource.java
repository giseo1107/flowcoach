package com.giseo.flowcoach.data.vehicle;

import android.car.Car;
import android.car.VehiclePropertyIds;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.content.Context;
import android.util.Log;

import com.giseo.flowcoach.domain.Gear;

/**
 * 기어(P·R·N·D)를 Android Automotive 표준 속성 GEAR_SELECTION으로 읽는다.
 *
 * Pleos Vehicle SDK의 Gear API는 deprecated이고, Pleos 문서가 Google 표준 GEAR_SELECTION 사용을 안내한다.
 * 에뮬레이터에서는 Virtual Console의 Gear Selection으로 바뀐다.
 *
 * 권한: android.car.permission.CAR_POWERTRAIN
 * 읽지 못하면 Gear.UNKNOWN → 예전처럼 계속 코칭한다(기어 때문에 앱이 멈추지 않게).
 */
public final class AaosGearSource {

    private static final String TAG = "AaosGear";

    private final Context context;
    private Car car;
    private CarPropertyManager properties;

    private volatile Gear gear = Gear.UNKNOWN;
    private volatile String status = "기어 연결 전";

    private final CarPropertyManager.CarPropertyEventCallback callback =
            new CarPropertyManager.CarPropertyEventCallback() {
                @Override
                public void onChangeEvent(CarPropertyValue value) {
                    Object v = value.getValue();
                    if (v instanceof Integer) gear = Gear.fromAaos((Integer) v);
                }

                @Override
                public void onErrorEvent(int propertyId, int areaId) {
                    status = "기어 읽기 오류";
                }
            };

    public AaosGearSource(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start() {
        try {
            car = Car.createCar(context);
            properties = (CarPropertyManager) car.getCarManager(Car.PROPERTY_SERVICE);
            if (properties == null) {
                status = "차량 속성 서비스 없음";
                return;
            }
            boolean ok = properties.registerCallback(callback, VehiclePropertyIds.GEAR_SELECTION,
                    CarPropertyManager.SENSOR_RATE_ONCHANGE);
            status = ok ? "기어 연결됨" : "기어 구독 실패";
            poll();
        } catch (SecurityException e) {
            status = "기어 권한 없음(CAR_POWERTRAIN)";
            Log.w(TAG, "GEAR_SELECTION 권한 없음", e);
        } catch (Throwable t) {
            status = "기어 사용 불가";
            Log.w(TAG, "GEAR_SELECTION 시작 실패", t);
        }
    }

    public void stop() {
        try {
            if (properties != null) properties.unregisterCallback(callback);
            if (car != null) car.disconnect();
        } catch (Throwable t) {
            Log.w(TAG, "GEAR_SELECTION 해제 실패", t);
        }
        properties = null;
        car = null;
    }

    /** 지금 기어. 변경 이벤트를 놓쳐도 맞도록 부를 때마다 한 번 더 읽는다. */
    public Gear gear() {
        poll();
        return gear;
    }

    private void poll() {
        if (properties == null) return;
        try {
            gear = Gear.fromAaos(properties.getIntProperty(VehiclePropertyIds.GEAR_SELECTION, 0));
        } catch (Throwable t) {
            // 이벤트로 받은 값을 그대로 쓴다
        }
    }

    public String status() {
        return status;
    }
}
