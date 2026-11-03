package com.giseo.flowcoach.voice;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/**
 * "지금 어때?" 음성 질문 인식.
 * 지금은 Android 기본 음성 인식(SpeechRecognizer)을 쓰고,
 * Pleos SpeechToText SDK가 열리면 같은 역할로 교체한다.
 * 인식 서비스가 없는 환경(일부 에뮬레이터)에서는 버튼만으로 바로 답한다.
 */
public final class VoiceQuery {

    public interface Callback {
        /** 인식한 문장. 못 알아들었으면 null. 메인 스레드에서 호출된다. */
        void onHeard(String text);
    }

    /** 이 시간 안에 결과가 없으면 못 알아들은 것으로 보고 바로 답한다(인식 서비스가 응답하지 않는 환경 대비). */
    private static final long TIMEOUT_MS = 7_000;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    /** 지금 듣고 있는 요청. 결과는 한 번만 넘긴다. */
    private Runnable pendingTimeout;

    public VoiceQuery(Context context) {
        this.context = context;
    }

    public static boolean isAvailable(Context context) {
        return SpeechRecognizer.isRecognitionAvailable(context);
    }

    public void listen(Callback callback) {
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context);
        }
        final boolean[] done = {false};
        final Callback once = text -> {
            if (done[0]) return;
            done[0] = true;
            if (pendingTimeout != null) handler.removeCallbacks(pendingTimeout);
            pendingTimeout = null;
            callback.onHeard(text);
        };
        pendingTimeout = () -> {
            if (recognizer != null) recognizer.cancel();
            once.onHeard(null);
        };
        handler.postDelayed(pendingTimeout, TIMEOUT_MS);
        recognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { }
            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }

            @Override
            public void onError(int error) {
                once.onHeard(null);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> list = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                once.onHeard(list != null && !list.isEmpty() ? list.get(0) : null);
            }
        });

        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
                .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        recognizer.startListening(intent);
    }

    public void destroy() {
        if (pendingTimeout != null) handler.removeCallbacks(pendingTimeout);
        pendingTimeout = null;
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
    }
}
