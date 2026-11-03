package com.giseo.flowcoach.domain;

import java.util.ArrayList;
import java.util.List;

/**
 * 주행 후 리포트. 정차·도착 후에만 화면에 보여준다(주행 중에는 음성만).
 *
 * 흐름 점수(0~100) 계산:
 *   100점에서 시작해
 *   - 흐름보다 느렸던 시간 비율 × 30
 *   - 제한속도를 넘은 시간 비율 × 40
 *   - 추월 후 미복귀 1회당 10, 잦은 차로 변경 1회당 8, 급감속 1회당 4
 *   를 뺀다. 계산식이 단순해야 운전자가 "왜 이 점수인지" 이해할 수 있다.
 */
public final class TripReport {

    private long startMs = -1;
    private long lastMs = -1;
    /** 주차·후진으로 기록을 쉬었다가 다시 시작했는지. */
    private boolean gap = false;
    private double distanceKm;
    private double maxSpeedKmh;

    private double smoothSec;
    private double tooSlowSec;
    private double overLimitSec;
    private double congestedSec;

    private int laneChanges;
    private int passingHolds;
    private int frequentLaneChanges;
    private int hardBrakes;

    void record(DrivingSnapshot s, FlowResult flow, List<CoachEvent> events) {
        if (startMs < 0) startMs = s.timeMs;
        double dtSec = (lastMs < 0 || gap) ? 0 : Math.min(5, Math.max(0, (s.timeMs - lastMs) / 1000.0));
        lastMs = s.timeMs;
        gap = false;

        distanceKm += s.speedKmh * dtSec / 3600.0;
        maxSpeedKmh = Math.max(maxSpeedKmh, s.speedKmh);

        switch (flow.state) {
            case SMOOTH: smoothSec += dtSec; break;
            case TOO_SLOW: tooSlowSec += dtSec; break;
            case OVER_LIMIT: overLimitSec += dtSec; break;
            case CONGESTED: congestedSec += dtSec; break;
            default: break;
        }

        for (CoachEvent e : events) {
            switch (e.type) {
                case LANE_CHANGE_LEFT:
                case LANE_CHANGE_RIGHT: laneChanges++; break;
                case PASSING_LANE_HOLD: passingHolds++; break;
                case FREQUENT_LANE_CHANGE: frequentLaneChanges++; break;
                case HARD_BRAKE: hardBrakes++; break;
                default: break;
            }
        }
    }

    /** 주차·후진처럼 기록하지 않은 구간이 있었음을 알린다(다음 기록에서 그 시간을 더하지 않도록). */
    void markGap() {
        gap = true;
    }

    /** 흐름을 평가할 수 있었던 시간(정체·정차 제외). */
    public double evaluatedSec() {
        return smoothSec + tooSlowSec + overLimitSec;
    }

    public int score() {
        return score(deductions(smoothSec, tooSlowSec, overLimitSec, passingHolds, frequentLaneChanges, hardBrakes));
    }

    /** 점수에서 빠지는 항목. 리포트 화면이 "왜 이 점수인지"를 항목별로 보여줄 때 쓴다. */
    public enum Item { PASSING, FREQUENT_LANE, HARD_BRAKE, OVER_LIMIT, TOO_SLOW }

    /** 항목 하나가 깎은 점수. */
    public static final class Deduction {
        public final Item item;
        public final double points;

        Deduction(Item item, double points) {
            this.item = item;
            this.points = points;
        }
    }

    /** 이번 주행의 항목별 감점(큰 것부터, 0점인 항목도 포함). */
    public List<Deduction> deductions() {
        return deductions(smoothSec, tooSlowSec, overLimitSec, passingHolds, frequentLaneChanges, hardBrakes);
    }

    /** 기록 값으로 항목별 감점을 계산한다(큰 것부터). score()와 리포트 화면이 같은 식을 쓴다. */
    public static List<Deduction> deductions(double smoothSec, double tooSlowSec, double overLimitSec,
                                             int passingHolds, int frequentLaneChanges, int hardBrakes) {
        double evaluated = smoothSec + tooSlowSec + overLimitSec;
        List<Deduction> list = new ArrayList<>();
        list.add(new Deduction(Item.PASSING, 10.0 * passingHolds));
        list.add(new Deduction(Item.FREQUENT_LANE, 8.0 * frequentLaneChanges));
        list.add(new Deduction(Item.HARD_BRAKE, 4.0 * hardBrakes));
        list.add(new Deduction(Item.OVER_LIMIT, evaluated > 0 ? 40 * (overLimitSec / evaluated) : 0));
        list.add(new Deduction(Item.TOO_SLOW, evaluated > 0 ? 30 * (tooSlowSec / evaluated) : 0));
        // 큰 감점부터(같으면 위 순서 유지)
        list.sort((a, b) -> Double.compare(b.points, a.points));
        return list;
    }

    /** 감점 목록 → 0~100점. */
    public static int score(List<Deduction> deductions) {
        double score = 100;
        for (Deduction d : deductions) score -= d.points;
        return (int) Math.round(Math.max(0, Math.min(100, score)));
    }

    public String grade() {
        return grade(score());
    }

    public static String grade(int s) {
        if (s >= 90) return "흐름 마스터";
        if (s >= 75) return "좋은 흐름";
        if (s >= 50) return "조금 더 부드럽게";
        return "흐름을 끊고 있어요";
    }

    /** 도착 후 음성으로 읽어 줄 요약. */
    public String spokenSummary() {
        return "오늘 흐름 점수는 " + score() + "점이에요. "
                + "급감속 " + hardBrakes + "번, 차로 변경 " + laneChanges + "번이 있었어요.";
    }

    public long durationSec() {
        return startMs < 0 ? 0 : (lastMs - startMs) / 1000;
    }

    public double getDistanceKm() { return distanceKm; }
    public double getMaxSpeedKmh() { return maxSpeedKmh; }
    public double getSmoothSec() { return smoothSec; }
    public double getTooSlowSec() { return tooSlowSec; }
    public double getOverLimitSec() { return overLimitSec; }
    public double getCongestedSec() { return congestedSec; }
    public int getLaneChanges() { return laneChanges; }
    public int getPassingHolds() { return passingHolds; }
    public int getFrequentLaneChanges() { return frequentLaneChanges; }
    public int getHardBrakes() { return hardBrakes; }
}
