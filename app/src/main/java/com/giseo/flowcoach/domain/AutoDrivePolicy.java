package com.giseo.flowcoach.domain;

/**
 * 기어로 주행을 저절로 시작·종료하는 규칙(차량이라서 가능한 기능).
 *
 *  - 시작: 주행 중이 아닐 때 기어가 D로 "바뀌면" 시작한다(D에 머물러 있다고 계속 시작하지 않는다).
 *    그래서 D 상태에서 [주행 끝내기]를 눌러도 곧바로 다시 시작되지 않고, P·N·R을 거쳐 다시 D가 돼야 시작한다.
 *  - 종료: 주행 중 D를 한 번이라도 거친 뒤 P에 ENDS_AFTER_PARK_MS 동안 머물면 끝낸다.
 *    신호 대기처럼 잠깐 P에 넣는 경우를 거르려고 바로 끝내지 않는다.
 *    D를 거치지 않았으면(P에서 [콘솔 연동 주행]을 누르고 아직 출발 전) 끝내지 않는다.
 *  - 짧은 주행(REPORT_MIN_KM 미만, 주차장 안 이동 등)은 리포트 없이 조용히 끝낸다.
 *  - 시연 중(또는 시연이 막 시작·종료된 직후)에는 아무것도 하지 않는다. 시연은 에뮬레이터 차의 기어를 직접 바꾸기 때문.
 *
 * 판단만 하고 실제 시작·종료는 CoachRuntime이 한다. 1초마다 onGear()를 부른다.
 */
public final class AutoDrivePolicy {

    public enum Action { NONE, START, END_WITH_REPORT, END_QUIET }

    public static final long ENDS_AFTER_PARK_MS = 5_000;
    public static final double REPORT_MIN_KM = 0.5;

    private Gear lastGear = Gear.UNKNOWN;
    private long parkedSinceMs = -1;
    /** 이번 주행에서 D를 거쳤는지. */
    private boolean sawDrive = false;

    /** 주행이 시작될 때(자동이든 버튼이든) 부른다. */
    public void onDriveStarted(Gear gearNow) {
        sawDrive = gearNow == Gear.DRIVE;
        parkedSinceMs = -1;
    }

    /**
     * @param driving   지금 실제(콘솔 연동) 주행 중인지
     * @param demo      시연 중이거나 시연 직후라 기어 변화를 무시해야 하는지
     * @param drivenKm  이번 주행에서 달린 거리
     */
    public Action onGear(Gear gear, long nowMs, boolean driving, boolean demo, double drivenKm) {
        Gear previous = lastGear;
        lastGear = gear;
        if (demo) {
            parkedSinceMs = -1;
            return Action.NONE;
        }
        if (!driving) {
            parkedSinceMs = -1;
            return gear == Gear.DRIVE && previous != Gear.DRIVE ? Action.START : Action.NONE;
        }
        if (gear == Gear.DRIVE) sawDrive = true;
        if (gear != Gear.PARK || !sawDrive) {
            parkedSinceMs = -1;
            return Action.NONE;
        }
        if (parkedSinceMs < 0) parkedSinceMs = nowMs;
        if (nowMs - parkedSinceMs < ENDS_AFTER_PARK_MS) return Action.NONE;
        parkedSinceMs = -1;
        sawDrive = false;
        return drivenKm >= REPORT_MIN_KM ? Action.END_WITH_REPORT : Action.END_QUIET;
    }
}
