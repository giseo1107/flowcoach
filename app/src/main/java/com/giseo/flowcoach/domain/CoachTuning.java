package com.giseo.flowcoach.domain;

import java.util.EnumMap;
import java.util.Map;

/**
 * 판단 기준값을 '고르기 쉽게' 나눈 것.
 *
 * 안내마다 반응 속도를 세 단계로 고른다: 빠르게(시연) · 보통 · 실제 운전(실사용 기준값).
 * 한 번에 고르는 프리셋(빠르게 보기 / 보통 / 실제 운전처럼)과, 경로 길이에 맞춰 정하는 '자동'도 있다.
 * 잦은 차로 변경(1분 안에 3번)은 단계와 상관없이 같다.
 */
public final class CoachTuning {

    /** 반응 속도 단계. */
    public enum Level {
        FAST("빠르게"),
        NORMAL("보통"),
        REAL("실제 운전");

        public final String label;

        Level(String label) {
            this.label = label;
        }
    }

    /** 한 번에 고르는 방법. AUTO는 경로 길이를 보고 FAST/NORMAL/REAL 중 하나로 정한다(RoutePlanner). */
    public enum Preset {
        AUTO("자동", "경로 길이에 맞춰 골라요"),
        REAL("실제 운전", "실제 차에서와 같은 기준이에요"),
        NORMAL("보통", "실제보다 조금 빨리 반응해요"),
        FAST("빠르게", "몇 초 만에 반응해요(시연용)"),
        CUSTOM("직접 조절", "안내마다 따로 골라요");

        public final String label;
        public final String description;

        Preset(String label, String description) {
            this.label = label;
            this.description = description;
        }
    }

    /** 단계를 고를 수 있는 항목(안내 5종 + 안내 간격). */
    public enum Item {
        PRAISE("칭찬(흐름 잘 탐)"),
        PASSING("추월 후 미복귀"),
        TOO_SLOW("흐름보다 느림"),
        OVER_LIMIT("제한속도 초과"),
        HARD_BRAKE("급제동 반복"),
        GAP("안내 사이 간격");

        public final String label;

        Item(String label) {
            this.label = label;
        }
    }

    private final EnumMap<Item, Level> levels = new EnumMap<>(Item.class);

    private CoachTuning(Level all) {
        for (Item i : Item.values()) levels.put(i, all);
    }

    public static CoachTuning of(Level all) {
        return new CoachTuning(all);
    }

    public CoachTuning copy() {
        CoachTuning t = new CoachTuning(Level.NORMAL);
        t.levels.putAll(levels);
        return t;
    }

    public Level get(Item item) {
        return levels.get(item);
    }

    public CoachTuning set(Item item, Level level) {
        levels.put(item, level);
        return this;
    }

    /** 모든 항목이 같은 단계면 그 단계, 섞여 있으면 null. */
    public Level uniformLevel() {
        Level first = null;
        for (Level l : levels.values()) {
            if (first == null) first = l;
            else if (first != l) return null;
        }
        return first;
    }

    /** 기준값으로 바꾼다. 모두 '실제 운전'이면 CoachConfig.defaults()와 같고, 모두 '빠르게'면 CoachConfig.demo()와 같다. */
    public CoachConfig toConfig() {
        CoachConfig c = CoachConfig.defaults();
        c.smoothPraiseMs = pick(Item.PRAISE, 15_000, 60_000, 5 * 60_000);
        c.passingHoldMs = pick(Item.PASSING, 20_000, 40_000, 60_000);
        c.tooSlowPersistMs = pick(Item.TOO_SLOW, 8_000, 12_000, 15_000);
        c.overLimitPersistMs = pick(Item.OVER_LIMIT, 4_000, 4_000, 5_000);
        c.repeatedBrakeCount = (int) pick(Item.HARD_BRAKE, 2, 2, 3);
        c.repeatedBrakeWindowMs = pick(Item.HARD_BRAKE, 60_000, 3 * 60_000, 5 * 60_000);
        c.globalGapMs = pick(Item.GAP, 6_000, 12_000, 20_000);
        c.sameTypeCooldownMs = pick(Item.GAP, 30_000, 60_000, 2 * 60_000);
        return c;
    }

    private long pick(Item item, long fast, long normal, long real) {
        switch (levels.get(item)) {
            case FAST: return fast;
            case NORMAL: return normal;
            default: return real;
        }
    }

    /** 화면용: 이 항목이 지금 단계에서 어떻게 반응하는지 한 줄로. */
    public String explain(Item item) {
        CoachConfig c = toConfig();
        switch (item) {
            case PRAISE: return "흐름에 맞게 " + duration(c.smoothPraiseMs) + " 달리면 칭찬";
            case PASSING: return "추월 후 왼쪽 차로에 " + duration(c.passingHoldMs) + " 머물면 안내";
            case TOO_SLOW: return "흐름보다 느린 상태가 " + duration(c.tooSlowPersistMs) + " 이어지면 안내";
            case OVER_LIMIT: return "제한속도를 넘은 상태가 " + duration(c.overLimitPersistMs) + " 이어지면 안내";
            case HARD_BRAKE: return duration(c.repeatedBrakeWindowMs) + " 안에 급제동 " + c.repeatedBrakeCount + "번이면 안내";
            default: return "안내 사이 " + duration(c.globalGapMs) + ", 같은 안내는 " + duration(c.sameTypeCooldownMs) + " 뒤에";
        }
    }

    public Map<Item, Level> levels() {
        return java.util.Collections.unmodifiableMap(levels);
    }

    public static String duration(long ms) {
        long s = ms / 1000;
        if (s < 60) return s + "초";
        if (s % 60 == 0) return (s / 60) + "분";
        return (s / 60) + "분 " + (s % 60) + "초";
    }
}
