package com.giseo.flowcoach.session;

import android.os.Handler;
import android.os.Looper;

import com.giseo.flowcoach.data.demo.DemoScenario;
import com.giseo.flowcoach.domain.CoachEngine;
import com.giseo.flowcoach.domain.TickResult;

import java.util.List;

/**
 * 시연 시나리오를 1초에 한 프레임씩 CoachEngine에 넣는다.
 * 실제 주행 세션과 똑같이 engine.tick()을 호출하므로 판단 로직은 동일하다.
 */
public final class DemoSession {

    public interface Callback {
        void onTick(DemoScenario.Frame frame, TickResult result);

        void onFinished();
    }

    private static final long TICK_MS = 1000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final CoachEngine engine;
    private final List<DemoScenario.Frame> frames;
    private final Callback callback;

    private int index = 0;
    private boolean running = false;

    private final Runnable step = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            if (index >= frames.size()) {
                running = false;
                callback.onFinished();
                return;
            }
            DemoScenario.Frame frame = frames.get(index++);
            TickResult result = engine.tick(frame.snapshot, frame.road);
            callback.onTick(frame, result);
            handler.postDelayed(this, TICK_MS);
        }
    };

    public DemoSession(CoachEngine engine, List<DemoScenario.Frame> frames, Callback callback) {
        this.engine = engine;
        this.frames = frames;
        this.callback = callback;
    }

    public void start() {
        if (running) return;
        running = true;
        handler.post(step);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(step);
    }

    public boolean isRunning() {
        return running;
    }

    /** 진행률(0~1). */
    public float progress() {
        return frames.isEmpty() ? 0 : (float) index / frames.size();
    }
}
