package com.giseo.flowcoach.domain;

import static org.junit.Assert.assertEquals;

import com.giseo.flowcoach.domain.AutoDrivePolicy.Action;

import org.junit.Test;

/** 기어로 주행 자동 시작·종료. */
public class AutoDrivePolicyTest {

    @Test
    public void D로_바뀌면_시작하고_P에_5초_머물면_리포트와_함께_끝난다() {
        AutoDrivePolicy p = new AutoDrivePolicy();
        assertEquals(Action.NONE, p.onGear(Gear.PARK, 0, false, false, 0));
        assertEquals(Action.START, p.onGear(Gear.DRIVE, 1000, false, false, 0));
        p.onDriveStarted(Gear.DRIVE);
        assertEquals(Action.NONE, p.onGear(Gear.DRIVE, 2000, true, false, 3.0));
        assertEquals(Action.NONE, p.onGear(Gear.PARK, 3000, true, false, 3.0));
        assertEquals(Action.NONE, p.onGear(Gear.PARK, 7000, true, false, 3.0));     // 4초: 아직
        assertEquals(Action.END_WITH_REPORT, p.onGear(Gear.PARK, 8000, true, false, 3.0));
    }

    @Test
    public void 잠깐_P에_넣었다가_D로_가면_이어서_달린다() {
        AutoDrivePolicy p = new AutoDrivePolicy();
        p.onDriveStarted(Gear.DRIVE);
        p.onGear(Gear.DRIVE, 0, true, false, 1.0);
        assertEquals(Action.NONE, p.onGear(Gear.PARK, 1000, true, false, 1.0));
        assertEquals(Action.NONE, p.onGear(Gear.PARK, 4000, true, false, 1.0));
        assertEquals(Action.NONE, p.onGear(Gear.DRIVE, 5000, true, false, 1.0));    // 다시 출발
        assertEquals(Action.NONE, p.onGear(Gear.PARK, 6000, true, false, 1.0));     // 주차 시간은 처음부터
        assertEquals(Action.NONE, p.onGear(Gear.PARK, 10000, true, false, 1.0));
        assertEquals(Action.END_WITH_REPORT, p.onGear(Gear.PARK, 11000, true, false, 1.0));
    }

    @Test
    public void 짧은_주행은_리포트_없이_끝낸다() {
        AutoDrivePolicy p = new AutoDrivePolicy();
        p.onDriveStarted(Gear.DRIVE);
        p.onGear(Gear.DRIVE, 0, true, false, 0.2);
        p.onGear(Gear.PARK, 1000, true, false, 0.2);
        assertEquals(Action.END_QUIET, p.onGear(Gear.PARK, 6000, true, false, 0.2));
    }

    @Test
    public void D에_머물러_있으면_다시_시작하지_않는다() {
        AutoDrivePolicy p = new AutoDrivePolicy();
        assertEquals(Action.START, p.onGear(Gear.DRIVE, 0, false, false, 0));       // 앱을 D에서 열었을 때도 시작
        // [주행 끝내기]로 끝냈지만 여전히 D
        assertEquals(Action.NONE, p.onGear(Gear.DRIVE, 1000, false, false, 0));
        assertEquals(Action.NONE, p.onGear(Gear.NEUTRAL, 2000, false, false, 0));
        assertEquals(Action.START, p.onGear(Gear.DRIVE, 3000, false, false, 0));    // N → D 는 다시 시작
    }

    @Test
    public void 출발_전_P에서는_끝내지_않는다() {
        // P에서 [콘솔 연동 주행]을 누르고 아직 D로 안 바꿨다
        AutoDrivePolicy p = new AutoDrivePolicy();
        p.onDriveStarted(Gear.PARK);
        assertEquals(Action.NONE, p.onGear(Gear.PARK, 0, true, false, 0));
        assertEquals(Action.NONE, p.onGear(Gear.PARK, 60_000, true, false, 0));
    }

    @Test
    public void 시연_중에는_기어를_무시한다() {
        AutoDrivePolicy p = new AutoDrivePolicy();
        assertEquals(Action.NONE, p.onGear(Gear.DRIVE, 0, false, true, 0));         // 시연이 기어를 D로
        assertEquals(Action.NONE, p.onGear(Gear.PARK, 1000, false, true, 0));       // 시연 끝, P로
        assertEquals(Action.NONE, p.onGear(Gear.PARK, 2000, false, false, 0));
        assertEquals(Action.START, p.onGear(Gear.DRIVE, 3000, false, false, 0));    // 그 뒤 직접 D → 시작
    }

    @Test
    public void 기어를_모르면_시작하지_않는다() {
        AutoDrivePolicy p = new AutoDrivePolicy();
        assertEquals(Action.NONE, p.onGear(Gear.UNKNOWN, 0, false, false, 0));
        assertEquals(Action.NONE, p.onGear(Gear.UNKNOWN, 1000, false, false, 0));
    }
}
