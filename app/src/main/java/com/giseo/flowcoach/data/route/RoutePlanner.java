package com.giseo.flowcoach.data.route;

import com.giseo.flowcoach.data.demo.DemoScene;
import com.giseo.flowcoach.domain.CoachConfig;
import com.giseo.flowcoach.domain.CoachTuning;
import com.giseo.flowcoach.domain.RoadType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 실제 도로 위에 상황을 배치한다(거리 기준).
 *
 * 상황마다 '안내가 나오려면 최소 몇 초가 필요한지'는 기준값에 따라 다르다
 * (예: 칭찬은 빠르게 15초 / 실제 운전 5분). 그 시간 동안 달릴 거리를 계산해서
 *  - 신호 없는 도로(고속도로·자동차전용도로) 구간 안에만,
 *  - 추월 후 미복귀는 흐름이 법 기준(80km/h) 이상인 구간에만,
 *  - 안내 간격 규칙만큼 서로 떨어뜨려서
 * 출발점부터 차례로 놓는다. 안 들어가는 상황은 이유와 함께 표시한다.
 * 구간이 하나뿐인 경로는 남는 거리를 상황 사이에 고르게 나눠서 경로 전체에 퍼지게 한다.
 *
 * 상황은 '그 지점에 도달하면' 시작한다(RouteDriver). 실제 흐름이 예상과 달라도 상황이 엉뚱한 도로로 밀리지 않는다.
 */
public final class RoutePlanner {

    /** 처음: 가속 + 실시간 교통 첫 조회를 기다리는 시간. */
    static final int LEAD_SEC = 20;
    /** 경로 길이 중 계획에 쓰는 비율(끝부분 여유). */
    static final double USABLE = 0.9;
    /** 느린 구간(시내 등)에서 들어온 직후에는 이만큼(초) 달려 속도를 올린 뒤에 상황을 시작한다. */
    static final int ENTRY_SEC = 25;

    /** 배치된 상황 하나: 출발점에서 startMeters 지점에 도달하면 durationSec 동안. */
    public static final class Step {
        public final DemoScene scene;
        public final double startMeters;
        public final int durationSec;
        /** 이 상황을 놓은 구간의 도로 이름. */
        public final String roadName;

        Step(DemoScene scene, double startMeters, int durationSec, String roadName) {
            this.scene = scene;
            this.startMeters = startMeters;
            this.durationSec = durationSec;
            this.roadName = roadName;
        }
    }

    /** 상황 하나가 이 경로·기준값에서 나올 수 있는지. */
    public static final class Check {
        public final DemoScene scene;
        public final boolean fits;
        /** 필요한 시간(초). */
        public final int needSec;
        /** 안 들어갈 때 이유(들어가면 ""). */
        public final String reason;

        Check(DemoScene scene, boolean fits, int needSec, String reason) {
            this.scene = scene;
            this.fits = fits;
            this.needSec = needSec;
            this.reason = reason;
        }
    }

    public static final class Plan {
        public final List<Step> steps;
        public final List<Check> checks;
        /** 경로를 다 달리는 데 걸리는 예상 시간(초). */
        public final int estimatedSec;

        Plan(List<Step> steps, List<Check> checks, int estimatedSec) {
            this.steps = Collections.unmodifiableList(steps);
            this.checks = Collections.unmodifiableList(checks);
            this.estimatedSec = estimatedSec;
        }

        public boolean allFit() {
            for (Check c : checks) if (!c.fits) return false;
            return true;
        }

        public int fitCount() {
            int n = 0;
            for (Check c : checks) if (c.fits) n++;
            return n;
        }
    }

    private RoutePlanner() {
    }

    /** 구간이 하나인 경로(길이·흐름·제한만으로). */
    public static Plan plan(List<DemoScene> wanted, CoachConfig c, double routeMeters, double flowKmh, int limitKmh) {
        List<RoadSpan> one = Collections.singletonList(
                new RoadSpan(0, routeMeters, RoadType.EXPRESSWAY, limitKmh, flowKmh, ""));
        return plan(wanted, c, routeMeters, one);
    }

    public static Plan plan(List<DemoScene> wanted, CoachConfig c, RouteScenario r) {
        return plan(wanted, c, r.line.lengthMeters(), r.spans);
    }

    static Plan plan(List<DemoScene> wanted, CoachConfig c, double routeMeters, List<RoadSpan> spans) {
        double endLimit = routeMeters * USABLE;
        int gap = gapSec(c);
        List<Check> checks = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        double cursor = LEAD_SEC * cruiseMs(spanAt(spans, 0));
        boolean anyUninterrupted = false;
        for (RoadSpan s : spans) anyUninterrupted |= s.allowsSituations();

        for (DemoScene scene : wanted) {
            Placement p = place(scene, c, spans, cursor, gap, endLimit, steps.isEmpty());
            if (p != null) {
                steps.add(new Step(scene, p.start, p.needSec, p.span.name));
                checks.add(new Check(scene, true, p.needSec, ""));
                cursor = p.end;
            } else {
                checks.add(new Check(scene, false, roughNeed(scene, c, spans),
                        reason(scene, c, spans, cursor, gap, endLimit, anyUninterrupted)));
            }
        }

        // 구간이 하나뿐이면 남는 거리를 상황 사이에 고르게 나눈다(상황이 경로 전체에 퍼지게).
        // '흐름에 맞음'은 앞 상황(첫 상황이면 출발)에 붙인 채로 둔다: 흐름에 맞춰 달린 시간이 그대로
        // 칭찬까지의 시간이라, 사이를 벌리면 칭찬이 상황보다 먼저 나온다.
        if (spans.size() == 1 && !steps.isEmpty()) {
            double slack = Math.max(0, endLimit - cursor);
            int movable = 0;
            for (Step st : steps) if (st.scene != DemoScene.SMOOTH) movable++;
            List<Step> spread = new ArrayList<>();
            int k = 0;
            double offset = 0;
            for (Step st : steps) {
                if (st.scene != DemoScene.SMOOTH) offset = slack * (++k) / (movable + 1);
                spread.add(new Step(st.scene, st.startMeters + offset, st.durationSec, st.roadName));
            }
            steps = spread;
        }

        double sec = 0;
        for (RoadSpan s : spans) sec += s.lengthM() / cruiseMs(s);
        return new Plan(steps, checks, (int) Math.round(sec));
    }

    /** 상황 하나를 cursor 이후 처음 들어가는 곳에 놓는다. 없으면 null. */
    private static Placement place(DemoScene scene, CoachConfig c, List<RoadSpan> spans, double cursor, int gap,
                                   double endLimit, boolean first) {
        // '흐름에 맞음'은 따로 기다릴 필요가 없다(흐름에 맞춰 달리는 것 자체가 그 상황).
        // 첫 상황이면 출발부터, 아니면 앞 상황이 끝나자마자 시작한다
        double m = scene == DemoScene.SMOOTH ? (first ? 0 : cursor)
                : cursor + gap * cruiseMs(spanAt(spans, cursor));
        for (RoadSpan span : spans) {
            if (span.endM <= m || !suitable(scene, c, span)) continue;
            double start = Math.max(m, entryStart(spans, span));
            double rec = span.expectedRecKmh();
            int need = needSec(scene, c, rec, span.limitKmh);
            double end = start + need * speedFor(scene, c, rec, span.limitKmh) / 3.6;
            if (end > endLimit) return null; // 뒤로 갈수록 남은 거리만 줄어든다
            if (end <= span.endM || continuousSuitable(scene, c, spans, start, end)) {
                Placement p = new Placement();
                p.span = span;
                p.start = start;
                p.end = end;
                p.needSec = need;
                return p;
            }
        }
        return null;
    }

    /**
     * 이 구간에서 상황을 시작할 수 있는 가장 이른 지점.
     * 바로 앞 구간이 더 느리면(시내 → 고속도로 진입 등) 속도를 올리는 시간(ENTRY_SEC)만큼 뒤로 미룬다.
     * 그러지 않으면 진입 직후 느린 속도 때문에 '흐름보다 느림'이 다른 상황과 섞인다.
     */
    static double entryStart(List<RoadSpan> spans, RoadSpan span) {
        if (span.startM <= 0) return span.startM;
        RoadSpan before = spanAt(spans, span.startM - 1);
        if (before == span) return span.startM;
        boolean slower = !before.allowsSituations() || before.expectedRecKmh() < span.expectedRecKmh() - 10;
        return slower ? span.startM + ENTRY_SEC * cruiseMs(span) : span.startM;
    }

    private static final class Placement {
        RoadSpan span;
        double start;
        double end;
        int needSec;
    }

    /** 이 상황을 이 구간에서 보여줄 수 있는지. */
    static boolean suitable(DemoScene scene, CoachConfig c, RoadSpan span) {
        if (!span.allowsSituations()) return false;
        if (scene == DemoScene.PASSING) return span.assumedFlowKmh >= c.passingRuleMinFlowKmh;
        return true;
    }

    private static boolean continuousSuitable(DemoScene scene, CoachConfig c, List<RoadSpan> spans, double from, double to) {
        for (RoadSpan s : spans) {
            if (s.endM <= from || s.startM >= to) continue;
            if (!suitable(scene, c, s)) return false;
        }
        return true;
    }

    private static String reason(DemoScene scene, CoachConfig c, List<RoadSpan> spans, double cursor, int gap,
                                 double endLimit, boolean anyUninterrupted) {
        if (!anyUninterrupted) return "고속도로·자동차전용도로 구간이 없어서 안내를 만들 수 없어요(시내 도로는 신호 때문에 흐름 판단에서 빼요)";
        boolean anySuitable = false;
        for (RoadSpan s : spans) anySuitable |= suitable(scene, c, s);
        if (!anySuitable) {
            return String.format(Locale.KOREA, "흐름이 %.0fkm/h 이상인 고속도로 구간이 없어요(추월차로 규정)", c.passingRuleMinFlowKmh);
        }
        RoadSpan longest = null;
        for (RoadSpan s : spans) if (suitable(scene, c, s) && (longest == null || s.lengthM() > longest.lengthM())) longest = s;
        double rec = longest.expectedRecKmh();
        int need = needSec(scene, c, rec, longest.limitKmh);
        double needM = need * speedFor(scene, c, rec, longest.limitKmh) / 3.6 + gap * cruiseMs(longest);
        double room = Math.min(endLimit, longest.endM) - Math.max(cursor, longest.startM);
        return String.format(Locale.KOREA, "%s 이상 필요해서 이 경로에 안 들어가요(약 %.1fkm 부족)",
                CoachTuning.duration(need * 1000L), Math.max(0.1, (needM - Math.max(0, room)) / 1000.0));
    }

    private static int roughNeed(DemoScene scene, CoachConfig c, List<RoadSpan> spans) {
        RoadSpan s = spans.get(0);
        return needSec(scene, c, s.expectedRecKmh(), s.limitKmh);
    }

    /** 자동: 모든 상황이 들어가는 가장 실제에 가까운 단계(실제 운전 → 보통 → 빠르게). */
    public static CoachTuning.Level autoLevel(List<DemoScene> wanted, double routeMeters, double flowKmh, int limitKmh) {
        List<RoadSpan> one = Collections.singletonList(
                new RoadSpan(0, routeMeters, RoadType.EXPRESSWAY, limitKmh, flowKmh, ""));
        return autoLevel(wanted, routeMeters, one);
    }

    public static CoachTuning.Level autoLevel(RouteScenario r) {
        return autoLevel(r.situations, r.line.lengthMeters(), r.spans);
    }

    static CoachTuning.Level autoLevel(List<DemoScene> wanted, double routeMeters, List<RoadSpan> spans) {
        int best = -1;
        CoachTuning.Level bestLevel = CoachTuning.Level.FAST;
        for (CoachTuning.Level level : new CoachTuning.Level[]{
                CoachTuning.Level.REAL, CoachTuning.Level.NORMAL, CoachTuning.Level.FAST}) {
            Plan p = plan(wanted, CoachTuning.of(level).toConfig(), routeMeters, spans);
            if (p.allFit()) return level;
            if (p.fitCount() > best) { // 다 안 들어가면 가장 많이 들어가는 단계
                best = p.fitCount();
                bestLevel = level;
            }
        }
        return bestLevel;
    }

    /** 상황을 보여주는 데 필요한 시간(초). RouteDriver의 동작과 맞춰야 한다. */
    static int needSec(DemoScene s, CoachConfig c, double rec, int limit) {
        switch (s) {
            case SMOOTH:
                return (int) (c.smoothPraiseMs / 1000) + 6;
            case TOO_SLOW:
                return RouteDriver.SLOW_SETTLE_SEC + (int) (c.tooSlowPersistMs / 1000) + 6;
            case PASSING:
                return RouteDriver.SIGNAL_SEC + (int) (c.passingHoldMs / 1000) + 6 + RouteDriver.RETURN_SEC;
            case OVER_LIMIT: {
                int accel = (int) Math.ceil((limit + RouteDriver.OVER_BY_KMH - rec) / RouteDriver.ACCEL_KMH_PER_SEC);
                return Math.max(0, accel) + (int) (c.overLimitPersistMs / 1000) + 6;
            }
            case HARD_BRAKE:
                return c.repeatedBrakeCount * RouteDriver.BRAKE_CYCLE_SEC + 2;
            case LANE_CHANGES:
                return (c.frequentCount + 1) * RouteDriver.LANE_CYCLE_SEC + 4;
            default:
                return 20;
        }
    }

    /** 상황 사이 간격: 안내 간격 규칙 + 속도를 되돌릴 시간. */
    static int gapSec(CoachConfig c) {
        return (int) Math.max(12, c.globalGapMs / 1000 + 6);
    }

    static double speedFor(DemoScene s, CoachConfig c, double rec, int limit) {
        switch (s) {
            case TOO_SLOW: return Math.max(30, rec - c.slowMarginKmh - RouteDriver.SLOW_BELOW_KMH);
            case OVER_LIMIT: return limit + RouteDriver.OVER_BY_KMH - 4;
            case HARD_BRAKE: return rec - 12;
            default: return rec;
        }
    }

    private static double cruiseMs(RoadSpan s) {
        return Math.max(20, s.expectedRecKmh() - 2) / 3.6;
    }

    private static RoadSpan spanAt(List<RoadSpan> spans, double m) {
        for (RoadSpan s : spans) if (m < s.endM) return s;
        return spans.get(spans.size() - 1);
    }

    /** 기준값 단계를 고를 수 있는 항목. 잦은 차로 변경은 단계가 없어서 null. */
    public static CoachTuning.Item itemFor(DemoScene scene) {
        switch (scene) {
            case SMOOTH: return CoachTuning.Item.PRAISE;
            case PASSING: return CoachTuning.Item.PASSING;
            case TOO_SLOW: return CoachTuning.Item.TOO_SLOW;
            case OVER_LIMIT: return CoachTuning.Item.OVER_LIMIT;
            case HARD_BRAKE: return CoachTuning.Item.HARD_BRAKE;
            default: return null;
        }
    }
}
