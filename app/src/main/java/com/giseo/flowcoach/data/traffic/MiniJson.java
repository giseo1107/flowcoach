package com.giseo.flowcoach.data.traffic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 의존성 없는 작은 JSON 파서.
 * Android의 org.json은 일반 JVM 단위 테스트에서 동작하지 않아서(스텁),
 * 교통 API 응답 파싱을 테스트할 수 있도록 직접 구현했다.
 *
 * 결과 타입: Map(객체) / List(배열) / String / Double / Boolean / null
 */
public final class MiniJson {

    private final String s;
    private int i;

    private MiniJson(String s) {
        this.s = s;
    }

    public static Object parse(String json) {
        MiniJson p = new MiniJson(json);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        if (p.i != p.s.length()) throw p.error("끝에 남은 문자");
        return v;
    }

    private Object value() {
        if (i >= s.length()) throw error("예상치 못한 끝");
        char c = s.charAt(i);
        switch (c) {
            case '{': return object();
            case '[': return array();
            case '"': return string();
            case 't': expect("true"); return Boolean.TRUE;
            case 'f': expect("false"); return Boolean.FALSE;
            case 'n': expect("null"); return null;
            default: return number();
        }
    }

    private Map<String, Object> object() {
        Map<String, Object> map = new LinkedHashMap<>();
        i++; // {
        skipWs();
        if (peek() == '}') { i++; return map; }
        while (true) {
            skipWs();
            String key = string();
            skipWs();
            if (peek() != ':') throw error("':' 필요");
            i++;
            skipWs();
            map.put(key, value());
            skipWs();
            char c = peek();
            i++;
            if (c == '}') return map;
            if (c != ',') throw error("',' 또는 '}' 필요");
        }
    }

    private List<Object> array() {
        List<Object> list = new ArrayList<>();
        i++; // [
        skipWs();
        if (peek() == ']') { i++; return list; }
        while (true) {
            skipWs();
            list.add(value());
            skipWs();
            char c = peek();
            i++;
            if (c == ']') return list;
            if (c != ',') throw error("',' 또는 ']' 필요");
        }
    }

    private String string() {
        if (peek() != '"') throw error("문자열 필요");
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: sb.append(e); // " \ /
                }
            } else {
                sb.append(c);
            }
        }
        throw error("닫히지 않은 문자열");
    }

    private Double number() {
        int start = i;
        while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
        if (start == i) throw error("값 필요");
        return Double.parseDouble(s.substring(start, i));
    }

    private void expect(String word) {
        if (!s.startsWith(word, i)) throw error("'" + word + "' 필요");
        i += word.length();
    }

    private char peek() {
        return i < s.length() ? s.charAt(i) : '\0';
    }

    private void skipWs() {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    }

    private IllegalArgumentException error(String msg) {
        return new IllegalArgumentException("JSON 오류(" + i + "): " + msg);
    }
}
