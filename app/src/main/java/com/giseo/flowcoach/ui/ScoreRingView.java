package com.giseo.flowcoach.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.content.ContextCompat;

import com.giseo.flowcoach.R;

/**
 * 리포트의 흐름 점수 링: 위에서 시계 방향으로 점수만큼 채운다(100점 = 한 바퀴).
 * 가운데에 점수(크게)와 등급 문장. 처음 보일 때 0점부터 차오른다.
 * 색: 75점 이상 초록, 50점 이상 주황, 그 아래 빨강(게이지와 같은 상태 색).
 */
public class ScoreRingView extends View {

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unit = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gradePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private int score;
    private float shown; // 애니메이션 중 지금 보이는 점수
    private String grade = "";
    private ValueAnimator animator;

    public ScoreRingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        track.setColor(color(R.color.gauge_track));
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        number.setColor(color(R.color.text_primary));
        number.setTextAlign(Paint.Align.CENTER);
        number.setFakeBoldText(true);
        unit.setColor(color(R.color.text_secondary));
        gradePaint.setTextAlign(Paint.Align.CENTER);
        gradePaint.setFakeBoldText(true);
    }

    /** 점수와 등급 문장. animate면 0점부터 차오른다. */
    public void setScore(int score, String grade, boolean animate) {
        this.score = Math.max(0, Math.min(100, score));
        this.grade = grade == null ? "" : grade;
        int c = color(colorFor(this.score));
        arc.setColor(c);
        gradePaint.setColor(c);
        if (animator != null) animator.cancel();
        if (!animate) {
            shown = this.score;
            invalidate();
            return;
        }
        animator = ValueAnimator.ofFloat(0, this.score);
        animator.setDuration(900);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            shown = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    /** 점수 구간별 상태 색. */
    public static int colorFor(int score) {
        if (score >= 75) return R.color.state_smooth;
        if (score >= 50) return R.color.state_slow;
        return R.color.state_over;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        // 받은 자리를 그대로 쓰고, 링은 그 안의 가장 큰 정사각형 가운데에 그린다.
        // 높이를 정하지 않으면(스크롤 안) 가로만큼.
        int w = MeasureSpec.getSize(widthMeasureSpec);
        int hMode = MeasureSpec.getMode(heightMeasureSpec);
        int h = MeasureSpec.getSize(heightMeasureSpec);
        if (hMode == MeasureSpec.UNSPECIFIED) h = w;
        else if (hMode == MeasureSpec.AT_MOST) h = Math.min(w, h);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float size = Math.min(getWidth(), getHeight());
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float stroke = size * 0.07f;
        float r = size / 2f - stroke;
        track.setStrokeWidth(stroke);
        arc.setStrokeWidth(stroke);
        oval.set(cx - r, cy - r, cx + r, cy + r);
        canvas.drawCircle(cx, cy, r, track);
        float sweep = 360f * shown / 100f;
        if (sweep > 0.5f) canvas.drawArc(oval, -90, sweep, false, arc);

        // 가운데: 점수 + "점", 아래 등급 문장
        String n = String.valueOf(Math.round(shown));
        number.setTextSize(r * 0.78f);
        unit.setTextSize(r * 0.18f);
        float nw = number.measureText(n);
        float uw = unit.measureText("점");
        float baseline = cy + r * 0.16f;
        canvas.drawText(n, cx - uw / 2f - r * 0.02f, baseline, number);
        canvas.drawText("점", cx + nw / 2f - uw / 2f + r * 0.02f, baseline, unit);

        gradePaint.setTextSize(r * 0.17f);
        float max = r * 1.5f;
        float gw = gradePaint.measureText(grade);
        if (gw > max) gradePaint.setTextSize(gradePaint.getTextSize() * max / gw);
        canvas.drawText(grade, cx, cy + r * 0.48f, gradePaint);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    private int color(int res) {
        return ContextCompat.getColor(getContext(), res);
    }
}
