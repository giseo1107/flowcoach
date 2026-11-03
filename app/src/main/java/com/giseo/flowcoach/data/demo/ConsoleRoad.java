package com.giseo.flowcoach.data.demo;

import com.giseo.flowcoach.domain.RoadContext;
import com.giseo.flowcoach.domain.RoadType;

/**
 * 콘솔 연동 주행에서 쓰는 가상의 도로.
 * 자동차전용도로(제한 90km/h, 구간 흐름 88km/h)로 두면
 * Virtual Console 크루즈 목표 속도 범위(약 20~100km/h) 안에서
 * '흐름보다 느림 / 원활 / 과속'과 추월차로 경고(흐름 80 이상)를 모두 재현할 수 있다.
 */
public final class ConsoleRoad {

    /** 교통 API 키가 없거나 조회에 실패했을 때 쓰는 가정값. */
    public static final RoadContext ROAD = new RoadContext(RoadType.MOTORWAY, 90, 88);

    /**
     * 실시간 교통정보를 조회할 시연 위치: 경부고속도로 판교 부근.
     * 에뮬레이터 GPS는 한국이 아니므로, 콘솔 연동 주행에서는 이 위치의 실제 흐름 속도를 받아 쓴다.
     */
    public static final double DEMO_LAT = 37.400;
    public static final double DEMO_LON = 127.100;
    public static final String DEMO_ROAD_HINT = "경부";
    /** 경부고속도로 노선번호(ITS type=ex 조회용). */
    public static final String DEMO_ROUTE_NO = "0010";
    /** 시연 주행 방향. 상행·하행 흐름이 크게 다를 수 있어 한 방향만 쓴다. */
    public static final String DEMO_DIRECTION = "상행";

    private ConsoleRoad() {
    }
}
