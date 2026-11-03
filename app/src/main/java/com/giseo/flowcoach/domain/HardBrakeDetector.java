package com.giseo.flowcoach.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * 급감속 감지.
 * 급제동은 뒤차들의 연쇄 감속을 부르고, 이게 '이유 없는 정체(유령 정체)'의 출발점이 된다.
 *
 *  - 속도 기준: 직전 시점보다 초당 hardDecelKmhPerSec 이상 줄면 급감속.
 *  - 페달 기준: 브레이크 페달을 hardPedalPercent 이상 깊게 밟는 순간(속도 데이터가 늦을 때 보완).
 *  - 반복: repeatedBrakeWindowMs 안에 repeatedBrakeCount번 이상이면 차간 간격 조언.
 */
public final class HardBrakeDetector {

    private static final long NEVER = Long.MIN_VALUE / 4;

    private final CoachConfig config;

    private DrivingSnapshot prev;
    private boolean prevPedalHard = false;
    private boolean inHardEpisode = false;
    private long lastHardBrakeMs = NEVER;
    private long lastRepeatedEventMs = NEVER;
    private final Deque<Long> recent = new ArrayDeque<>();
    private int hardBrakeCount = 0;

    public HardBrakeDetector(CoachConfig config) {
        this.config = config;
    }

    public List<CoachEvent> update(DrivingSnapshot s) {
        List<CoachEvent> events = new ArrayList<>(2);
        boolean hard = false;

        if (prev != null) {
            double dtSec = (s.timeMs - prev.timeMs) / 1000.0;
            if (dtSec > 0 && dtSec <= 3) {
                double decel = (prev.speedKmh - s.speedKmh) / dtSec;
                if (prev.speedKmh >= config.hardBrakeMinSpeedKmh && decel >= config.hardDecelKmhPerSec) {
                    hard = true;
                }
            }
        }

        boolean pedalHard = s.brakePedalPercent >= config.hardPedalPercent;
        if (pedalHard && !prevPedalHard && s.speedKmh >= config.hardBrakeMinSpeedKmh) {
            hard = true; // 페달을 깊게 밟기 시작한 순간
        }
        prevPedalHard = pedalHard;
        prev = s;

        // 브레이크를 길게 밟아 급감속이 몇 초 이어져도 '한 번의 급제동'으로 센다.
        // 감속이 기준 아래로 내려가야 다음 급제동을 셀 준비가 된다.
        boolean newEpisode = hard && !inHardEpisode;
        inHardEpisode = hard;

        long now = s.timeMs;
        if (newEpisode && now - lastHardBrakeMs >= config.hardBrakeRefractoryMs) {
            lastHardBrakeMs = now;
            hardBrakeCount++;
            events.add(new CoachEvent(CoachEvent.Type.HARD_BRAKE, now));

            recent.addLast(now);
            while (!recent.isEmpty() && now - recent.peekFirst() > config.repeatedBrakeWindowMs) {
                recent.removeFirst();
            }
            if (recent.size() >= config.repeatedBrakeCount
                    && now - lastRepeatedEventMs >= config.repeatedBrakeWindowMs) {
                lastRepeatedEventMs = now;
                events.add(new CoachEvent(CoachEvent.Type.REPEATED_HARD_BRAKE, now));
            }
        }
        return events;
    }

    /** 주행이 끊겼다가 다시 시작할 때: 이전 속도와 비교해 급감속으로 착각하지 않게 지운다. */
    public void resetForStop() {
        prev = null;
        prevPedalHard = false;
        inHardEpisode = false;
    }

    public int getHardBrakeCount() {
        return hardBrakeCount;
    }
}
