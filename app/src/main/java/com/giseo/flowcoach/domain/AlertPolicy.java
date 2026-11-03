package com.giseo.flowcoach.domain;

/**
 * 안내를 어떤 방법으로 알릴지 정한다.
 *
 * Pleos Connect에서는 왼쪽에 내비가 고정되고, 오른쪽에는 흐름 코치가 떠 있을 수도
 * 다른 앱(음악 등)이 떠 있을 수도 있다. 그래서 '흐름 코치 화면이 보이는지'에 따라 알리는 방법을 나눈다.
 *
 * | 종류      | 예                                 | 화면이 보일 때        | 화면이 안 보일 때        |
 * |-----------|------------------------------------|-----------------------|--------------------------|
 * | 위험 경고 | 과속, 급제동 반복                  | 음성 + 패널 빨간 강조 | 음성 + 상단 팝업(몇 초)  |
 * | 습관 코칭 | 추월차로 미복귀, 잦은 차로 변경, 느림 | 음성 + 패널           | 음성만                   |
 * | 칭찬      | 흐름 잘 타고 있어요                | 음성 + 패널           | 음성만                   |
 * | 상태 변화 | 코칭 쉼(P·R), 정체 구간            | 패널                  | 상시 알림 내용만 바뀜    |
 * | 주행 끝   | 흐름 점수                          | 리포트 화면           | 알림 1번 → 누르면 리포트 |
 *
 * 모드에 따라 '소리'가 다르다.
 *  - 코치 모드  : 주행 중 말로 알려준다(위 표의 '음성').
 *  - 조용한 모드: 주행 중엔 말하지 않는다. 위험 경고만 짧은 경고음(띵-동) + 강조/팝업,
 *                 습관 코칭은 소리 없이 패널 문장으로만 보이고 리포트에 남는다.
 *                 (느림·칭찬은 CoachEngine이 조용한 모드에서 아예 만들지 않는다)
 *  - "지금 어때?"는 운전자가 직접 물은 것이라 두 모드 모두 음성으로 답한다.
 */
public final class AlertPolicy {

    public enum Category {
        DANGER, HABIT, PRAISE
    }

    /** 한 안내를 알리는 방법. */
    public static final class Delivery {
        public final boolean voice;
        /** 말 대신 짧은 경고음(조용한 모드의 위험 경고). */
        public final boolean chime;
        /** 흐름 코치 화면이 안 보일 때 상단 팝업(헤드업 알림). */
        public final boolean popup;
        /** 흐름 코치 화면이 보일 때 패널을 눈에 띄게 강조. */
        public final boolean highlight;

        Delivery(boolean voice, boolean chime, boolean popup, boolean highlight) {
            this.voice = voice;
            this.chime = chime;
            this.popup = popup;
            this.highlight = highlight;
        }
    }

    private AlertPolicy() {
    }

    public static Category categoryOf(CoachEvent.Type type) {
        switch (type) {
            case OVER_LIMIT:
            case REPEATED_HARD_BRAKE:
                return Category.DANGER;
            case SMOOTH_PRAISE:
                return Category.PRAISE;
            default:
                return Category.HABIT;
        }
    }

    /** 코치 모드 기준. */
    public static Delivery delivery(CoachEvent.Type type, boolean appVisible) {
        return delivery(type, appVisible, CoachMode.COACH);
    }

    /**
     * @param appVisible 흐름 코치 화면이 지금 보이는지(분할 화면 포함)
     */
    public static Delivery delivery(CoachEvent.Type type, boolean appVisible, CoachMode mode) {
        boolean danger = categoryOf(type) == Category.DANGER;
        boolean quiet = mode == CoachMode.QUIET;
        return new Delivery(!quiet, quiet && danger, danger && !appVisible, danger && appVisible);
    }
}
