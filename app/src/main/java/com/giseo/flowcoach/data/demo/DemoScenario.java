package com.giseo.flowcoach.data.demo;

import com.giseo.flowcoach.domain.DrivingSnapshot;
import com.giseo.flowcoach.domain.RoadContext;
import com.giseo.flowcoach.domain.RoadType;
import com.giseo.flowcoach.domain.TurnSignal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 시연용 고속도로 주행 시나리오(약 2분 45초).
 * 에뮬레이터에는 속도·GPS 조작 기능이 없어서, 실제 주행과 같은 형태의 데이터를 1초 단위로 만들어 재생한다.
 * 판단 로직(CoachEngine)은 실제 주행과 완전히 같은 코드를 탄다.
 *
 * 흐름: 진입 → 원활(칭찬) → 추월 후 미복귀 → 복귀 → 흐름보다 느림 → 급제동 반복
 *      → 잦은 차로 변경 → 과속 → 정체 구간(안내 없음) → 정차
 */
public final class DemoScenario {

    /** 시연 한 프레임(1초). */
    public static final class Frame {
        public final DrivingSnapshot snapshot;
        public final RoadContext road;
        /** 화면 하단에 띄울 시연 설명. */
        public final String caption;

        Frame(DrivingSnapshot snapshot, RoadContext road, String caption) {
            this.snapshot = snapshot;
            this.road = road;
            this.caption = caption;
        }
    }

    private static final int LIMIT = 100;
    private static final double HEADING = 90.0; // 동쪽으로 직진

    private final List<Frame> frames = new ArrayList<>();
    private final long startMs;

    private DemoScenario(long startMs) {
        this.startMs = startMs;
    }

    public static List<Frame> highway(long startMs) {
        DemoScenario d = new DemoScenario(startMs);
        //       시작초 끝초  시작속도 끝속도 흐름  방향지시등        페달 설명
        d.seg(0, 6, 70, 96, 98, TurnSignal.OFF, 0, "고속도로 본선 합류");
        d.seg(6, 30, 96, 96, 98, TurnSignal.OFF, 0, "흐름에 맞춰 주행 중");
        d.seg(30, 32, 96, 98, 98, TurnSignal.LEFT, 0, "추월하려고 왼쪽 차로로 (방향지시등 ←)");
        d.seg(32, 56, 98, 93, 98, TurnSignal.OFF, 0, "추월 후 왼쪽 차로에 계속 머무는 중");
        d.seg(56, 58, 93, 93, 98, TurnSignal.RIGHT, 0, "오른쪽 차로로 복귀 (방향지시등 →)");
        d.seg(58, 66, 93, 76, 98, TurnSignal.OFF, 0, "흐름보다 느려지는 중");
        d.seg(66, 78, 76, 76, 98, TurnSignal.OFF, 0, "흐름보다 느리게 주행");
        d.seg(78, 88, 76, 95, 98, TurnSignal.OFF, 0, "다시 흐름에 맞춤");
        d.seg(88, 90, 95, 70, 95, TurnSignal.OFF, 85, "급제동");
        d.seg(90, 95, 70, 90, 95, TurnSignal.OFF, 0, "다시 가속");
        d.seg(95, 97, 90, 64, 95, TurnSignal.OFF, 85, "또 급제동");
        d.seg(97, 105, 64, 95, 98, TurnSignal.OFF, 0, "다시 가속");
        d.seg(105, 107, 95, 95, 98, TurnSignal.LEFT, 0, "차로 변경 ←");
        d.seg(107, 110, 95, 95, 98, TurnSignal.OFF, 0, "");
        d.seg(110, 112, 95, 95, 98, TurnSignal.RIGHT, 0, "차로 변경 →");
        d.seg(112, 115, 95, 95, 98, TurnSignal.OFF, 0, "");
        d.seg(115, 117, 95, 95, 98, TurnSignal.LEFT, 0, "차로 변경 ←");
        d.seg(117, 120, 95, 95, 98, TurnSignal.OFF, 0, "");
        d.seg(120, 122, 95, 95, 98, TurnSignal.RIGHT, 0, "차로 변경 →");
        d.seg(122, 128, 95, 112, 105, TurnSignal.OFF, 0, "속도를 올리는 중");
        d.seg(128, 137, 112, 112, 105, TurnSignal.OFF, 0, "제한속도 초과");
        d.seg(137, 145, 112, 70, 60, TurnSignal.OFF, 0, "앞쪽 정체로 감속");
        d.seg(145, 158, 70, 25, 30, TurnSignal.OFF, 0, "정체 구간: 도로 전체가 느릴 땐 안내하지 않음");
        d.seg(158, 165, 25, 0, 25, TurnSignal.OFF, 30, "정차");
        return Collections.unmodifiableList(d.frames);
    }

    /**
     * 기능별 짧은 장면. 버튼으로 하나씩 골라 볼 수 있게 했다.
     * 각 장면은 새 CoachEngine에서 재생된다고 가정하고(쿨다운이 섞이지 않게) 반응이 빨리 나오도록 짰다.
     */
    public static List<Frame> scene(DemoScene scene, long startMs) {
        DemoScenario d = new DemoScenario(startMs);
        TurnSignal off = TurnSignal.OFF;
        switch (scene) {
            case SMOOTH:
                d.seg(0, 5, 70, 96, 98, off, 0, "고속도로 본선 합류");
                d.seg(5, 24, 96, 96, 98, off, 0, "흐름(98km/h)에 맞춰 주행 중");
                break;
            case PASSING:
                d.seg(0, 4, 96, 96, 98, off, 0, "흐름에 맞춰 주행 중");
                d.seg(4, 6, 96, 97, 98, TurnSignal.LEFT, 0, "추월하려고 왼쪽 차로로 (방향지시등 ←)");
                d.seg(6, 32, 97, 95, 98, off, 0, "추월 후 왼쪽 차로에 계속 머무는 중");
                d.seg(32, 34, 95, 95, 98, TurnSignal.RIGHT, 0, "오른쪽 차로로 복귀 (방향지시등 →)");
                d.seg(34, 38, 95, 95, 98, off, 0, "복귀 완료");
                break;
            case TOO_SLOW:
                d.seg(0, 4, 96, 96, 98, off, 0, "흐름에 맞춰 주행 중");
                d.seg(4, 8, 96, 76, 98, off, 0, "흐름보다 느려지는 중");
                d.seg(8, 20, 76, 76, 98, off, 0, "흐름(98)보다 느리게 주행 (76km/h)");
                d.seg(20, 26, 76, 25, 30, off, 0, "앞쪽 정체로 도로 전체가 느려짐");
                d.seg(26, 38, 25, 25, 30, off, 0, "정체 구간: 도로 전체가 느릴 땐 안내하지 않음");
                break;
            case HARD_BRAKE:
                d.seg(0, 4, 95, 95, 95, off, 0, "흐름에 맞춰 주행 중");
                d.seg(4, 6, 95, 70, 95, off, 85, "급제동");
                d.seg(6, 10, 70, 90, 95, off, 0, "다시 가속");
                d.seg(10, 12, 90, 64, 95, off, 85, "또 급제동");
                d.seg(12, 20, 64, 95, 95, off, 0, "다시 가속");
                break;
            case LANE_CHANGES:
                d.seg(0, 3, 95, 95, 98, off, 0, "흐름에 맞춰 주행 중");
                d.seg(3, 5, 95, 95, 98, TurnSignal.LEFT, 0, "차로 변경 ← (1번째)");
                d.seg(5, 8, 95, 95, 98, off, 0, "");
                d.seg(8, 10, 95, 95, 98, TurnSignal.RIGHT, 0, "차로 변경 → (2번째)");
                d.seg(10, 13, 95, 95, 98, off, 0, "");
                d.seg(13, 15, 95, 95, 98, TurnSignal.LEFT, 0, "차로 변경 ← (3번째)");
                d.seg(15, 18, 95, 95, 98, off, 0, "");
                d.seg(18, 20, 95, 95, 98, TurnSignal.RIGHT, 0, "차로 변경 → (4번째)");
                d.seg(20, 26, 95, 95, 98, off, 0, "한 차로 유지");
                break;
            case OVER_LIMIT:
                d.seg(0, 4, 95, 95, 105, off, 0, "흐름에 맞춰 주행 중 (제한 100km/h)");
                d.seg(4, 10, 95, 112, 105, off, 0, "속도를 올리는 중");
                d.seg(10, 20, 112, 112, 105, off, 0, "제한속도 초과 (112km/h)");
                break;
            default:
                break;
        }
        return Collections.unmodifiableList(d.frames);
    }

    /** [fromSec, toSec) 구간을 1초 단위로 채운다. 속도는 선형 보간. */
    private void seg(int fromSec, int toSec, double fromKmh, double toKmh, double flowKmh,
                     TurnSignal signal, int pedalPercent, String caption) {
        int len = toSec - fromSec;
        for (int i = 0; i < len; i++) {
            double ratio = len == 1 ? 1 : (double) (i + 1) / len;
            double speed = fromKmh + (toKmh - fromKmh) * ratio;
            long t = startMs + (fromSec + i) * 1000L;
            DrivingSnapshot s = new DrivingSnapshot(t, speed, HEADING, signal,
                    pedalPercent > 0, pedalPercent);
            RoadContext road = new RoadContext(RoadType.EXPRESSWAY, LIMIT, flowKmh);
            frames.add(new Frame(s, road, caption));
        }
    }
}
