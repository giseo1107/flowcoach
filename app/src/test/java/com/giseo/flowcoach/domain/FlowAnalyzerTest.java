package com.giseo.flowcoach.domain;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class FlowAnalyzerTest {

    private final FlowAnalyzer analyzer = new FlowAnalyzer(CoachConfig.defaults());
    private final RoadContext highway = new RoadContext(RoadType.EXPRESSWAY, 100, 95);

    @Test
    public void 흐름에_맞으면_원활() {
        assertEquals(FlowState.SMOOTH, analyzer.analyze(92, highway).state);
    }

    @Test
    public void 권장속도보다_15킬로_넘게_느리면_느림() {
        assertEquals(FlowState.TOO_SLOW, analyzer.analyze(78, highway).state);
    }

    @Test
    public void 제한속도_초과는_과속() {
        assertEquals(FlowState.OVER_LIMIT, analyzer.analyze(110, highway).state);
    }

    @Test
    public void GPS_오차_범위_안은_과속이_아님() {
        assertEquals(FlowState.SMOOTH, analyzer.analyze(102, highway).state);
    }

    @Test
    public void 도로_전체가_막히면_느려도_정체로_판단() {
        RoadContext jam = new RoadContext(RoadType.EXPRESSWAY, 100, 25);
        assertEquals(FlowState.CONGESTED, analyzer.analyze(20, jam).state);
    }

    @Test
    public void 흐름이_과속이어도_권장속도는_제한속도_이하() {
        RoadContext fastFlow = new RoadContext(RoadType.EXPRESSWAY, 100, 120);
        FlowResult r = analyzer.analyze(95, fastFlow);
        assertEquals(100, r.recommendedKmh, 0.001);
        assertEquals(FlowState.SMOOTH, r.state);
    }

    @Test
    public void 흐름_정보가_없으면_제한속도_기준() {
        RoadContext noFlow = new RoadContext(RoadType.EXPRESSWAY, 100, Double.NaN);
        assertEquals(FlowState.TOO_SLOW, analyzer.analyze(80, noFlow).state);
        assertEquals(FlowState.SMOOTH, analyzer.analyze(90, noFlow).state);
    }
}
