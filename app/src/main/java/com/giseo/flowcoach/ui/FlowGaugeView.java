package com.giseo.flowcoach.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.core.content.ContextCompat;

import com.giseo.flowcoach.R;
import com.giseo.flowcoach.domain.TurnSignal;

/**
 * 코치 패널의 중심: 원형 흐름 게이지.
 *
 *          ╭── 초록 = 권장 구간 ──┃제한──╮   ← 제한 넘는 구간은 옅은 빨강
 *        ╭─╯                        ╰─╮
 *        │  ◀        96        ▶     │   ← 지금 속도(크게) · 방향지시등
 *        │          km/h              │
 *         ╰─╮                    ╭─╯
 *            흐름에 잘 맞아요            ← 상태 문장(상태 색)
 *
 * 점(●)이 지금 속도 위치다. 글자를 읽지 않아도 '점이 초록 구간 안에 있으면 OK'로 알 수 있게 한다.
 * 창 크기(내비 옆 반쪽 세로 / 혼자 가로)에 따라 글자 크기도 게이지 크기에 맞춰 같이 커지고 작아진다.
 */
public class FlowGaugeView extends View {

    /** 원호가 시작하는 각도(안드로이드 기준 3시=0°, 시계 방향). 8시 방향에서 시작해 4시 방향에서 끝난다. */
    private static final float START_DEG = 150f;
    private static final float SWEEP_DEG = 240f;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zonePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint limitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint recPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint speedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint unitPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint statusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint signalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();
    private final RectF innerRect = new RectF();
    private final float density;
    private final String unit;

    private double mySpeed = 0;
    /** 점이 부드럽게 움직이도록 화면에 그리는 속도(애니메이션 중간값). */
    private float shownSpeed = 0;
    private double recommended = 0;
    private int limit = 100;
    private double slowMargin = 15;
    private int stateColor;
    private String status = "";
    private TurnSignal signal = TurnSignal.OFF;
    private ValueAnimator animator;

    public FlowGaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        unit = context.getString(R.string.unit_kmh);
        stateColor = color(R.color.state_unknown);

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(color(R.color.gauge_track));

        zonePaint.setStyle(Paint.Style.STROKE);
        zonePaint.setColor(color(R.color.gauge_zone));

        overPaint.setStyle(Paint.Style.STROKE);
        overPaint.setColor(color(R.color.gauge_over));

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);

        limitPaint.setColor(color(R.color.state_over));
        limitPaint.setStrokeCap(Paint.Cap.ROUND);
        recPaint.setColor(color(R.color.text_primary));
        recPaint.setStrokeCap(Paint.Cap.ROUND);

        markerStroke.setStyle(Paint.Style.STROKE);
        markerStroke.setColor(color(R.color.bg)); // 배경색 테두리 → 점이 원호에서 도려낸 것처럼 또렷하게(낮·밤 모두)

        Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
        speedPaint.setTypeface(bold);
        speedPaint.setTextAlign(Paint.Align.CENTER);
        speedPaint.setColor(color(R.color.text_primary));
        speedPaint.setFontFeatureSettings("tnum"); // 숫자 폭을 같게 해서 속도가 바뀔 때 흔들리지 않게

        unitPaint.setTextAlign(Paint.Align.CENTER);
        unitPaint.setColor(color(R.color.text_secondary));

        statusPaint.setTypeface(bold);
        statusPaint.setTextAlign(Paint.Align.CENTER);

        signalPaint.setTextAlign(Paint.Align.CENTER);
        signalPaint.setColor(color(R.color.state_slow));
    }

    /**
     * @param stateColor 흐름 상태 색(점·안쪽 띠·상태 문장)
     * @param status     게이지 아래에 보일 상태 문장
     */
    public void setValues(double mySpeed, double recommended, int limit, double slowMargin,
                          int stateColor, String status) {
        this.recommended = recommended;
        this.limit = limit;
        this.slowMargin = slowMargin;
        this.stateColor = stateColor;
        this.status = status == null ? "" : status;
        animateSpeed(mySpeed);
        invalidate();
    }

    public void setSignal(TurnSignal signal) {
        if (this.signal == signal) return;
        this.signal = signal == null ? TurnSignal.OFF : signal;
        invalidate();
    }

    private void animateSpeed(double target) {
        mySpeed = target;
        if (animator != null) animator.cancel();
        if (!isAttachedToWindow() || Math.abs(target - shownSpeed) < 0.5) {
            shownSpeed = (float) target;
            return;
        }
        animator = ValueAnimator.ofFloat(shownSpeed, (float) target);
        animator.setDuration(600);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(a -> {
            shownSpeed = (float) a.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (animator != null) animator.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float pad = dp(8);
        float w = getWidth() - getPaddingLeft() - getPaddingRight() - pad * 2;
        float h = getHeight() - getPaddingTop() - getPaddingBottom() - pad * 2;
        if (w <= 0 || h <= 0) return;

        // 원호 위쪽 끝 ~ 상태 문장 아래까지 세로로 약 1.85r 필요
        float r = Math.min(w / 2f, h / 1.85f);
        float stroke = r * 0.13f;
        r -= stroke / 2f;
        float cx = getPaddingLeft() + pad + w / 2f;
        float top = getPaddingTop() + pad + (h - r * 1.85f - stroke / 2f) / 2f;
        float cy = top + stroke / 2f + r;

        arcRect.set(cx - r, cy - r, cx + r, cy + r);
        trackPaint.setStrokeWidth(stroke);
        zonePaint.setStrokeWidth(stroke);
        overPaint.setStrokeWidth(stroke);

        double max = scaleMax();

        // 1) 전체 트랙
        canvas.drawArc(arcRect, START_DEG, SWEEP_DEG, false, trackPaint);
        // 2) 제한속도를 넘는 구간(옅은 빨강)
        float limitDeg = deg(limit, max);
        canvas.drawArc(arcRect, limitDeg, START_DEG + SWEEP_DEG - limitDeg, false, overPaint);
        // 3) 권장 구간: (권장 속도 - 여유) ~ 제한속도
        if (recommended > 0) {
            float zs = deg(Math.max(0, recommended - slowMargin), max);
            if (limitDeg > zs) canvas.drawArc(arcRect, zs, limitDeg - zs, false, zonePaint);
        }
        // 4) 안쪽 얇은 띠: 0 ~ 지금 속도(상태 색)
        float inner = r - stroke * 0.95f;
        innerRect.set(cx - inner, cy - inner, cx + inner, cy + inner);
        progressPaint.setStrokeWidth(Math.max(dp(4), stroke * 0.22f));
        progressPaint.setColor(stateColor);
        float speedDeg = deg(shownSpeed, max);
        if (shownSpeed > 0.5f) canvas.drawArc(innerRect, START_DEG, speedDeg - START_DEG, false, progressPaint);

        // 5) 제한속도 눈금(빨강)과 권장 속도 눈금(흰색)
        limitPaint.setStrokeWidth(Math.max(dp(3), stroke * 0.16f));
        tick(canvas, cx, cy, r, stroke * 0.85f, limitDeg, limitPaint);
        if (recommended > 0) {
            recPaint.setStrokeWidth(Math.max(dp(2), stroke * 0.1f));
            tick(canvas, cx, cy, r, stroke * 0.6f, deg(recommended, max), recPaint);
        }

        // 6) 지금 속도 점
        double a = Math.toRadians(speedDeg);
        float mx = cx + (float) (r * Math.cos(a));
        float my = cy + (float) (r * Math.sin(a));
        markerPaint.setColor(stateColor);
        markerStroke.setStrokeWidth(Math.max(dp(2), stroke * 0.14f));
        canvas.drawCircle(mx, my, stroke * 0.72f, markerPaint);
        canvas.drawCircle(mx, my, stroke * 0.72f, markerStroke);

        // 7) 가운데 글자: 지금 속도(크게) + km/h. 세 자리(100 이상)면 안쪽 띠에 닿지 않게 폭에 맞춰 줄인다
        String speed = String.valueOf(Math.round(mySpeed));
        float speedSize = r * 0.66f;
        speedPaint.setTextSize(speedSize);
        float speedMax = r * 1.05f;
        float sw = speedPaint.measureText(speed);
        if (sw > speedMax) speedPaint.setTextSize(speedSize * speedMax / sw);
        canvas.drawText(speed, cx, cy + r * 0.16f, speedPaint);
        unitPaint.setTextSize(r * 0.15f);
        canvas.drawText(unit, cx, cy + r * 0.38f, unitPaint);

        // 방향지시등: 속도 숫자 위 양쪽(계기판처럼)
        signalPaint.setTextSize(r * 0.2f);
        float signalY = cy - r * 0.42f;
        if (signal == TurnSignal.LEFT || signal == TurnSignal.HAZARD) {
            canvas.drawText("◀", cx - r * 0.4f, signalY, signalPaint);
        }
        if (signal == TurnSignal.RIGHT || signal == TurnSignal.HAZARD) {
            canvas.drawText("▶", cx + r * 0.4f, signalY, signalPaint);
        }

        // 8) 상태 문장: 원호 양 끝 사이(아래쪽 빈 곳). 길면 폭에 맞춰 줄인다
        statusPaint.setColor(stateColor);
        float size = r * 0.22f;
        statusPaint.setTextSize(size);
        float maxWidth = r * 1.5f;
        float tw = statusPaint.measureText(status);
        if (tw > maxWidth) statusPaint.setTextSize(size * maxWidth / tw);
        canvas.drawText(status, cx, cy + r * 0.78f, statusPaint);
    }

    /** 게이지 끝값: 제한속도보다 넉넉히, 20 단위로 올림. */
    private double scaleMax() {
        double m = Math.max(Math.max(limit + 40, mySpeed + 10), 120);
        return Math.ceil(m / 20.0) * 20.0;
    }

    private float deg(double speed, double max) {
        double v = Math.max(0, Math.min(speed, max));
        return START_DEG + (float) (v / max) * SWEEP_DEG;
    }

    private static void tick(Canvas c, float cx, float cy, float r, float half, float degrees, Paint p) {
        double a = Math.toRadians(degrees);
        float cos = (float) Math.cos(a);
        float sin = (float) Math.sin(a);
        c.drawLine(cx + (r - half) * cos, cy + (r - half) * sin, cx + (r + half) * cos, cy + (r + half) * sin, p);
    }

    private int color(int res) {
        return ContextCompat.getColor(getContext(), res);
    }

    private float dp(float v) {
        return v * density;
    }
}
