package com.giseo.flowcoach.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.giseo.flowcoach.R;
import com.giseo.flowcoach.data.demo.DemoScene;
import com.giseo.flowcoach.data.route.RoutePlanner;
import com.giseo.flowcoach.data.route.RouteScenario;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 실제 도로 시연용 경로 미니맵: 지도 그림 없이 실제 경로 모양을 선으로 그린다.
 *  - 신호 없는 도로(고속도로·자동차전용도로)는 진한 굵은 선, 시내는 옅은 파란 선, 지나온 길은 흐린 회색
 *  - 상황 지점은 작은 점. 지금 상황·다음 상황만 번호 배지로 크게, 선 옆으로 비켜서(몰려 있거나 지금 위치와 겹쳐도 보이게)
 *  - 지금 위치는 큰 파란 점
 *  - 아래: 선 색 설명 한 줄 + "다음 ③ 흐름보다 느림 · 1.2km" 한 줄
 */
public class RouteMiniMapView extends View {

    private static final int SAMPLES = 160;

    private final Paint fast = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint city = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint done = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint marker = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerDone = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint markerText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint here = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint status = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float density;
    private final float captionSize;

    private RouteScenario route;
    private List<RoutePlanner.Step> steps = new ArrayList<>();
    private double meters;
    private DemoScene active;

    public RouteMiniMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        captionSize = getResources().getDimension(R.dimen.text_caption);
        bg.setColor(color(R.color.surface));
        fast.setColor(color(R.color.route_fast));
        city.setColor(color(R.color.route_city));
        done.setColor(color(R.color.route_done));
        for (Paint p : new Paint[]{fast, city, done}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        marker.setColor(color(R.color.state_slow));
        markerDone.setColor(color(R.color.route_done));
        markerText.setColor(color(R.color.on_accent));
        markerText.setTextAlign(Paint.Align.CENTER);
        markerText.setFakeBoldText(true);
        here.setColor(color(R.color.accent));
        ring.setColor(color(R.color.bg));
        ring.setStyle(Paint.Style.STROKE);
        label.setColor(color(R.color.text_secondary));
        status.setColor(color(R.color.text_primary));
        status.setFakeBoldText(true);
    }

    public void setRoute(RouteScenario route, RoutePlanner.Plan plan) {
        this.route = route;
        this.steps = plan != null ? plan.steps : new ArrayList<>();
        this.meters = 0;
        this.active = null;
        invalidate();
    }

    /** 달린 거리(m)와 지금 보여주는 상황(상황 사이면 null). */
    public void setProgress(double meters, DemoScene activeScene) {
        this.meters = meters;
        this.active = activeScene;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        canvas.drawRoundRect(0, 0, w, h, dp(16), dp(16), bg);
        if (route == null) return;

        float pad = dp(26);
        float lineH = captionSize * 1.45f;
        float textTop = h - dp(12) - lineH * 2; // 아래 두 줄 자리
        float top = pad;
        float bottom = textTop - dp(12);
        if (bottom - top < dp(40)) bottom = top + dp(40);

        // 경로를 화면에 맞춘다(위·경도 → 평면, 가로세로 비율 유지)
        double len = route.line.lengthMeters();
        double[][] pts = new double[SAMPLES + 1][];
        double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, minY = Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        double k = Math.cos(Math.toRadians(route.line.at(0)[0]));
        for (int i = 0; i <= SAMPLES; i++) {
            double[] p = route.line.at(len * i / SAMPLES);
            double x = p[1] * k;
            double y = -p[0];
            pts[i] = new double[]{x, y};
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }
        double spanX = Math.max(1e-6, maxX - minX);
        double spanY = Math.max(1e-6, maxY - minY);
        double scale = Math.min((w - 2 * pad) / spanX, (bottom - top) / spanY);
        double ox = (w - spanX * scale) / 2 - minX * scale;
        double oy = top + ((bottom - top) - spanY * scale) / 2 - minY * scale;

        // 선: 지나온 길 → 시내 → 신호 없는 도로 순서로(굵은 선이 위에 오게)
        float stroke = dp(10);
        fast.setStrokeWidth(stroke);
        city.setStrokeWidth(stroke * 0.7f);
        done.setStrokeWidth(stroke * 0.6f);
        for (int pass = 0; pass < 3; pass++) {
            for (int i = 1; i <= SAMPLES; i++) {
                double m = len * (i - 0.5) / SAMPLES;
                boolean passed = m < meters;
                boolean signalFree = route.spanAt(m).allowsSituations();
                int kind = passed ? 0 : signalFree ? 2 : 1;
                if (kind != pass) continue;
                Paint p = kind == 0 ? done : kind == 1 ? city : fast;
                canvas.drawLine((float) (ox + pts[i - 1][0] * scale), (float) (oy + pts[i - 1][1] * scale),
                        (float) (ox + pts[i][0] * scale), (float) (oy + pts[i][1] * scale), p);
            }
        }

        // 상황 지점: 모두 작은 점, 지금·다음 상황만 번호 배지
        int activeIdx = activeIndex();
        int nextIdx = nextIndex();
        for (int i = 0; i < steps.size(); i++) {
            if (i == activeIdx || i == nextIdx) continue;
            float[] xy = project(steps.get(i).startMeters, k, scale, ox, oy);
            boolean passed = steps.get(i).startMeters <= meters;
            canvas.drawCircle(xy[0], xy[1], dp(6), passed ? markerDone : marker);
        }
        // 지금 위치
        float[] me = project(meters, k, scale, ox, oy);
        ring.setStrokeWidth(dp(4));
        canvas.drawCircle(me[0], me[1], dp(13), here);
        canvas.drawCircle(me[0], me[1], dp(13), ring);

        // 지금·다음 상황 배지: 선 옆으로 비켜 놓고 짧은 줄로 지점과 잇는다(지금 위치 점에 가리지 않게)
        if (nextIdx >= 0) badgeBeside(canvas, nextIdx, k, scale, ox, oy, w, bottom, 1);
        if (activeIdx >= 0) badgeBeside(canvas, activeIdx, k, scale, ox, oy, w, bottom, -1);

        // 아래 1줄: 선 색 설명
        float x0 = dp(14);
        float y1 = textTop + lineH * 0.8f;
        label.setTextSize(captionSize * 0.9f);
        float sw = dp(22);
        float legendW = sw + dp(6) + label.measureText(getContext().getString(R.string.map_legend_fast))
                + dp(16) + sw + dp(6) + label.measureText(getContext().getString(R.string.map_legend_city));
        float maxW = w - x0 * 2;
        if (legendW > maxW) {
            label.setTextSize(label.getTextSize() * maxW / legendW);
            sw *= maxW / legendW;
        }
        float cy = y1 - label.getTextSize() * 0.35f;
        float x = x0;
        fast.setStrokeWidth(dp(7));
        canvas.drawLine(x, cy, x + sw, cy, fast);
        x += sw + dp(6);
        String a = getContext().getString(R.string.map_legend_fast);
        canvas.drawText(a, x, y1, label);
        x += label.measureText(a) + dp(16);
        city.setStrokeWidth(dp(6));
        canvas.drawLine(x, cy, x + sw, cy, city);
        x += sw + dp(6);
        canvas.drawText(getContext().getString(R.string.map_legend_city), x, y1, label);

        // 아래 2줄: 지금/다음 상황
        String text = statusText(activeIdx, nextIdx);
        status.setTextSize(captionSize);
        float tw = status.measureText(text);
        if (tw > maxW) status.setTextSize(Math.max(captionSize * 0.7f, captionSize * maxW / tw));
        canvas.drawText(ellipsize(text, status, maxW), x0, y1 + lineH, status);
    }

    /**
     * 상황 지점(작은 점)에서 경로의 옆 방향으로 비켜 번호 배지를 그린다.
     * side: 1이면 진행 방향 오른쪽, -1이면 왼쪽(지금·다음 배지가 서로 반대쪽에 놓이게). 화면 밖이면 반대쪽.
     */
    private void badgeBeside(Canvas canvas, int index, double k, double scale, double ox, double oy,
                             float w, float bottom, int side) {
        double at = steps.get(index).startMeters;
        double len = route.line.lengthMeters();
        double look = Math.max(150, len * 0.02);
        float[] p = project(at, k, scale, ox, oy);
        float[] a = project(Math.max(0, at - look), k, scale, ox, oy);
        float[] b = project(Math.min(len, at + look), k, scale, ox, oy);
        float dx = b[0] - a[0];
        float dy = b[1] - a[1];
        float n = (float) Math.hypot(dx, dy);
        if (n < 1e-3f) {
            dx = 0;
            dy = -1;
            n = 1;
        }
        // 진행 방향 (dx, dy)의 오른쪽 법선(화면 좌표, y 아래로) = (-dy, dx)
        float nx = -dy / n * side;
        float ny = dx / n * side;
        float rad = dp(15);
        float off = dp(34);
        float bx = p[0] + nx * off;
        float by = p[1] + ny * off;
        if (bx < rad || bx > w - rad || by < rad || by > bottom) {
            bx = p[0] - nx * off;
            by = p[1] - ny * off;
        }
        marker.setStrokeWidth(dp(3));
        canvas.drawLine(p[0], p[1], bx, by, marker);
        canvas.drawCircle(p[0], p[1], dp(6), marker);
        ring.setStrokeWidth(dp(3));
        canvas.drawCircle(bx, by, rad, marker);
        canvas.drawCircle(bx, by, rad, ring);
        markerText.setTextSize(dp(17));
        canvas.drawText(String.valueOf(index + 1), bx, by + dp(6), markerText);
    }

    /** 지금 보여주는 상황의 번호(0부터). 상황 사이면 -1. */
    private int activeIndex() {
        if (active == null) return -1;
        for (int i = steps.size() - 1; i >= 0; i--) {
            if (steps.get(i).startMeters <= meters && steps.get(i).scene == active) return i;
        }
        return -1;
    }

    /** 아직 시작하지 않은 첫 상황. 없으면 -1. */
    private int nextIndex() {
        for (int i = 0; i < steps.size(); i++) {
            if (steps.get(i).startMeters > meters) return i;
        }
        return -1;
    }

    private String statusText(int activeIdx, int nextIdx) {
        if (activeIdx >= 0) {
            return String.format(Locale.KOREA, "지금 %d  %s", activeIdx + 1, steps.get(activeIdx).scene.title);
        }
        if (nextIdx >= 0) {
            RoutePlanner.Step s = steps.get(nextIdx);
            return String.format(Locale.KOREA, "다음 %d  %s · %.1fkm", nextIdx + 1, s.scene.title,
                    Math.max(0, s.startMeters - meters) / 1000.0);
        }
        double left = Math.max(0, route.line.lengthMeters() - meters) / 1000.0;
        return String.format(Locale.KOREA, "도착까지 %.1fkm", left);
    }

    private static String ellipsize(String text, Paint p, float max) {
        if (p.measureText(text) <= max) return text;
        String s = text;
        while (s.length() > 1 && p.measureText(s + "…") > max) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    private float[] project(double atMeters, double k, double scale, double ox, double oy) {
        double[] p = route.line.at(atMeters);
        return new float[]{(float) (ox + p[1] * k * scale), (float) (oy - p[0] * scale)};
    }

    private int color(int res) {
        return ContextCompat.getColor(getContext(), res);
    }

    private float dp(float v) {
        return v * density;
    }
}
