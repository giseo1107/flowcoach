package com.giseo.flowcoach.domain;

/** 운전자에게 들려줄 음성 안내 한 건. */
public final class CoachMessage {

    public final CoachEvent.Type type;
    /** 화면·TTS용 전체 문장(숫자 포함). */
    public final String text;
    /**
     * 미리 녹음된 음성 파일을 찾는 키(예: "passing_lane_hold").
     * TTS 엔진이 없는 환경(에뮬레이터)에서는 이 키로 음성 파일을 재생한다.
     */
    public final String voiceKey;
    public final long timeMs;

    public CoachMessage(CoachEvent.Type type, String text, String voiceKey, long timeMs) {
        this.type = type;
        this.text = text;
        this.voiceKey = voiceKey;
        this.timeMs = timeMs;
    }

    @Override
    public String toString() {
        return type + "@" + timeMs + ": " + text;
    }
}
