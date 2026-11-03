package com.giseo.flowcoach.domain;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 흐름 코치의 두뇌.
 * 1초마다 tick()을 호출하면 흐름 진단·차로 습관·급감속을 종합해서
 * '지금 말할 안내가 있는지'를 결정한다.
 *
 * 잔소리가 되지 않게 하는 장치:
 *  - 상태 지속 시간: '느림'은 tooSlowPersistMs 이상 이어져야 안내
 *  - 전체 간격: 어떤 안내든 globalGapMs 간격
 *  - 종류별 쿨다운: 같은 안내는 sameTypeCooldownMs 안에 반복하지 않음
 *  - 조용한 모드: 위험 패턴만 안내
 *  - 우선순위: 동시에 여러 개면 안전에 가까운 것부터
 *
 * Android에 의존하지 않는 순수 Java라서 단위 테스트로 검증할 수 있다.
 */
public final class CoachEngine {

    private static final long NEVER = Long.MIN_VALUE / 4;
    /** 전체 간격 때문에 못 한 안내를 잠시 들고 있는 시간. */
    private static final long PENDING_TTL_MS = 10_000;

    /** 우선순위(앞쪽이 높음). */
    private static final CoachEvent.Type[] PRIORITY = {
            CoachEvent.Type.OVER_LIMIT,
            CoachEvent.Type.REPEATED_HARD_BRAKE,
            CoachEvent.Type.FREQUENT_LANE_CHANGE,
            CoachEvent.Type.PASSING_LANE_HOLD,
            CoachEvent.Type.TOO_SLOW,
            CoachEvent.Type.SMOOTH_PRAISE,
    };

    private final CoachConfig config;
    private final FlowAnalyzer flowAnalyzer;
    private final LaneHabitDetector laneDetector;
    private final HardBrakeDetector brakeDetector;
    private final TripReport report = new TripReport();

    private CoachMode mode;

    private FlowState currentState = FlowState.UNKNOWN;
    private long stateSinceMs = NEVER;
    private long stateEventLastMs = NEVER;
    /** 마지막 차로 변경·급감속 시각(칭찬 타이밍 판단용). */
    private long lastIncidentMs = NEVER;

    private final Map<CoachEvent.Type, Long> lastSpokenByType = new EnumMap<>(CoachEvent.Type.class);
    private long lastSpokenMs = NEVER;
    private CoachMessage pending;

    private FlowResult lastFlow;
    /** 직전 틱이 주차·후진·중립이라 코칭을 쉬고 있었는지. */
    private boolean paused = false;

    public CoachEngine(CoachConfig config, CoachMode mode) {
        this.config = config;
        this.mode = mode;
        this.flowAnalyzer = new FlowAnalyzer(config);
        this.laneDetector = new LaneHabitDetector(config);
        this.brakeDetector = new HardBrakeDetector(config);
    }

    public TickResult tick(DrivingSnapshot s, RoadContext road) {
        long now = s.timeMs;
        if (!s.gear.allowsCoaching()) {
            return pausedTick(s, road);
        }
        paused = false;
        FlowResult flow = flowAnalyzer.analyze(s.speedKmh, road);
        lastFlow = flow;

        List<CoachEvent> events = new ArrayList<>(4);
        events.addAll(laneDetector.update(s, road));
        events.addAll(brakeDetector.update(s));
        for (CoachEvent e : events) {
            if (e.type == CoachEvent.Type.LANE_CHANGE_LEFT || e.type == CoachEvent.Type.LANE_CHANGE_RIGHT
                    || e.type == CoachEvent.Type.HARD_BRAKE) {
                lastIncidentMs = now;
            }
        }
        addFlowStateEvent(flow, now, events);

        report.record(s, flow, events);

        CoachMessage message = selectMessage(events, flow, now);
        return new TickResult(flow, events, message, laneDetector.getLaneOffset(), laneDetector.isPassing());
    }

    /**
     * P·R·N: 판단·음성·리포트를 모두 쉰다. 화면 게이지용 값(제한·권장)만 채우고 상태는 UNKNOWN.
     * 쉬는 동안의 상태(흐름 지속 시간, 켜 둔 방향지시등, 추월, 직전 속도)는 지워서
     * 다시 D로 갔을 때 엉뚱한 안내가 나오지 않게 한다.
     */
    private TickResult pausedTick(DrivingSnapshot s, RoadContext road) {
        FlowResult f = flowAnalyzer.analyze(s.speedKmh, road);
        FlowResult idle = new FlowResult(FlowState.UNKNOWN, s.speedKmh, f.recommendedKmh, f.speedLimitKmh, f.flowSpeedKmh);
        lastFlow = idle;
        if (!paused) {
            paused = true;
            currentState = FlowState.UNKNOWN;
            stateSinceMs = s.timeMs;
            stateEventLastMs = NEVER;
            pending = null;
            laneDetector.resetForStop();
            brakeDetector.resetForStop();
            report.markGap();
        }
        return new TickResult(idle, new ArrayList<CoachEvent>(0), null, 0, false);
    }

    /** 주차·후진·중립이라 코칭을 쉬는 중인지. */
    public boolean isPaused() {
        return paused;
    }

    /** 흐름 상태가 일정 시간 이어졌을 때만 이벤트로 만든다. */
    private void addFlowStateEvent(FlowResult flow, long now, List<CoachEvent> events) {
        if (flow.state != currentState) {
            currentState = flow.state;
            stateSinceMs = now;
            stateEventLastMs = NEVER;
        }
        long duration = now - stateSinceMs;

        CoachEvent.Type type = null;
        long persist = 0;
        long repeatEvery = config.sameTypeCooldownMs;
        switch (currentState) {
            case TOO_SLOW:
                type = CoachEvent.Type.TOO_SLOW;
                persist = config.tooSlowPersistMs;
                break;
            case OVER_LIMIT:
                type = CoachEvent.Type.OVER_LIMIT;
                persist = config.overLimitPersistMs;
                break;
            case SMOOTH:
                // 추월 차로에 머무는 중이거나, 방금 차로 변경·급감속이 있었다면 칭찬하지 않는다.
                if (laneDetector.isPassing() || now - lastIncidentMs < config.smoothPraiseMs) break;
                type = CoachEvent.Type.SMOOTH_PRAISE;
                persist = config.smoothPraiseMs;
                repeatEvery = config.smoothPraiseMs * 2;
                break;
            default:
                break;
        }
        if (type != null && duration >= persist && now - stateEventLastMs >= repeatEvery) {
            stateEventLastMs = now;
            events.add(new CoachEvent(type, now));
        }
    }

    private CoachMessage selectMessage(List<CoachEvent> events, FlowResult flow, long now) {
        // 이번 틱의 후보 중 가장 우선순위가 높은 것
        CoachMessage candidate = null;
        for (CoachEvent.Type type : PRIORITY) {
            if (!containsType(events, type)) continue;
            if (!allowedInMode(type)) continue;
            Long last = lastSpokenByType.get(type);
            if (last != null && now - last < config.sameTypeCooldownMs) continue;
            candidate = new CoachMessage(type, textFor(type, flow), voiceKeyFor(type), now);
            break;
        }

        // 전에 미뤄둔 안내가 더 중요하면 그것부터
        if (pending != null) {
            if (now - pending.timeMs > PENDING_TTL_MS) {
                pending = null;
            } else if (candidate == null || priorityOf(pending.type) < priorityOf(candidate.type)) {
                candidate = new CoachMessage(pending.type, pending.text, pending.voiceKey, pending.timeMs);
            }
        }
        if (candidate == null) return null;

        if (now - lastSpokenMs < config.globalGapMs) {
            if (pending == null || priorityOf(candidate.type) < priorityOf(pending.type)) {
                pending = candidate; // 잠깐 미뤘다가 말한다
            }
            return null;
        }

        pending = null;
        lastSpokenMs = now;
        lastSpokenByType.put(candidate.type, now);
        return new CoachMessage(candidate.type, candidate.text, candidate.voiceKey, now);
    }

    private boolean allowedInMode(CoachEvent.Type type) {
        if (mode == CoachMode.COACH) return true;
        switch (type) {
            case OVER_LIMIT:
            case REPEATED_HARD_BRAKE:
            case FREQUENT_LANE_CHANGE:
            case PASSING_LANE_HOLD:
                return true;
            default:
                return false;
        }
    }

    private String textFor(CoachEvent.Type type, FlowResult flow) {
        switch (type) {
            case OVER_LIMIT:
                return "제한속도 " + flow.speedLimitKmh + "킬로를 넘었어요. 속도를 조금 줄여주세요.";
            case REPEATED_HARD_BRAKE:
                return "급제동이 반복되고 있어요. 앞차와 간격을 조금 더 두면 흐름이 부드러워져요.";
            case FREQUENT_LANE_CHANGE:
                return "차로 변경이 잦아요. 한 차로를 유지하는 게 흐름에 더 좋아요.";
            case PASSING_LANE_HOLD:
                return "추월을 마쳤다면 오른쪽 차로로 돌아가 볼까요?";
            case TOO_SLOW:
                if (laneDetector.isPassing()) {
                    return "흐름보다 조금 느려요. 편하게 가고 싶다면 오른쪽 차로가 좋아요.";
                }
                return "흐름보다 " + Math.round(-flow.gapKmh()) + "킬로 정도 느려요. 시속 "
                        + Math.round(flow.recommendedKmh) + "킬로 정도가 지금 흐름에 맞아요.";
            case SMOOTH_PRAISE:
                return "지금 흐름 잘 타고 있어요. 이대로 가면 돼요.";
            default:
                return "";
        }
    }

    /** 녹음된 음성 파일 키. 숫자가 들어가는 문장은 숫자 없는 버전으로 녹음한다. */
    private String voiceKeyFor(CoachEvent.Type type) {
        if (type == CoachEvent.Type.TOO_SLOW && laneDetector.isPassing()) {
            return "too_slow_passing";
        }
        return type.name().toLowerCase(java.util.Locale.ROOT);
    }

    /** describeNow()에 해당하는 녹음 음성 키. */
    public String describeNowKey() {
        if (lastFlow == null) return "now_unknown";
        switch (lastFlow.state) {
            case OVER_LIMIT: return "now_over_limit";
            case CONGESTED: return "now_congested";
            case TOO_SLOW: return "now_too_slow";
            case SMOOTH: return "now_smooth";
            default: return "now_unknown";
        }
    }

    /** "지금 어때?" 음성 질문에 대한 답. */
    public String describeNow() {
        if (lastFlow == null) {
            return "아직 주행 정보를 받는 중이에요.";
        }
        FlowResult f = lastFlow;
        long my = Math.round(f.mySpeedKmh);
        String answer;
        switch (f.state) {
            case OVER_LIMIT:
                answer = "지금 시속 " + my + "킬로로, 제한속도 " + f.speedLimitKmh + "킬로보다 빨라요.";
                break;
            case CONGESTED:
                answer = "지금 도로가 정체 중이에요. 앞차와 간격만 여유 있게 두세요.";
                break;
            case TOO_SLOW:
                answer = "지금 시속 " + my + "킬로로, 흐름보다 " + Math.round(-f.gapKmh()) + "킬로 정도 느려요.";
                break;
            case SMOOTH:
                answer = "지금 시속 " + my + "킬로로, 흐름에 잘 맞춰 달리고 있어요.";
                break;
            default:
                answer = "지금은 정차 중이거나 판단할 정보가 부족해요.";
        }
        if (laneDetector.isPassing()) {
            answer += " 왼쪽으로 추월한 뒤 아직 복귀 전이에요.";
        }
        return answer;
    }

    private static boolean containsType(List<CoachEvent> events, CoachEvent.Type type) {
        for (CoachEvent e : events) {
            if (e.type == type) return true;
        }
        return false;
    }

    private static int priorityOf(CoachEvent.Type type) {
        for (int i = 0; i < PRIORITY.length; i++) {
            if (PRIORITY[i] == type) return i;
        }
        return PRIORITY.length;
    }

    public void setMode(CoachMode mode) {
        this.mode = mode;
    }

    public CoachMode getMode() {
        return mode;
    }

    public TripReport getReport() {
        return report;
    }

    public FlowResult getLastFlow() {
        return lastFlow;
    }
}
