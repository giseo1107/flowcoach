package com.giseo.flowcoach.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.giseo.flowcoach.R;

/**
 * 리포트의 시간 비율 막대: 흐름에 맞음(초록) · 흐름보다 느림(주황) · 제한속도 초과(빨강) · 정체(회색, 평가 제외).
 * 둥근 막대 하나를 비율대로 나눠 칠한다. 아주 짧은 구간도 보이게 최소 폭을 준다.
 */
public class TimeShareBarView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint seg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Path clip = new Path();
    private final int[] colors;
    private double[] values = new double[4];

    public TimeShareBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
        track.setColor(color(R.color.gauge_track));
        colors = new int[]{color(R.color.state_smooth), color(R.color.state_slow), color(R.color.state_over),
                color(R.color.state_unknown)};
    }

    /** 초 단위: 흐름에 맞음, 느림, 초과, 정체. */
    public void setShares(double smoothSec, double slowSec, double overSec, double congestedSec) {
        values = new double[]{smoothSec, slowSec, overSec, congestedSec};
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float rad = h / 2f;
        r.set(0, 0, w, h);
        canvas.drawRoundRect(r, rad, rad, track);
        double total = 0;
        for (double v : values) total += Math.max(0, v);
        if (total <= 0) return;

        // 0이 아닌 구간은 최소 폭(막대 높이의 절반)을 받고, 나머지를 비율대로 나눈다
        float minW = h * 0.5f;
        int nonZero = 0;
        for (double v : values) if (v > 0) nonZero++;
        float free = Math.max(0, w - minW * nonZero);
        canvas.save();
        clip.reset();
        clip.addRoundRect(r, rad, rad, Path.Direction.CW);
        canvas.clipPath(clip);
        float x = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i] <= 0) continue;
            float segW = minW + (float) (free * values[i] / total);
            seg.setColor(colors[i]);
            canvas.drawRect(x, 0, Math.min(w, x + segW), h, seg);
            x += segW;
        }
        canvas.restore();
    }

    private int color(int res) {
        return ContextCompat.getColor(getContext(), res);
    }
}
