package com.giseo.flowcoach.voice;

import android.content.Context;

/**
 * TTS 엔진이 있으면 TTS로(숫자까지 포함한 문장), 없으면 녹음 음성 파일로 안내한다.
 * 실제 차량에서는 TTS, 에뮬레이터 시연에서는 녹음 음성이 쓰인다.
 */
public final class SmartSpeaker implements Speaker {

    private final AndroidTtsSpeaker tts;
    private final ClipSpeaker clips;

    public SmartSpeaker(Context context) {
        tts = new AndroidTtsSpeaker(context);
        clips = new ClipSpeaker(context);
    }

    @Override
    public boolean speak(String voiceKey, String text) {
        if (tts.isReady()) {
            return tts.speak(voiceKey, text);
        }
        return clips.speak(voiceKey, text);
    }

    @Override
    public String status() {
        return tts.isReady() ? tts.status() : tts.status() + " → " + clips.status();
    }

    @Override
    public void shutdown() {
        tts.shutdown();
        clips.shutdown();
    }
}
