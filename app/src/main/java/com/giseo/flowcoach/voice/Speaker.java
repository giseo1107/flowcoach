package com.giseo.flowcoach.voice;

/**
 * 음성 안내 출력.
 *  - AndroidTtsSpeaker : Android 기본 TTS(문장을 그대로 읽음)
 *  - ClipSpeaker       : 미리 녹음한 음성 파일 재생(TTS 엔진이 없는 에뮬레이터용)
 *  - SmartSpeaker      : TTS가 되면 TTS, 아니면 음성 파일
 * Pleos TextToSpeech SDK가 열리면 같은 인터페이스로 구현체만 추가한다.
 */
public interface Speaker {

    /**
     * @param voiceKey 녹음 음성 파일 키(VoicePrompts). 없으면 null.
     * @param text     TTS로 읽을 전체 문장
     * @return 말하기를 시작했으면 true
     */
    boolean speak(String voiceKey, String text);

    /** 화면에 보여줄 음성 출력 상태. */
    String status();

    void shutdown();
}
