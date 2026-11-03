package com.giseo.flowcoach.data.route;

import com.giseo.flowcoach.data.traffic.MiniJson;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 카카오 로컬 API 키워드 장소 검색(목적지·출발지 찾기).
 * GET https://dapi.kakao.com/v2/local/search/keyword.json?query=판교역 · 헤더 Authorization: KakaoAK {REST API 키}
 * 카카오 개발자 콘솔에서 [카카오맵] 사용 설정을 켜야 응답한다.
 */
public final class KakaoPlaceSearch {

    public static final String ENDPOINT = "https://dapi.kakao.com/v2/local/search/keyword.json";
    private static final int SIZE = 8;

    /** 검색 결과 한 곳. */
    public static final class Place {
        public final String name;
        public final String address;
        public final double lat;
        public final double lon;
        public final String category;
        /** 기준 위치에서의 거리(m). 모르면 -1. */
        public final int distanceM;

        public Place(String name, String address, double lat, double lon, String category, int distanceM) {
            this.name = name;
            this.address = address;
            this.lat = lat;
            this.lon = lon;
            this.category = category;
            this.distanceM = distanceM;
        }

        /** "판교역 신분당선 · 경기 성남시 분당구 … · 1.2km" */
        public String label() {
            String d = distanceM < 0 ? "" : distanceM < 1000 ? " · " + distanceM + "m"
                    : String.format(Locale.KOREA, " · %.1fkm", distanceM / 1000.0);
            return name + (address.isEmpty() ? "" : " · " + address) + d;
        }
    }

    private final String restKey;

    public KakaoPlaceSearch(String restKey) {
        this.restKey = restKey;
    }

    public boolean isEnabled() {
        return restKey != null && !restKey.isEmpty();
    }

    /** 장소 검색(네트워크 스레드에서). near가 있으면 그 근처일수록 앞에 온다. */
    public List<Place> search(String query, double nearLat, double nearLon) throws Exception {
        return parse(Http.get(buildUrl(query, nearLat, nearLon), "KakaoAK " + restKey));
    }

    static String buildUrl(String query, double nearLat, double nearLon) throws Exception {
        String url = ENDPOINT + "?size=" + SIZE + "&query=" + URLEncoder.encode(query, "UTF-8");
        if (!Double.isNaN(nearLat) && !Double.isNaN(nearLon)) {
            url += String.format(Locale.ROOT, "&x=%.6f&y=%.6f", nearLon, nearLat);
        }
        return url;
    }

    @SuppressWarnings("unchecked")
    static List<Place> parse(String json) {
        Object root = MiniJson.parse(json);
        if (!(root instanceof Map)) return Collections.emptyList();
        Object docs = ((Map<String, Object>) root).get("documents");
        if (!(docs instanceof List)) return Collections.emptyList();
        List<Place> out = new ArrayList<>();
        for (Object o : (List<Object>) docs) {
            Map<String, Object> d = (Map<String, Object>) o;
            try {
                String road = str(d.get("road_address_name"));
                String address = road.isEmpty() ? str(d.get("address_name")) : road;
                String dist = str(d.get("distance"));
                out.add(new Place(str(d.get("place_name")), address,
                        Double.parseDouble(str(d.get("y"))), Double.parseDouble(str(d.get("x"))),
                        str(d.get("category_name")), dist.isEmpty() ? -1 : (int) Double.parseDouble(dist)));
            } catch (RuntimeException ignored) {
                // 좌표가 없는 항목은 건너뛴다
            }
        }
        return out;
    }

    private static String str(Object v) {
        return v == null ? "" : v.toString();
    }
}
