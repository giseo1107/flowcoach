package com.giseo.flowcoach.voice;

/**
 * 경고음 파형을 만든다(안드로이드 없이 테스트할 수 있게 분리).
 * "띵(C6, 0.15초) - 동(E6, 0.25초)": 짧게 올라가는 두 음이라 놀라게 하지 않으면서 귀에 띈다.
 */
public final class ChimeTone {

    public static final int SAMPLE_RATE = 44_100;
    private static final double[] FREQ_HZ = {1046.5, 1318.5};
    private static final int[] LENGTH_MS = {150, 250};
    private static final double VOLUME = 0.45;

    private ChimeTone() {
    }

    public static int durationMs() {
        int sum = 0;
        for (int ms : LENGTH_MS) sum += ms;
        return sum;
    }

    /** 16비트 모노 PCM. 각 음은 짧게 켜지고(5ms) 종소리처럼 줄어든다. */
    public static short[] pcm(int sampleRate) {
        int total = sampleRate * durationMs() / 1000;
        short[] out = new short[total];
        int pos = 0;
        for (int n = 0; n < FREQ_HZ.length; n++) {
            int len = sampleRate * LENGTH_MS[n] / 1000;
            int attack = sampleRate * 5 / 1000;
            for (int i = 0; i < len && pos < total; i++, pos++) {
                double t = (double) i / sampleRate;
                double env = Math.exp(-t * 9.0);                 // 종소리처럼 줄어듦
                if (i < attack) env *= (double) i / attack;      // 딸깍 소리 없이 시작
                if (i > len - attack) env *= (double) (len - i) / attack; // 딸깍 소리 없이 끝
                double v = Math.sin(2 * Math.PI * FREQ_HZ[n] * t)
                        + 0.3 * Math.sin(2 * Math.PI * FREQ_HZ[n] * 2 * t); // 배음으로 맑게
                out[pos] = (short) Math.round(VOLUME * env * v / 1.3 * Short.MAX_VALUE);
            }
        }
        return out;
    }
}
