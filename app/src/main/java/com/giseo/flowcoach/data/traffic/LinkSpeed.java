package com.giseo.flowcoach.data.traffic;

/** 교통소통정보 API의 링크(도로 구간) 하나의 평균 통행속도. */
public final class LinkSpeed {

    public final String roadName;
    public final String linkId;
    public final double speedKmh;
    /** 데이터 생성 시각(API 원문, 예: 20261029153000). */
    public final String createdDate;
    /** 방향(roadDrcType). 고속도로 조회(type=ex)에서 "상행"/"하행", 그 외에는 빈 문자열. */
    public final String direction;

    public LinkSpeed(String roadName, String linkId, double speedKmh, String createdDate) {
        this(roadName, linkId, speedKmh, createdDate, "");
    }

    public LinkSpeed(String roadName, String linkId, double speedKmh, String createdDate, String direction) {
        this.roadName = roadName == null ? "" : roadName;
        this.linkId = linkId == null ? "" : linkId;
        this.speedKmh = speedKmh;
        this.createdDate = createdDate == null ? "" : createdDate;
        this.direction = direction == null ? "" : direction;
    }

    @Override
    public String toString() {
        return roadName + (direction.isEmpty() ? "" : "(" + direction + ")") + "#" + linkId + "=" + speedKmh;
    }
}
