package com.giseo.flowcoach.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.giseo.flowcoach.R;
import com.giseo.flowcoach.domain.TurnSignal;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 시연용 차로 뷰: 위에서 내려다본 도로.
 *
 * 왼쪽 계기판(Pleos 시스템 화면)에는 실제 차의 ADAS 센서가 차로·주변 차를 그려 주지만 에뮬레이터에는 센서가 없어서
 * 비어 있다. 시연 중에는 이 뷰가 그 역할을 대신한다.
 *  - 내 차: 방향지시등이 켜졌다 꺼지면 그쪽 차로로 옮겨 간다(추월 → 1차로, 복귀 → 원래 차로)
 *  - 주변 차: 실제(또는 시나리오) 흐름 속도로 달린다. 내가 느리면 나를 앞질러 가고, 과속하면 내가 앞지른다
 *  - 차로선이 내 속도로 흘러가고, 급제동이면 브레이크등, 방향지시등은 깜빡인다
 * 판단에는 쓰지 않는 그림이다(판단은 CoachEngine).
 */
public class DemoLaneView extends View {

    /** 화면 세로 길이가 실제 몇 m인지. */
    private static final float VIEW_METERS = 70f;
    private static final float CAR_LENGTH_M = 4.6f;
    private static final int OTHER_CARS = 7;

    private final Paint road = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint other = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint brake = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blink = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelWarn = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint passingLane = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();
    private final Random random = new Random(7);
    private final float density;

    private int lanes = 4;
    private String roadName = "";
    /** 내 차로(0 = 가장 왼쪽 1차로). 그리기용은 부드럽게 따라가는 값. */
    private int myLane = 1;
    private float myLaneShown = 1;
    private float mySpeedKmh;
    private float flowKmh = 90;
    private TurnSignal signal = TurnSignal.OFF;
    private TurnSignal lastSignal = TurnSignal.OFF;
    private boolean braking;
    private float dashOffsetM;
    private long lastFrameMs;
    private boolean running;

    /** 주변 차: 차로와 내 차 기준 앞뒤 거리(m, +면 앞), 자기 속도. */
    private static final class Car {
        int lane;
        float aheadM;
        float speedKmh;
        int shade;
    }

    private final List<Car> cars = new ArrayList<>();

    public DemoLaneView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        road.setColor(color(R.color.lane_road));
        edge.setColor(color(R.color.lane_line));
        edge.setStrokeWidth(dp(4));
        dash.setColor(color(R.color.lane_line));
        dash.setStrokeWidth(dp(3));
        other.setColor(color(R.color.lane_car));
        mine.setColor(color(R.color.accent));
        glass.setColor(color(R.color.lane_road));
        brake.setColor(color(R.color.state_over));
        blink.setColor(color(R.color.state_slow));
        label.setColor(color(R.color.text_primary));
        label.setFakeBoldText(true);
        labelWarn.setColor(color(R.color.state_slow));
        labelWarn.setFakeBoldText(true);
        pill.setColor(color(R.color.lane_pill));
        passingLane.setColor(color(R.color.lane_passing));
        resetCars();
    }

    /** 시연이 시작될 때(또는 장면이 바뀔 때) 처음 상태로. */
    public void reset(int laneCount, String name) {
        lanes = Math.max(2, Math.min(5, laneCount));
        roadName = name == null ? "" : name;
        myLane = lanes >= 3 ? 1 : 0;
        myLaneShown = myLane;
        lastSignal = TurnSignal.OFF;
        resetCars();
        invalidate();
    }

    /** 지금 도로(차로 수·이름)가 바뀌었을 때. 내 차로는 범위 안으로 맞춘다. */
    public void setRoad(int laneCount, String name) {
        int n = Math.max(2, Math.min(5, laneCount));
        roadName = name == null ? "" : name;
        if (n != lanes) {
            lanes = n;
            myLane = Math.min(myLane, lanes - 1);
            resetCars();
        }
    }

    /** 1초마다(주행 틱) 들어오는 값. */
    public void update(double speedKmh, double flowSpeedKmh, TurnSignal turnSignal, boolean brakePressed) {
        mySpeedKmh = (float) speedKmh;
        if (flowSpeedKmh > 5) flowKmh = (float) flowSpeedKmh;
        signal = turnSignal == null ? TurnSignal.OFF : turnSignal;
        braking = brakePressed;
        // 신호가 켜졌다가 꺼지면 그쪽으로 한 차로 옮긴다
        if (lastSignal == TurnSignal.LEFT && signal == TurnSignal.OFF) moveTo(myLane - 1);
        if (lastSignal == TurnSignal.RIGHT && signal == TurnSignal.OFF) moveTo(myLane + 1);
        lastSignal = signal;
        if (!running) {
            running = true;
            lastFrameMs = SystemClock.uptimeMillis();
            postInvalidateOnAnimation();
        }
    }

    public void stop() {
        running = false;
    }

    private void moveTo(int lane) {
        int target = Math.max(0, Math.min(lanes - 1, lane));
        if (target == myLane) return;
        myLane = target;
        // 옮겨 간 차로 바로 옆에 있던 차는 앞뒤로 비켜 준다
        for (Car c : cars) {
            if (c.lane == myLane && Math.abs(c.aheadM) < 14) c.aheadM += c.aheadM >= 0 ? 22 : -22;
        }
    }

    private void resetCars() {
        cars.clear();
        for (int i = 0; i < OTHER_CARS; i++) cars.add(spawn(-VIEW_METERS * 0.5f + VIEW_METERS * i / OTHER_CARS));
    }

    private Car spawn(float aheadM) {
        Car c = new Car();
        // 내 차·다른 차와 겹치지 않는 차로를 고른다(못 찾으면 마지막 후보)
        for (int tries = 0; tries < 12; tries++) {
            c.lane = random.nextInt(lanes);
            if (laneFree(c.lane, aheadM)) break;
        }
        c.aheadM = aheadM;
        c.speedKmh = laneSpeed(c.lane);
        c.shade = random.nextInt(3);
        return c;
    }

    /** 그 차로의 그 위치에 내 차·다른 차가 없는지. */
    private boolean laneFree(int lane, float aheadM) {
        if (lane == myLane && Math.abs(aheadM) < 16) return false;
        for (Car o : cars) {
            if (o.lane == lane && Math.abs(o.aheadM - aheadM) < CAR_LENGTH_M + 8) return false;
        }
        return true;
    }

    /** 왼쪽 차로일수록 조금 빠르게(실제 고속도로처럼). */
    private float laneSpeed(int lane) {
        float spread = lanes <= 1 ? 0 : (lanes - 1 - lane) / (float) (lanes - 1) - 0.5f; // +0.5(왼쪽) ~ -0.5(오른쪽)
        return flowKmh + spread * 12f + (random.nextFloat() - 0.5f) * 4f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        long now = SystemClock.uptimeMillis();
        float dt = lastFrameMs == 0 ? 0 : Math.min(0.1f, (now - lastFrameMs) / 1000f);
        lastFrameMs = now;
        step(dt);

        float w = getWidth();
        float h = getHeight();
        float pxPerM = h / VIEW_METERS;
        float laneW = Math.min(w * 0.86f / lanes, dp(130));
        float roadW = laneW * lanes;
        float left = (w - roadW) / 2f;

        // 도로
        r.set(left - dp(10), 0, left + roadW + dp(10), h);
        canvas.drawRoundRect(r, dp(12), dp(12), road);
        // 추월차로(1차로)에 있으면 그 차로를 옅게 칠한다(추월 후 미복귀 장면이 눈에 보이게)
        boolean inPassingLane = myLane == 0 && lanes >= 3;
        if (inPassingLane) canvas.drawRect(left, 0, left + laneW, h, passingLane);
        canvas.drawLine(left, 0, left, h, edge);
        canvas.drawLine(left + roadW, 0, left + roadW, h, edge);
        // 차로선(점선): 내 속도로 아래로 흘러간다
        float period = 12f; // m: 선 6m + 빈칸 6m
        float off = (dashOffsetM % period) * pxPerM;
        for (int i = 1; i < lanes; i++) {
            float x = left + laneW * i;
            for (float y = -period * pxPerM + off; y < h; y += period * pxPerM) {
                canvas.drawLine(x, y, x, y + 6f * pxPerM, dash);
            }
        }

        // 주변 차
        float myY = h * 0.68f;
        float carW = laneW * 0.56f;
        float carL = CAR_LENGTH_M * pxPerM;
        for (Car c : cars) {
            float cx = left + laneW * (c.lane + 0.5f);
            float cy = myY - c.aheadM * pxPerM;
            if (cy < -carL || cy > h + carL) continue;
            other.setAlpha(170 + c.shade * 30);
            drawCar(canvas, cx, cy, carW, carL, other, false, TurnSignal.OFF);
        }

        // 내 차(차로 변경은 부드럽게)
        myLaneShown += (myLane - myLaneShown) * Math.min(1f, dt * 3f);
        float mx = left + laneW * (myLaneShown + 0.5f);
        drawCar(canvas, mx, myY, carW, carL, mine, braking, signal);

        // 위쪽 알약: "내 차 2차로 / 4차로" (+ 추월차로면 주황 글씨). 도로 이름은 위 줄(road_text)에 이미 있다
        drawPill(canvas, w, "내 차 " + (myLane + 1) + "차로 / " + lanes + "차로", inPassingLane ? "  추월차로" : "");

        if (running) postInvalidateOnAnimation();
    }

    private void step(float dt) {
        dashOffsetM += mySpeedKmh / 3.6f * dt;
        // 같은 차로의 앞차와 거리를 지킨다(가까우면 앞차 속도로 맞추고, 겹치면 뒤로 뺀다)
        float minGap = CAR_LENGTH_M + 2f;
        for (Car a : cars) {
            for (Car b : cars) {
                if (a == b || a.lane != b.lane) continue;
                float d = b.aheadM - a.aheadM; // b가 a 앞이면 +
                if (d >= 0 && d < minGap + 6f) a.speedKmh = Math.min(a.speedKmh, b.speedKmh);
                if (d >= 0 && d < minGap) a.aheadM = b.aheadM - minGap;
            }
        }
        for (int i = 0; i < cars.size(); i++) {
            Car c = cars.get(i);
            // 내 차 기준 상대 속도로 앞뒤로 움직인다
            c.aheadM += (c.speedKmh - mySpeedKmh) / 3.6f * dt;
            // 같은 차로에서 내 차와 겹치지 않게 속도를 맞춘다
            if (c.lane == myLane && c.aheadM > 0 && c.aheadM < 12) c.speedKmh = Math.max(c.speedKmh, mySpeedKmh);
            if (c.lane == myLane && c.aheadM < 0 && c.aheadM > -12) c.speedKmh = Math.min(c.speedKmh, mySpeedKmh);
            if (c.aheadM > VIEW_METERS * 0.45f || c.aheadM < -VIEW_METERS * 0.55f) {
                // 화면 밖으로 나가면 반대쪽에서 새 차가 들어온다
                float enter = c.aheadM > 0 ? -VIEW_METERS * 0.5f : VIEW_METERS * 0.42f;
                cars.remove(i);
                cars.add(i, spawn(enter));
            }
        }
    }

    private void drawPill(Canvas canvas, float w, String main, String warn) {
        float size = getResources().getDimension(R.dimen.text_label);
        label.setTextSize(size);
        labelWarn.setTextSize(size);
        float padX = dp(14);
        float tw = label.measureText(main) + labelWarn.measureText(warn);
        float max = w - dp(16) - padX * 2;
        if (tw > max) {
            float k = max / tw;
            label.setTextSize(size * k);
            labelWarn.setTextSize(size * k);
            tw = max;
        }
        float ph = label.getTextSize() * 1.7f;
        float px = (w - tw) / 2f - padX;
        float py = dp(10);
        r.set(px, py, px + tw + padX * 2, py + ph);
        canvas.drawRoundRect(r, ph / 2, ph / 2, pill);
        float base = py + ph / 2 + label.getTextSize() * 0.36f;
        canvas.drawText(main, px + padX, base, label);
        if (!warn.isEmpty()) canvas.drawText(warn, px + padX + label.measureText(main), base, labelWarn);
    }

    private void drawCar(Canvas canvas, float cx, float cy, float w, float l, Paint body, boolean brakeOn,
                         TurnSignal sig) {
        r.set(cx - w / 2, cy - l / 2, cx + w / 2, cy + l / 2);
        canvas.drawRoundRect(r, w * 0.25f, w * 0.25f, body);
        // 앞유리
        r.set(cx - w * 0.36f, cy - l * 0.32f, cx + w * 0.36f, cy - l * 0.12f);
        canvas.drawRoundRect(r, w * 0.1f, w * 0.1f, glass);
        // 브레이크등
        if (brakeOn) {
            canvas.drawRect(cx - w / 2, cy + l / 2 - dp(5), cx - w / 2 + w * 0.3f, cy + l / 2, brake);
            canvas.drawRect(cx + w / 2 - w * 0.3f, cy + l / 2 - dp(5), cx + w / 2, cy + l / 2, brake);
        }
        // 방향지시등(0.5초마다 깜빡임)
        boolean on = (SystemClock.uptimeMillis() / 500) % 2 == 0;
        if (on && (sig == TurnSignal.LEFT || sig == TurnSignal.HAZARD)) {
            canvas.drawCircle(cx - w / 2, cy - l / 2 + dp(4), dp(6), blink);
            canvas.drawCircle(cx - w / 2, cy + l / 2 - dp(4), dp(6), blink);
        }
        if (on && (sig == TurnSignal.RIGHT || sig == TurnSignal.HAZARD)) {
            canvas.drawCircle(cx + w / 2, cy - l / 2 + dp(4), dp(6), blink);
            canvas.drawCircle(cx + w / 2, cy + l / 2 - dp(4), dp(6), blink);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        running = false;
        super.onDetachedFromWindow();
    }

    private int color(int res) {
        return ContextCompat.getColor(getContext(), res);
    }

    private float dp(float v) {
        return v * density;
    }
}
