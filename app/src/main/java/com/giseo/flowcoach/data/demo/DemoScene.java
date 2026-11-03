package com.giseo.flowcoach.data.demo;

import com.giseo.flowcoach.domain.CoachEvent;

/**
 * 시연 장면(기능별 6개). 버튼 하나 = 장면 하나 = 기능 하나.
 * 각 장면은 짧게(약 20~35초) 만들고, 장면이 시작되면 바로 그 기능의 반응이 나오도록 속도·신호를 짰다.
 */
public enum DemoScene {
    SMOOTH("흐름에 맞음", "흐름에 맞춰 달리면 칭찬해요",
            "흐름에 맞춰 15초쯤 달리면 \"지금 흐름 잘 타고 있어요\"",
            CoachEvent.Type.SMOOTH_PRAISE),
    PASSING("추월 후 미복귀", "추월한 뒤 왼쪽 차로에 계속 머물러요",
            "왼쪽 차로에 20초 넘게 머물면 \"추월을 마쳤다면 오른쪽 차로로 돌아가 볼까요?\"",
            CoachEvent.Type.PASSING_LANE_HOLD),
    TOO_SLOW("흐름보다 느림", "흐름보다 느리게 달리다가, 도로 전체가 막혀요",
            "느린 상태가 8초 넘게 이어지면 \"흐름보다 조금 느려요\", 이어서 정체 구간에서는 느려도 안내하지 않아요",
            CoachEvent.Type.TOO_SLOW),
    HARD_BRAKE("급제동 반복", "짧은 시간에 급제동을 두 번 해요",
            "두 번째 급제동에서 \"급제동이 반복되고 있어요. 앞차와 간격을 조금 더 두면…\"",
            CoachEvent.Type.REPEATED_HARD_BRAKE),
    LANE_CHANGES("잦은 차로 변경", "1분 안에 차로를 네 번 바꿔요",
            "세 번째 차로 변경에서 \"차로 변경이 잦아요. 한 차로를 유지하는 게…\"",
            CoachEvent.Type.FREQUENT_LANE_CHANGE),
    OVER_LIMIT("제한속도 초과", "제한속도 100km/h 도로에서 112km/h까지 올려요",
            "제한속도를 넘고 4초 뒤 \"제한속도 100킬로를 넘었어요. 속도를 조금 줄여주세요\"",
            CoachEvent.Type.OVER_LIMIT);

    /** 버튼에 쓰는 짧은 이름. */
    public final String title;
    /** 장면에서 일어나는 일. */
    public final String description;
    /** 나와야 하는 반응(화면에 미리 보여준다). */
    public final String expected;
    /** 이 장면이 보여주려는 안내 종류(테스트에서 실제로 나오는지 확인). */
    public final CoachEvent.Type expectedEvent;

    DemoScene(String title, String description, String expected, CoachEvent.Type expectedEvent) {
        this.title = title;
        this.description = description;
        this.expected = expected;
        this.expectedEvent = expectedEvent;
    }
}
