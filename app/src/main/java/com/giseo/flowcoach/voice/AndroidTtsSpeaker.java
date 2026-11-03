package com.giseo.flowcoach.voice;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;

/**
 * Android 기본 TTS로 음성 안내.
 *
 * 차량 오디오 규칙:
 *  - 용도를 '길 안내 음성'(USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)으로 지정
 *  - 말하는 동안만 오디오 포커스를 잠깐(TRANSIENT_MAY_DUCK) 받아 음악 볼륨을 낮춤
 *  - Pleos 'Voice Guidance Preroll Silence' 가이드에 따라 앞에 짧은 무음을 넣어 첫 음절 잘림 방지
 */
public final class AndroidTtsSpeaker implements Speaker, TextToSpeech.OnInitListener {

    private static final String TAG = "FlowCoachTts";
    private static final long PREROLL_SILENCE_MS = 300;

    private final AudioManager audioManager;
    private final AudioAttributes attributes;
    private final AudioFocusRequest focusRequest;
    private final TextToSpeech tts;

    private volatile boolean ready = false;
    private volatile String status = "음성 엔진 준비 중";
    private String pendingText;
    private int utteranceSeq = 0;

    public AndroidTtsSpeaker(Context context) {
        Context app = context.getApplicationContext();
        audioManager = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .build();
        tts = new TextToSpeech(app, this);
    }

    @Override
    public void onInit(int status) {
        // 엔진이 하나도 없으면 생성자 안에서 바로 onInit(ERROR)가 불려 tts가 아직 null일 수 있다.
        if (tts == null) {
            this.status = "음성 엔진 없음";
            Log.w(TAG, this.status);
            return;
        }
        StringBuilder engines = new StringBuilder();
        for (TextToSpeech.EngineInfo e : tts.getEngines()) {
            engines.append(e.name).append(' ');
        }
        Log.i(TAG, "설치된 TTS 엔진: [" + engines.toString().trim() + "]");

        if (status != TextToSpeech.SUCCESS) {
            this.status = engines.length() == 0 ? "음성 엔진 없음" : "음성 엔진 초기화 실패(" + status + ")";
            Log.w(TAG, this.status);
            return;
        }
        int result = tts.setLanguage(Locale.KOREAN);
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            this.status = "한국어 음성 없음(" + tts.getDefaultEngine() + ")";
            Log.w(TAG, this.status);
            return;
        }
        tts.setAudioAttributes(attributes);
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
            }

            @Override
            public void onDone(String utteranceId) {
                if (!tts.isSpeaking()) {
                    audioManager.abandonAudioFocusRequest(focusRequest);
                }
            }

            @Override
            public void onError(String utteranceId) {
                audioManager.abandonAudioFocusRequest(focusRequest);
            }
        });
        ready = true;
        this.status = "음성 안내 켜짐(" + tts.getDefaultEngine() + ")";
        Log.i(TAG, this.status);
        String text = pendingText;
        pendingText = null;
        if (text != null) {
            speak(null, text);
        }
    }

    public boolean isReady() {
        return ready;
    }

    @Override
    public boolean speak(String voiceKey, String text) {
        if (text == null || text.isEmpty()) return false;
        if (!ready) {
            pendingText = text; // 엔진 준비되면 바로 말함
            return false;
        }
        audioManager.requestAudioFocus(focusRequest);
        String id = "flowcoach-" + (utteranceSeq++);
        tts.playSilentUtterance(PREROLL_SILENCE_MS, TextToSpeech.QUEUE_FLUSH, id + "-preroll");
        tts.speak(text, TextToSpeech.QUEUE_ADD, null, id);
        return true;
    }

    @Override
    public String status() {
        return status;
    }

    @Override
    public void shutdown() {
        tts.stop();
        tts.shutdown();
        audioManager.abandonAudioFocusRequest(focusRequest);
    }
}
