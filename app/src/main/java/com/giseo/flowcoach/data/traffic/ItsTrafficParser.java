package com.giseo.flowcoach.data.traffic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 국가교통정보센터(ITS) 교통소통정보(trafficInfo) 응답 파서.
 *
 * 공개 문서가 없어서 확인된 범위(roadName, speed, createdDate 등)를 기준으로,
 * 응답 구조가 조금 달라도 동작하도록 느슨하게 읽는다.
 *  - items 위치: body.items / body.items.item / items / response.body.items ...
 *  - items가 객체 하나로 올 때도 배열로 취급
 *  - 숫자가 문자열("85.0")로 와도 읽음
 */
public final class ItsTrafficParser {

    /** 파싱 결과. 오류면 error에 사유가 들어간다. */
    public static final class Result {
        public final List<LinkSpeed> links;
        public final String error;

        Result(List<LinkSpeed> links, String error) {
            this.links = links;
            this.error = error;
        }

        public boolean ok() {
            return error == null;
        }
    }

    private ItsTrafficParser() {
    }

    public static Result parse(String body) {
        if (body == null || body.trim().isEmpty()) return fail("빈 응답");
        Object root;
        try {
            root = MiniJson.parse(body.trim());
        } catch (RuntimeException e) {
            String head = body.trim();
            if (head.startsWith("<")) return fail("JSON이 아닌 응답(XML/HTML) — getType=json 확인");
            return fail("JSON 파싱 실패");
        }

        // 서버가 알려주는 오류(예: 인증키 오류)
        String serverError = findServerError(root);
        if (serverError != null) return fail(serverError);

        List<Object> items = findItems(root);
        if (items == null) return fail("items 없음");

        List<LinkSpeed> links = new ArrayList<>();
        for (Object o : items) {
            if (!(o instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) o;
            double speed = toDouble(m.get("speed"));
            if (Double.isNaN(speed)) continue;
            links.add(new LinkSpeed(str(m.get("roadName")), str(m.get("linkId")), speed,
                    str(m.get("createdDate")), str(m.get("roadDrcType"))));
        }
        return new Result(links, null);
    }

    private static Result fail(String why) {
        return new Result(Collections.<LinkSpeed>emptyList(), why);
    }

    private static String findServerError(Object root) {
        Map<?, ?> header = child(root, "header");
        if (header == null) header = child(child(root, "response"), "header");
        if (header == null) return null;
        Object code = header.get("resultCode");
        if (code == null) return null;
        String c = str(code);
        if (c.equals("0") || c.equals("0.0") || c.equals("00") || c.equalsIgnoreCase("success")) return null;
        Object msg = header.get("resultMsg");
        return "서버 오류 " + c + (msg != null ? " (" + str(msg) + ")" : "");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> findItems(Object root) {
        Object[] candidates = {
                path(root, "body", "items"),
                path(root, "body", "items", "item"),
                path(root, "response", "body", "items"),
                path(root, "response", "body", "items", "item"),
                path(root, "items"),
        };
        for (Object c : candidates) {
            if (c instanceof List) return (List<Object>) c;
            if (c instanceof Map && ((Map<?, ?>) c).containsKey("speed")) {
                List<Object> one = new ArrayList<>();
                one.add(c);
                return one;
            }
        }
        if (root instanceof List) return (List<Object>) root;
        return null;
    }

    private static Object path(Object root, String... keys) {
        Object cur = root;
        for (String k : keys) {
            if (!(cur instanceof Map)) return null;
            cur = ((Map<?, ?>) cur).get(k);
        }
        return cur;
    }

    private static Map<?, ?> child(Object o, String key) {
        if (!(o instanceof Map)) return null;
        Object c = ((Map<?, ?>) o).get(key);
        return c instanceof Map ? (Map<?, ?>) c : null;
    }

    static double toDouble(Object o) {
        if (o instanceof Double) return (Double) o;
        if (o instanceof String) {
            try {
                return Double.parseDouble(((String) o).trim());
            } catch (NumberFormatException e) {
                return Double.NaN;
            }
        }
        return Double.NaN;
    }

    private static String str(Object o) {
        if (o == null) return "";
        if (o instanceof Double) {
            double d = (Double) o;
            if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d);
            return String.valueOf(d);
        }
        return String.valueOf(o);
    }
}
