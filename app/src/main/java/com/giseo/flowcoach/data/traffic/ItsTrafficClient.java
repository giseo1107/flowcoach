package com.giseo.flowcoach.data.traffic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * 국가교통정보센터(ITS) 오픈API — 교통소통정보(trafficInfo) 호출.
 * 좌표 범위(minX~maxX 경도, minY~maxY 위도) 안의 도로 링크별 평균 통행속도를 받는다.
 * 인증키: its.go.kr 회원가입 → 마이페이지 → 인증키 신청.
 *
 * 실제 호출로 확인한 규칙(tools/its_probe.sh, 2026-10-29):
 *  - drcType은 필수(빠지면 resultCode 4002 "필수 입력값 누락")
 *  - type=ex(고속도로)는 routeNo(노선번호)가 필수이고, routeNo=all은 결과 0건
 *  - type=all은 routeNo 없이 범위 안의 모든 도로(고속도로·국도·시내도로)를 돌려주지만
 *    방향 정보(roadDrcType)가 비어 있고 진출입로까지 섞인다. drcType=up/down도 효과 없음
 *  - type=ex&routeNo=0010(경부고속도로)은 본선만, roadDrcType에 "상행"/"하행"이 채워져 온다
 *    (type=ex에서 drcType=up은 오류 → 방향은 응답을 받아 앱에서 나눈다)
 */
public final class ItsTrafficClient {

    public static final String ENDPOINT = "https://openapi.its.go.kr:9443/trafficInfo";
    /** 전체 도로(routeNo 없이 좌표 범위로 조회 가능) */
    public static final String TYPE_ALL = "all";
    /** 고속도로(routeNo 필수) */
    public static final String TYPE_EXPRESSWAY = "ex";
    /** 국도 */
    public static final String TYPE_NATIONAL = "its";

    private final String apiKey;

    public ItsTrafficClient(String apiKey) {
        this.apiKey = apiKey;
    }

    /** 고속도로 노선번호(4자리). 확인된 값만 둔다. */
    public static final String ROUTE_GYEONGBU = "0010";

    public String buildUrl(String type, double minLon, double maxLon, double minLat, double maxLat) {
        return buildUrl(type, null, minLon, maxLon, minLat, maxLat);
    }

    /** routeNo는 type=ex일 때 필수(예: "0010" 경부고속도로). 필요 없으면 null. */
    public String buildUrl(String type, String routeNo, double minLon, double maxLon, double minLat, double maxLat) {
        try {
            return ENDPOINT
                    + "?apiKey=" + URLEncoder.encode(apiKey, "UTF-8")
                    + "&type=" + type
                    + (routeNo != null && !routeNo.isEmpty() ? "&routeNo=" + routeNo : "")
                    + "&drcType=all"
                    + "&minX=" + coord(minLon) + "&maxX=" + coord(maxLon)
                    + "&minY=" + coord(minLat) + "&maxY=" + coord(maxLat)
                    + "&getType=json";
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 응답 본문을 문자열로 돌려준다. 네트워크 스레드에서 호출해야 한다. */
    public String fetch(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("Accept", "application/json");
        try {
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = in == null ? "" : readAll(in);
            if (code >= 400) {
                throw new IOException("HTTP " + code + (body.isEmpty() ? "" : ": " + snippet(body)));
            }
            return body;
        } finally {
            conn.disconnect();
        }
    }

    private static String coord(double v) {
        return String.format(Locale.ROOT, "%.6f", v);
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    static String snippet(String body) {
        String one = body.replaceAll("\\s+", " ").trim();
        return one.length() > 120 ? one.substring(0, 120) + "…" : one;
    }
}
