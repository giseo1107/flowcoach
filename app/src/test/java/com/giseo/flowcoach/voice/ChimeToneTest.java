package com.giseo.flowcoach.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 조용한 모드 경고음 파형: 길이, 찢어지는 소리(클리핑) 없음, 딸깍 소리 없이 시작·끝. */
public class ChimeToneTest {

    @Test
    public void 길이는_0점4초() {
        short[] pcm = ChimeTone.pcm(ChimeTone.SAMPLE_RATE);
        assertEquals(400, ChimeTone.durationMs());
        assertEquals(ChimeTone.SAMPLE_RATE * 400 / 1000, pcm.length);
    }

    @Test
    public void 소리가_찢어지지_않고_조용히_시작하고_끝난다() {
        short[] pcm = ChimeTone.pcm(ChimeTone.SAMPLE_RATE);
        int peak = 0;
        for (short v : pcm) peak = Math.max(peak, Math.abs(v));
        assertTrue("너무 작음 " + peak, peak > Short.MAX_VALUE * 0.2);
        assertTrue("클리핑 " + peak, peak < Short.MAX_VALUE * 0.6);
        assertTrue(Math.abs(pcm[0]) < 200);
        assertTrue(Math.abs(pcm[pcm.length - 1]) < 2000);
    }
}
