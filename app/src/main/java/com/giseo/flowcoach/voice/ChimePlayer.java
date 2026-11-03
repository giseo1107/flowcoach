package com.giseo.flowcoach.voice;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

/**
 * 조용한 모드의 위험 경고음: 말 대신 짧은 "띵-동" 두 음.
 *
 * 차에 원래 있는 과속 경고음처럼 짧게 주의만 끈다. 음성 안내와 같은 길(내비 안내 음성 채널)로 내보내서
 * 음악이 나오고 있으면 잠깐 줄였다가 돌려놓는다. 소리는 파일 없이 코드로 만든다(ChimeTone).
 */
public final class ChimePlayer {

    private static final String TAG = "ChimePlayer";
    /** 같은 경고음이 연달아 울리지 않게 하는 최소 간격. */
    private static final long MIN_GAP_MS = 3_000;

    private final AudioManager audioManager;
    private final AudioAttributes attributes;
    private final AudioFocusRequest focusRequest;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final short[] pcm = ChimeTone.pcm(ChimeTone.SAMPLE_RATE);
    private long lastPlayMs = -MIN_GAP_MS;

    public ChimePlayer(Context context) {
        audioManager = context.getSystemService(AudioManager.class);
        attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .build();
    }

    /** 경고음 한 번. 3초 안에 다시 부르면 무시한다. */
    public void play() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastPlayMs < MIN_GAP_MS) return;
        lastPlayMs = now;
        try {
            AudioTrack track = new AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(new AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(ChimeTone.SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(pcm.length * 2)
                    .build();
            track.write(pcm, 0, pcm.length);
            if (audioManager != null) audioManager.requestAudioFocus(focusRequest);
            track.play();
            handler.postDelayed(() -> {
                track.release();
                if (audioManager != null) audioManager.abandonAudioFocusRequest(focusRequest);
            }, ChimeTone.durationMs() + 150);
        } catch (RuntimeException e) {
            Log.w(TAG, "경고음 재생 실패", e);
        }
    }
}
