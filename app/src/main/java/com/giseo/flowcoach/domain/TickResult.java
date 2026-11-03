package com.giseo.flowcoach.domain;

import java.util.Collections;
import java.util.List;

/** CoachEngine.tick() 한 번의 결과. 화면 갱신과 음성 안내에 쓴다. */
public final class TickResult {

    public final FlowResult flow;
    public final List<CoachEvent> events;
    /** 이번에 말할 안내. 없으면 null. */
    public final CoachMessage message;
    public final int laneOffset;
    public final boolean passing;

    public TickResult(FlowResult flow, List<CoachEvent> events, CoachMessage message,
                      int laneOffset, boolean passing) {
        this.flow = flow;
        this.events = Collections.unmodifiableList(events);
        this.message = message;
        this.laneOffset = laneOffset;
        this.passing = passing;
    }
}
