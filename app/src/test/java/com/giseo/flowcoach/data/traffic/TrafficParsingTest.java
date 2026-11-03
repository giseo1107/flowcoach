package com.giseo.flowcoach.data.traffic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class TrafficParsingTest {

    private static final String SAMPLE = "{\"header\":{\"resultCode\":0,\"resultMsg\":\"success\",\"totalCount\":4},"
            + "\"body\":{\"totalCount\":4,\"items\":["
            + "{\"roadName\":\"경부선\",\"linkId\":\"1000001\",\"speed\":\"92.0\",\"createdDate\":\"20261029153000\"},"
            + "{\"roadName\":\"경부선\",\"linkId\":\"1000002\",\"speed\":\"88.5\",\"createdDate\":\"20261029153000\"},"
            + "{\"roadName\":\"경부선\",\"linkId\":\"1000003\",\"speed\":\"35.0\",\"createdDate\":\"20261029153000\"},"
            + "{\"roadName\":\"용인서울선\",\"linkId\":\"2000001\",\"speed\":70,\"createdDate\":\"20261029153000\"}"
            + "]}}";

    @Test
    public void 작은_JSON_파서() {
        Object v = MiniJson.parse("{\"a\":[1,2.5,\"x\\\"y\",true,null,{\"b\":\"\\uAC00\"}]}");
        List<?> a = (List<?>) ((Map<?, ?>) v).get("a");
        assertEquals(1.0, a.get(0));
        assertEquals(2.5, a.get(1));
        assertEquals("x\"y", a.get(2));
        assertEquals(Boolean.TRUE, a.get(3));
        assertNull(a.get(4));
        assertEquals("가", ((Map<?, ?>) a.get(5)).get("b"));
    }

    @Test
    public void 교통소통정보_응답_파싱() {
        ItsTrafficParser.Result r = ItsTrafficParser.parse(SAMPLE);
        assertTrue(r.ok());
        assertEquals(4, r.links.size());
        assertEquals("경부선", r.links.get(0).roadName);
        assertEquals(92.0, r.links.get(0).speedKmh, 0.001);
        assertEquals(70.0, r.links.get(3).speedKmh, 0.001); // 숫자 문자열이 아닌 숫자도 읽음
    }

    @Test
    public void items가_객체_하나여도_읽음() {
        String one = "{\"body\":{\"items\":{\"item\":{\"roadName\":\"경부선\",\"speed\":\"80\"}}}}";
        ItsTrafficParser.Result r = ItsTrafficParser.parse(one);
        assertTrue(r.ok());
        assertEquals(1, r.links.size());
    }

    @Test
    public void 서버_오류는_사유를_알려줌() {
        String err = "{\"header\":{\"resultCode\":\"4\",\"resultMsg\":\"인증키 오류\"}}";
        ItsTrafficParser.Result r = ItsTrafficParser.parse(err);
        assertFalse(r.ok());
        assertTrue(r.error.contains("인증키 오류"));
    }

    @Test
    public void JSON이_아니면_실패() {
        assertFalse(ItsTrafficParser.parse("<response><header/></response>").ok());
        assertFalse(ItsTrafficParser.parse("").ok());
    }

    @Test
    public void 원하는_도로만_골라_중앙값() {
        List<LinkSpeed> links = ItsTrafficParser.parse(SAMPLE).links;
        FlowEstimator.Estimate e = FlowEstimator.estimate(links, "경부");
        assertEquals("경부선", e.roadName);
        assertEquals(3, e.linkCount);
        assertEquals(88.5, e.speedKmh, 0.001); // 35, 88.5, 92 의 중앙값 — 반대 방향 정체에 덜 흔들림
    }

    @Test
    public void 원하는_도로가_없으면_전체로() {
        List<LinkSpeed> links = Arrays.asList(new LinkSpeed("서해안선", "1", 100, ""),
                new LinkSpeed("서해안선", "2", 90, ""));
        FlowEstimator.Estimate e = FlowEstimator.estimate(links, "경부");
        assertEquals(2, e.linkCount);
        assertEquals(95, e.speedKmh, 0.001);
    }

    @Test
    public void 속도_0은_수집_안된_것으로_제외() {
        List<LinkSpeed> links = Arrays.asList(new LinkSpeed("경부선", "1", 0, ""));
        assertNull(FlowEstimator.estimate(links, null));
    }

    @Test
    public void 요청_URL_형식() {
        String url = new ItsTrafficClient("KEY").buildUrl(ItsTrafficClient.TYPE_ALL, 127.08, 127.12, 37.38, 37.42);
        assertEquals("https://openapi.its.go.kr:9443/trafficInfo?apiKey=KEY&type=all&drcType=all"
                + "&minX=127.080000&maxX=127.120000&minY=37.380000&maxY=37.420000&getType=json", url);
    }

    /** 2026-10-29 실제 응답 앞부분과 같은 형식. */
    private static final String REAL_FORMAT = "{\"header\":{\"resultCode\":0,\"resultMsg\":\"SUCCESS\"},"
            + "\"body\":{\"totalCount\":3,\"items\":["
            + "{\"roadName\":\"대왕판교로\",\"roadDrcType\":\"\",\"linkNo\":\"\",\"linkId\":\"2040000300\","
            + "\"startNodeId\":\"2040000400\",\"endNodeId\":\"2040000300\",\"speed\":\"50.3\","
            + "\"travelTime\":\"68.46\",\"createdDate\":\"20261029164500\"},"
            + "{\"roadName\":\"경부고속도로\",\"roadDrcType\":\"1\",\"linkNo\":\"\",\"linkId\":\"1\",\"speed\":\"94.0\",\"createdDate\":\"20261029164500\"},"
            + "{\"roadName\":\"경부고속도로\",\"roadDrcType\":\"2\",\"linkNo\":\"\",\"linkId\":\"2\",\"speed\":\"86.0\",\"createdDate\":\"20261029164500\"}"
            + "]}}";

    @Test
    public void 실제_응답_형식에서_경부만_골라냄() {
        ItsTrafficParser.Result r = ItsTrafficParser.parse(REAL_FORMAT);
        assertTrue(r.ok());
        assertEquals(3, r.links.size());
        FlowEstimator.Estimate e = FlowEstimator.estimateStrict(r.links, "경부");
        assertEquals("경부고속도로", e.roadName);
        assertEquals(2, e.linkCount);
        assertEquals(90.0, e.speedKmh, 0.001);
        assertEquals(com.giseo.flowcoach.domain.RoadType.EXPRESSWAY,
                TrafficFlowRepository.guessRoadType(e.roadName, "경부"));
    }

    /** 2026-10-29 type=ex&routeNo=0010 응답 형식: 방향값이 "상행"/"하행"으로 온다. */
    private static final String EX_FORMAT = "{\"header\":{\"resultCode\":0,\"resultMsg\":\"SUCCESS\"},\"body\":{\"items\":["
            + "{\"roadName\":\"경부고속도로\",\"roadDrcType\":\"상행\",\"speed\":\"93.9\"},"
            + "{\"roadName\":\"경부고속도로\",\"roadDrcType\":\"상행\",\"speed\":\"90.9\"},"
            + "{\"roadName\":\"경부고속도로\",\"roadDrcType\":\"상행\",\"speed\":\"74.3\"},"
            + "{\"roadName\":\"경부고속도로\",\"roadDrcType\":\"하행\",\"speed\":\"18.0\"},"
            + "{\"roadName\":\"경부고속도로\",\"roadDrcType\":\"하행\",\"speed\":\"79.5\"},"
            + "{\"roadName\":\"경부고속도로\",\"roadDrcType\":\"하행\",\"speed\":\"92.9\"}"
            + "]}}";

    @Test
    public void 상행_하행을_나눠_계산() {
        List<LinkSpeed> links = ItsTrafficParser.parse(EX_FORMAT).links;
        assertEquals("상행", links.get(0).direction);
        FlowEstimator.Estimate up = FlowEstimator.estimateStrict(links, "경부", "상행");
        FlowEstimator.Estimate down = FlowEstimator.estimateStrict(links, "경부", "하행");
        assertEquals(3, up.linkCount);
        assertEquals(90.9, up.speedKmh, 0.001);
        assertEquals(79.5, down.speedKmh, 0.001);
        assertNull(FlowEstimator.estimateStrict(links, "경부", "없는방향"));
    }

    @Test
    public void 노선번호_포함_URL() {
        String url = new ItsTrafficClient("KEY").buildUrl(ItsTrafficClient.TYPE_EXPRESSWAY,
                ItsTrafficClient.ROUTE_GYEONGBU, 127.08, 127.12, 37.38, 37.42);
        assertEquals("https://openapi.its.go.kr:9443/trafficInfo?apiKey=KEY&type=ex&routeNo=0010&drcType=all"
                + "&minX=127.080000&maxX=127.120000&minY=37.380000&maxY=37.420000&getType=json", url);
    }

    @Test
    public void 원하는_도로가_없으면_시내도로로_대신하지_않음() {
        List<LinkSpeed> city = Arrays.asList(new LinkSpeed("대왕판교로", "1", 50.3, ""));
        assertNull(FlowEstimator.estimateStrict(city, "경부"));
    }
}
