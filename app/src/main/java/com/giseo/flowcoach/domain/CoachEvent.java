package com.giseo.flowcoach.domain;

/** 감지기들이 만들어 내는 주행 이벤트. 음성 안내 여부는 CoachEngine이 정한다. */
public final class CoachEvent {

    public enum Type {
        /** 차로 변경 1회(기록용, 직접 안내하지 않음). */
        LANE_CHANGE_LEFT,
        LANE_CHANGE_RIGHT,
        /** 왼쪽으로 추월한 뒤 오래 복귀하지 않음. */
        PASSING_LANE_HOLD,
        /** 짧은 시간에 차로를 자주 바꿈. */
        FREQUENT_LANE_CHANGE,
        /** 급감속 1회(기록용). */
        HARD_BRAKE,
        /** 급감속이 반복됨. */
        REPEATED_HARD_BRAKE,
        /** 흐름보다 계속 느림. */
        TOO_SLOW,
        /** 제한속도 초과가 이어짐. */
        OVER_LIMIT,
        /** 흐름을 잘 타고 있음(칭찬). */
        SMOOTH_PRAISE
    }

    public final Type type;
    public final long timeMs;

    public CoachEvent(Type type, long timeMs) {
        this.type = type;
        this.timeMs = timeMs;
    }

    @Override
    public String toString() {
        return type + "@" + timeMs;
    }
}
