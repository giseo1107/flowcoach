package com.giseo.flowcoach.domain;

/**
 * 판단 기준값 모음. 실제 주행용(defaults)과 시연용(demo)을 나눠서,
 * 시연 때는 같은 로직을 짧은 시간 안에 보여줄 수 있게 한다.
 */
public final class CoachConfig {

    // ---- 흐름 진단 ----
    /** 구간 흐름 속도가 이보다 낮으면 정체로 본다. */
    public double congestionFlowKmh = 40;
    /** 권장 속도보다 이만큼 느리면 '흐름보다 느림'. */
    public double slowMarginKmh = 15;
    /** 제한속도를 이만큼 넘으면 과속. GPS 오차를 감안한 여유값. */
    public double overLimitToleranceKmh = 3;
    /** 이 속도 미만은 정차/서행으로 보고 흐름 판단을 하지 않는다. */
    public double minMovingKmh = 20;

    // ---- 차로 습관 ----
    /** 방향지시등이 이 시간 이상 켜졌다 꺼져야 차로 변경으로 본다(실수로 툭 건드린 것 제외). */
    public long minSignalMs = 1000;
    /** 차로 변경으로 인정할 최소 속도. */
    public double laneChangeMinSpeedKmh = 30;
    /** 신호 전후 진행 방향이 이보다 많이 바뀌면 차로 변경이 아니라 회전/분기로 본다. */
    public double maxHeadingChangeForLaneChangeDeg = 20;
    /** 왼쪽으로 추월한 뒤 이 시간 동안 복귀하지 않으면 '추월 후 미복귀'. */
    public long passingHoldMs = 60_000;
    /**
     * 도로교통법 시행규칙(별표 9): 정체로 시속 80km 이상 달릴 수 없을 때는 추월차로를 계속 써도 된다.
     * 그래서 흐름이 이보다 느리면 미복귀 경고를 하지 않는다.
     */
    public double passingRuleMinFlowKmh = 80;
    /** frequentWindowMs 안에 frequentCount번 이상 차로를 바꾸면 '잦은 차로 변경'. */
    public long frequentWindowMs = 60_000;
    public int frequentCount = 3;

    // ---- 급감속 ----
    /** 초당 이 이상 속도가 줄면 급감속(약 0.3g). */
    public double hardDecelKmhPerSec = 10;
    /** 급감속 판단을 시작하는 최소 속도. */
    public double hardBrakeMinSpeedKmh = 30;
    /** 페달을 이 이상 깊게 밟으면 급제동으로 본다(속도 데이터가 늦을 때 보완). */
    public int hardPedalPercent = 70;
    /** 한 번 감지한 뒤 이 시간 동안은 같은 급감속을 다시 세지 않는다. */
    public long hardBrakeRefractoryMs = 3_000;
    /** repeatedBrakeWindowMs 안에 repeatedBrakeCount번 이상 급감속하면 차간 간격 조언. */
    public long repeatedBrakeWindowMs = 5 * 60_000;
    public int repeatedBrakeCount = 3;

    // ---- 음성 안내 ----
    /** '느림' 상태가 이만큼 이어져야 안내(순간적인 감속은 무시). */
    public long tooSlowPersistMs = 15_000;
    /** '과속' 상태가 이만큼 이어져야 안내. */
    public long overLimitPersistMs = 5_000;
    /** '원활' 상태가 이만큼 이어지면 칭찬(코치 모드). */
    public long smoothPraiseMs = 5 * 60_000;
    /** 어떤 안내든 최소 이 간격을 둔다(잔소리 방지). */
    public long globalGapMs = 20_000;
    /** 같은 종류의 안내는 이 간격 안에 반복하지 않는다. */
    public long sameTypeCooldownMs = 2 * 60_000;

    public static CoachConfig defaults() {
        return new CoachConfig();
    }

    /** 시연용: 같은 로직, 짧은 시간. */
    public static CoachConfig demo() {
        CoachConfig c = new CoachConfig();
        c.passingHoldMs = 20_000;
        c.tooSlowPersistMs = 8_000;
        c.overLimitPersistMs = 4_000;
        c.smoothPraiseMs = 15_000;
        c.globalGapMs = 6_000;
        c.sameTypeCooldownMs = 30_000;
        c.repeatedBrakeWindowMs = 60_000;
        c.repeatedBrakeCount = 2;
        return c;
    }
}
