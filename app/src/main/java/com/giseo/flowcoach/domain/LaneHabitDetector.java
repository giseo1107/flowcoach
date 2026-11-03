package com.giseo.flowcoach.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * ★ 대표 기능: 차로 습관 감지.
 *
 * 차량 SDK에는 '지금 몇 차로인지' 정보가 없고, GPS는 차로를 구분할 만큼 정확하지 않다.
 * 그래서 절대 차로 대신 '방향지시등 + 진행 방향'으로 차로 변경 이벤트를 잡고,
 * 출발 시점 대비 상대적인 차로 위치(laneOffset)를 추적한다.
 *
 *  - 방향지시등이 minSignalMs 이상 켜졌다 꺼지고, 그동안 진행 방향이 크게 바뀌지 않았으면 차로 변경 1회.
 *    (진행 방향이 크게 바뀌면 교차로 회전·IC 분기로 보고 제외)
 *  - 왼쪽으로 옮긴 뒤(laneOffset > 0) passingHoldMs 동안 복귀하지 않으면 '추월 후 미복귀'.
 *    단, 흐름이 80km/h 미만인 정체 때는 법적으로 허용되므로 경고하지 않는다.
 *  - frequentWindowMs 안에 frequentCount번 이상 바꾸면 '잦은 차로 변경'.
 */
public final class LaneHabitDetector {

    private static final long NEVER = Long.MIN_VALUE / 4;

    private final CoachConfig config;

    private TurnSignal activeSignal = TurnSignal.OFF;
    private long signalStartMs;
    private double headingAtSignalStart = Double.NaN;

    /** 출발 시점 기준 상대 차로 위치. +1이면 한 차로 왼쪽. */
    private int laneOffset = 0;
    private long passingSinceMs = NEVER;
    private boolean passing = false;

    private final Deque<Long> recentChanges = new ArrayDeque<>();
    private long lastFrequentEventMs = NEVER;
    private int laneChangeCount = 0;

    public LaneHabitDetector(CoachConfig config) {
        this.config = config;
    }

    public List<CoachEvent> update(DrivingSnapshot s, RoadContext road) {
        List<CoachEvent> events = new ArrayList<>(2);
        TurnSignal sig = s.turnSignal;
        boolean directional = sig == TurnSignal.LEFT || sig == TurnSignal.RIGHT;

        if (activeSignal == TurnSignal.OFF) {
            if (directional) {
                startSignal(sig, s);
            }
        } else if (sig != activeSignal) {
            if (sig == TurnSignal.HAZARD) {
                activeSignal = TurnSignal.OFF; // 비상등: 차로 변경으로 세지 않음
            } else {
                finishSignal(activeSignal, s, events);
                if (directional) {
                    startSignal(sig, s);
                } else {
                    activeSignal = TurnSignal.OFF;
                }
            }
        }

        checkPassingHold(s, road, events);
        return events;
    }

    private void startSignal(TurnSignal sig, DrivingSnapshot s) {
        activeSignal = sig;
        signalStartMs = s.timeMs;
        headingAtSignalStart = s.headingDeg;
    }

    private void finishSignal(TurnSignal sig, DrivingSnapshot s, List<CoachEvent> events) {
        long duration = s.timeMs - signalStartMs;
        if (duration < config.minSignalMs) return;                  // 툭 건드린 것
        if (s.speedKmh < config.laneChangeMinSpeedKmh) return;      // 저속 = 회전/주차 등
        if (!Double.isNaN(headingAtSignalStart) && s.hasHeading()
                && angleDiff(headingAtSignalStart, s.headingDeg) > config.maxHeadingChangeForLaneChangeDeg) {
            return;                                                 // 방향이 크게 바뀜 = 회전/분기
        }

        laneChangeCount++;
        long now = s.timeMs;
        if (sig == TurnSignal.LEFT) {
            laneOffset = Math.min(laneOffset + 1, 3);
            events.add(new CoachEvent(CoachEvent.Type.LANE_CHANGE_LEFT, now));
            if (laneOffset > 0 && !passing) {
                passing = true;
                passingSinceMs = now;
            }
        } else {
            laneOffset = Math.max(laneOffset - 1, -3);
            events.add(new CoachEvent(CoachEvent.Type.LANE_CHANGE_RIGHT, now));
            if (laneOffset <= 0) {
                passing = false;
                passingSinceMs = NEVER;
            }
        }

        recentChanges.addLast(now);
        while (!recentChanges.isEmpty() && now - recentChanges.peekFirst() > config.frequentWindowMs) {
            recentChanges.removeFirst();
        }
        if (recentChanges.size() >= config.frequentCount
                && now - lastFrequentEventMs >= config.frequentWindowMs) {
            lastFrequentEventMs = now;
            events.add(new CoachEvent(CoachEvent.Type.FREQUENT_LANE_CHANGE, now));
        }
    }

    private void checkPassingHold(DrivingSnapshot s, RoadContext road, List<CoachEvent> events) {
        if (!passing) return;
        if (s.timeMs - passingSinceMs < config.passingHoldMs) return;
        if (!road.roadType.isUninterruptedFlow()) return;

        double flowForRule = road.hasFlowSpeed() ? road.flowSpeedKmh : s.speedKmh;
        if (flowForRule < config.passingRuleMinFlowKmh) return;     // 정체 시 예외(법 규정)

        // 흐름보다 확실히 빠르게 달리며 추월 중이면 아직 추월 중으로 본다.
        if (road.hasFlowSpeed() && s.speedKmh > road.flowSpeedKmh + 5) return;

        events.add(new CoachEvent(CoachEvent.Type.PASSING_LANE_HOLD, s.timeMs));
        passingSinceMs = s.timeMs; // 계속 머무르면 다음 주기에 다시 알림
    }

    static double angleDiff(double a, double b) {
        double d = Math.abs(((a - b) % 360 + 540) % 360 - 180);
        return d;
    }

    /**
     * 주차·후진으로 주행이 끊겼을 때 부른다. 켜 둔 방향지시등과 추월 상태를 지운다.
     * (휴게소에 섰다가 다시 출발했는데 '추월 후 미복귀'로 안내하면 안 되므로)
     * 차로 변경 횟수는 리포트용이라 유지한다.
     */
    public void resetForStop() {
        activeSignal = TurnSignal.OFF;
        headingAtSignalStart = Double.NaN;
        laneOffset = 0;
        passing = false;
        passingSinceMs = NEVER;
    }

    public int getLaneOffset() {
        return laneOffset;
    }

    /** 왼쪽으로 추월한 뒤 아직 복귀하지 않은 상태인지. */
    public boolean isPassing() {
        return passing;
    }

    public int getLaneChangeCount() {
        return laneChangeCount;
    }

    public TurnSignal getActiveSignal() {
        return activeSignal;
    }
}
