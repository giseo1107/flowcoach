package com.giseo.flowcoach.voice;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 미리 녹음해 두는 음성 안내 목록(키 → 문장).
 * 파일 이름은 res/raw/vc_{키}.m4a.
 * tools/make_voice_clips.sh 가 이 목록과 같은 문장으로 음성 파일을 만든다.
 *
 * 숫자가 들어가는 안내(예: "흐름보다 15킬로 느려요")는 녹음할 수 없어서
 * 숫자 없는 문장으로 녹음하고, 화면에는 숫자가 포함된 전체 문장을 보여준다.
 */
public final class VoicePrompts {

    public static final Map<String, String> LINES;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("demo_start", "시연 주행을 시작할게요.");
        m.put("console_start", "콘솔 연동 주행을 시작할게요.");
        m.put("auto_start", "주행을 시작했어요. 흐름에 맞춰 함께 갈게요.");
        m.put("smooth_praise", "지금 흐름 잘 타고 있어요. 이대로 가면 돼요.");
        m.put("passing_lane_hold", "추월을 마쳤다면 오른쪽 차로로 돌아가 볼까요?");
        m.put("too_slow", "지금 흐름보다 조금 느려요. 흐름에 맞춰 볼까요?");
        m.put("too_slow_passing", "흐름보다 조금 느려요. 편하게 가고 싶다면 오른쪽 차로가 좋아요.");
        m.put("over_limit", "제한속도를 넘었어요. 속도를 조금 줄여주세요.");
        m.put("repeated_hard_brake", "급제동이 반복되고 있어요. 앞차와 간격을 조금 더 두면 흐름이 부드러워져요.");
        m.put("frequent_lane_change", "차로 변경이 잦아요. 한 차로를 유지하는 게 흐름에 더 좋아요.");
        m.put("now_smooth", "지금 흐름에 잘 맞춰 달리고 있어요.");
        m.put("now_too_slow", "지금 흐름보다 조금 느려요.");
        m.put("now_over_limit", "지금 제한속도보다 빨라요.");
        m.put("now_congested", "지금 도로가 정체 중이에요. 앞차와 간격만 여유 있게 두세요.");
        m.put("now_unknown", "지금은 판단할 정보가 부족해요.");
        m.put("trip_end", "주행이 끝났어요. 흐름 리포트를 확인해 보세요.");
        LINES = Collections.unmodifiableMap(m);
    }

    private VoicePrompts() {
    }
}
