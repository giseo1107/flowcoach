package com.giseo.flowcoach.voice;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;

/**
 * 미리 녹음한 음성 파일(res/raw/vc_{키}.m4a)을 재생한다.
 * Pleos 에뮬레이터에는 TTS 엔진이 없어서 시연용으로 필요하다.
 * 오디오 규칙은 TTS와 같다: 길 안내 음성 용도 + 잠깐 오디오 포커스(음악 볼륨 낮춤).
 */
public final class ClipSpeaker implements Speaker {

    private static final String TAG = "FlowCoachClip";

    private final Context app;
    private final AudioManager audioManager;
    private final AudioAttributes attributes;
    private final AudioFocusRequest focusRequest;
    private final int clipCount;

    private MediaPlayer player;

    public ClipSpeaker(Context context) {
        app = context.getApplicationContext();
        audioManager = (AudioManager) app.getSystemService(Context.AUDIO_SERVICE);
        attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();
        focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                .setAudioAttributes(attributes)
                .build();

        int count = 0;
        for (String key : VoicePrompts.LINES.keySet()) {
            if (resIdFor(key) != 0) count++;
        }
        clipCount = count;
        Log.i(TAG, "음성 파일 " + clipCount + "/" + VoicePrompts.LINES.size() + "개");
    }

    private int resIdFor(String key) {
        // 음성 파일은 스크립트로 나중에 생성되므로 R.raw 대신 이름으로 찾는다.
        return app.getResources().getIdentifier("vc_" + key, "raw", app.getPackageName());
    }

    @Override
    public boolean speak(String voiceKey, String text) {
        if (voiceKey == null || voiceKey.isEmpty()) return false;
        int resId = resIdFor(voiceKey);
        if (resId == 0) {
            Log.w(TAG, "음성 파일 없음: vc_" + voiceKey);
            return false;
        }
        stopCurrent(); // 새 안내가 오면 이전 안내는 끊는다

        MediaPlayer mp = new MediaPlayer();
        try (AssetFileDescriptor afd = app.getResources().openRawResourceFd(resId)) {
            mp.setAudioAttributes(attributes);
            mp.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            mp.setOnCompletionListener(p -> finish(p));
            mp.setOnErrorListener((p, what, extra) -> {
                Log.w(TAG, "재생 오류 " + what + "/" + extra);
                finish(p);
                return true;
            });
            mp.prepare();
            audioManager.requestAudioFocus(focusRequest);
            mp.start();
            player = mp;
            return true;
        } catch (Exception e) {
            Log.w(TAG, "재생 실패: vc_" + voiceKey, e);
            mp.release();
            return false;
        }
    }

    private void finish(MediaPlayer p) {
        p.release();
        if (player == p) player = null;
        audioManager.abandonAudioFocusRequest(focusRequest);
    }

    private void stopCurrent() {
        if (player != null) {
            try {
                player.stop();
            } catch (IllegalStateException ignored) {
            }
            finish(player);
        }
    }

    public int getClipCount() {
        return clipCount;
    }

    @Override
    public String status() {
        return clipCount > 0
                ? "녹음 음성으로 안내 (" + clipCount + "/" + VoicePrompts.LINES.size() + ")"
                : "녹음 음성 파일 없음";
    }

    @Override
    public void shutdown() {
        stopCurrent();
    }
}
