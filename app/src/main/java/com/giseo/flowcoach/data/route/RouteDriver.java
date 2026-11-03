package com.giseo.flowcoach.data.route;

import com.giseo.flowcoach.data.demo.DemoScene;
import com.giseo.flowcoach.domain.CoachConfig;
import com.giseo.flowcoach.domain.TurnSignal;

/**
 * 계획(RoutePlanner.Plan)대로 1초씩 '운전'한다: 속도, 방향지시등, 브레이크 페달, 실제 도로 위 위치.
 *
 * 속도는 고정값이 아니라 그때의 권장 속도(실시간 흐름과 제한속도 중 낮은 값)를 기준으로 정한다.
 * 그래서 실제 교통이 시나리오와 달라도 '흐름보다 느림', '흐름에 맞음' 같은 상황이 그대로 만들어진다.
 *
 *  - 흐름 타기   : 권장 속도 - 2
 *  - 느림       : 권장 속도 - (느림 기준 + 8)
 *  - 추월 후 미복귀: ← 신호 → 왼쪽 차로 유지 → → 신호로 복귀
 *  - 과속       : 제한속도 + 12
 *  - 급제동 반복 : 14초마다 2초간 급제동(페달 85%, 초당 -13km/h)
 *  - 잦은 차로 변경: 6초마다 ←/→ 신호
 */
public final class RouteDriver {

    static final double ACCEL_KMH_PER_SEC = 3;
    static final double DECEL_KMH_PER_SEC = 4;
    static final double HARD_BRAKE_KMH_PER_SEC = 13;
    static final int HARD_BRAKE_PEDAL = 85;
    static final int SIGNAL_SEC = 2;
    static final int RETURN_SEC = 4;
    static final int SLOW_SETTLE_SEC = 6;
    static final double SLOW_BELOW_KMH = 8;
    static final double OVER_BY_KMH = 12;
    static final int BRAKE_CYCLE_SEC = 14;
    static final int LANE_CYCLE_SEC = 6;
    static final double START_KMH = 70;

    /** 한 초의 결과. */
    public static final class Output {
        public final double speedKmh;
        public final TurnSignal signal;
        public final int pedalPercent;
        public final double lat;
        public final double lon;
        public final double bearingDeg;
        public final double meters;
        public final String caption;
        /** 지금 보여주는 상황(상황 토막이 아니면 null). */
        public final DemoScene scene;
        public final boolean finished;

        Output(double speedKmh, TurnSignal signal, int pedalPercent, double[] where, double meters,
               String caption, DemoScene scene, boolean finished) {
            this.speedKmh = speedKmh;
            this.signal = signal;
            this.pedalPercent = pedalPercent;
            this.lat = where[0];
            this.lon = where[1];
            this.bearingDeg = where[2];
            this.meters = meters;
            this.caption = caption;
            this.scene = scene;
            this.finished = finished;
        }
    }

    private final Polyline line;
    private final RoutePlanner.Plan plan;
    private final CoachConfig config;
    private double speed = START_KMH;
    private double meters = 0;
    private int sec = 0;
    private boolean finished = false;
    /** 지금 보여주는 상황(plan.steps 번호, 없으면 -1)과 시작한 시각. */
    private int active = -1;
    private int activeStartSec = 0;
    /** 다음에 시작할 상황 번호. */
    private int next = 0;

    public RouteDriver(Polyline line, RoutePlanner.Plan plan, CoachConfig config) {
        this.line = line;
        this.plan = plan;
        this.config = config;
    }

    /**
     * 1초 진행한다.
     * @param recKmh   지금 권장 속도(흐름과 제한속도 중 낮은 값)
     * @param limitKmh 지금 제한속도
     */
    public Output step(double recKmh, int limitKmh) {
        if (finished) return output(TurnSignal.OFF, 0, "구간 끝", null);
        // 상황은 그 지점에 도달하면 시작하고, 정해진 시간이 지나면 끝난다
        if (active >= 0 && sec - activeStartSec >= plan.steps.get(active).durationSec) active = -1;
        if (active < 0 && next < plan.steps.size() && meters >= plan.steps.get(next).startMeters) {
            active = next++;
            activeStartSec = sec;
        }
        RoutePlanner.Step step = active >= 0 ? plan.steps.get(active) : null;
        int tau = step != null ? sec - activeStartSec : 0;
        double base = Math.max(30, recKmh - 2 + Math.sin(sec / 7.0)); // 사람처럼 ±1km/h 흔들림
        double target = base;
        TurnSignal signal = TurnSignal.OFF;
        int pedal = 0;
        String caption;
        DemoScene scene = null;

        if (step == null) {
            RoutePlanner.Step upcoming = nextSituation();
            if (sec < RoutePlanner.LEAD_SEC) {
                caption = "실제 도로 주행 시작";
            } else if (upcoming != null) {
                caption = "흐름에 맞춰 주행 중 · 다음: " + upcoming.scene.title + " (약 " + secondsToNext() + "초 뒤)";
            } else {
                caption = "흐름에 맞춰 주행 중 · 남은 상황 없음";
            }
        } else {
            scene = step.scene;
            int dur = step.durationSec;
            switch (scene) {
                case SMOOTH:
                    caption = "흐름에 맞춰 꾸준히 주행 (" + tau + "/" + (dur - 6) + "초)";
                    break;
                case TOO_SLOW:
                    target = Math.max(30, recKmh - config.slowMarginKmh - SLOW_BELOW_KMH);
                    caption = "흐름보다 느리게 주행 (" + Math.round(target) + "km/h)";
                    break;
                case PASSING:
                    if (tau < SIGNAL_SEC) {
                        signal = TurnSignal.LEFT;
                        target = recKmh + 3;
                        caption = "추월하려고 왼쪽 차로로 (방향지시등 ←)";
                    } else if (tau < dur - RETURN_SEC) {
                        target = recKmh + 1;
                        caption = "추월 후 왼쪽 차로에 머무는 중 (" + (tau - SIGNAL_SEC) + "초)";
                    } else if (tau < dur - RETURN_SEC + SIGNAL_SEC) {
                        signal = TurnSignal.RIGHT;
                        caption = "오른쪽 차로로 복귀 (방향지시등 →)";
                    } else {
                        caption = "복귀 완료";
                    }
                    break;
                case OVER_LIMIT:
                    target = limitKmh + OVER_BY_KMH;
                    caption = "제한속도(" + limitKmh + ")를 넘겨 주행 (" + Math.round(target) + "km/h)";
                    break;
                case HARD_BRAKE: {
                    int cycle = tau % BRAKE_CYCLE_SEC;
                    int nth = tau / BRAKE_CYCLE_SEC + 1;
                    if (nth <= config.repeatedBrakeCount && cycle >= 2 && cycle < 4) {
                        pedal = HARD_BRAKE_PEDAL;
                        caption = "급제동 (" + nth + "번째)";
                    } else {
                        caption = "다시 가속";
                    }
                    break;
                }
                case LANE_CHANGES: {
                    int cycle = tau % LANE_CYCLE_SEC;
                    int nth = tau / LANE_CYCLE_SEC; // 0부터
                    if (nth <= config.frequentCount && cycle < SIGNAL_SEC) {
                        signal = nth % 2 == 0 ? TurnSignal.LEFT : TurnSignal.RIGHT;
                        caption = "차로 변경 " + (signal == TurnSignal.LEFT ? "←" : "→") + " (" + (nth + 1) + "번째)";
                    } else {
                        caption = "차로 변경을 이어가는 중";
                    }
                    break;
                }
                default:
                    caption = "";
                    break;
            }
        }

        if (pedal > 0) {
            speed -= HARD_BRAKE_KMH_PER_SEC;
        } else {
            double d = target - speed;
            speed += Math.max(-DECEL_KMH_PER_SEC, Math.min(ACCEL_KMH_PER_SEC, d));
        }
        speed = Math.max(0, speed);
        meters += speed / 3.6;
        sec++;
        if (meters >= line.lengthMeters()) {
            meters = line.lengthMeters();
            finished = true;
            caption = "구간 끝";
        }
        return output(signal, pedal, caption, scene);
    }

    private Output output(TurnSignal signal, int pedal, String caption, DemoScene scene) {
        return new Output(speed, signal, pedal, line.at(meters), meters, caption, scene, finished);
    }

    public boolean isFinished() {
        return finished;
    }

    /** 지금 보여주는 상황(없으면 null). */
    public DemoScene currentScene() {
        return active >= 0 ? plan.steps.get(active).scene : null;
    }

    /** 다음에 시작할 상황(없으면 null). */
    public RoutePlanner.Step nextSituation() {
        return next < plan.steps.size() ? plan.steps.get(next) : null;
    }

    /** 다음 상황까지 지금 속도로 몇 초쯤 남았는지. */
    public int secondsToNext() {
        RoutePlanner.Step n = nextSituation();
        if (n == null) return 0;
        double ms = Math.max(5, speed) / 3.6;
        return (int) Math.max(0, Math.round((n.startMeters - meters) / ms));
    }

    public double meters() {
        return meters;
    }

    public int elapsedSec() {
        return sec;
    }
}
