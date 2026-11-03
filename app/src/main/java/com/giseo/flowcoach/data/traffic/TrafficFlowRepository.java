package com.giseo.flowcoach.data.traffic;

import com.giseo.flowcoach.data.location.GeoMath;
import com.giseo.flowcoach.data.location.GeoPosition;
import com.giseo.flowcoach.domain.RoadContext;
import com.giseo.flowcoach.domain.RoadType;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 실시간 교통 흐름 저장소.
 *
 * 두 가지 방식으로 조회한다.
 *  - 위치 기반(Query.located): 현재 위치 주변 약 400m의 도로 구간(type=all)으로 '지금 달리는 도로'를 고르고(RoadMatcher),
 *    고속도로면 노선별 조회(type=ex&routeNo)에서 진행 방향(상행/하행) 구간만 쓴다.
 *  - 고정(Query.fixed): 위치를 모를 때 시연용으로 정한 도로·방향(경부고속도로 상행, 판교 부근)을 조회한다.
 *
 * API 월 호출 한도(운영 1,000건)가 작아서 호출을 아낀다.
 *  - 1초마다 maybeRefresh()를 불러도 실제 호출은 필요할 때만:
 *    처음 / 2km 넘게 이동 / 고른 도로·방향이 바뀜 / 10분 경과. 어떤 경우에도 1분에 한 번 이하.
 *  - ITS 데이터 자체가 약 5분 단위로 갱신되므로 실시간성 손해는 거의 없다.
 *  - 앱 전체에서 하나만 두고(CoachRuntime), 주행을 다시 시작해도 최근 결과를 재사용한다.
 *  - 실패하면 기존 값(또는 호출부의 기본값)을 그대로 쓴다.
 */
public final class TrafficFlowRepository {

    /** 도로 찾기 범위(위경도 ±). 약 400m. */
    static final double DETECT_DEG = 0.004;
    /** 400m 안에 아무 도로도 없을 때 한 번 더 넓혀 보는 범위. 약 1km. */
    static final double DETECT_WIDE_DEG = 0.01;
    /** 고속도로 노선별 조회 범위(위경도 ±). 약 2km. */
    static final double ROUTE_DEG = 0.02;
    private static final long DEFAULT_REFRESH_MS = 10 * 60_000;
    /** 호출 사이 최소 간격. */
    static final long MIN_GAP_MS = 60_000;
    /** 이만큼 움직이면 주변 도로를 다시 찾는다(m). */
    static final double REDETECT_MOVE_M = 2_000;
    /** 이보다 오래된 정보는 쓰지 않는다. */
    private static final long STALE_MS = 25 * 60_000;

    /** 무엇을 조회할지. */
    public static final class Query {
        /** 위치 기반이면 현재 위치, 고정이면 조회 중심. */
        public final GeoPosition position;
        public final double vehicleKmh;
        /** 내비가 알려준 현재 도로 제한속도(모르면 0). */
        public final int limitKmh;
        /** 고정 조회일 때만: 노선번호·도로 이름·방향. */
        public final String fixedRouteNo;
        public final String fixedRoadHint;
        public final String fixedDirection;
        /** 위치 기반일 때: 달리는 도로를 미리 알면 그 이름(실제 도로 시연의 지금 구간). 모르면 null. */
        public final String roadHint;
        /** 위치 기반일 때: false면 주변 고속도로는 고르지 않는다(시내 구간). */
        public final boolean allowExpressway;

        private Query(GeoPosition position, double vehicleKmh, int limitKmh,
                      String fixedRouteNo, String fixedRoadHint, String fixedDirection,
                      String roadHint, boolean allowExpressway) {
            this.position = position;
            this.vehicleKmh = vehicleKmh;
            this.limitKmh = limitKmh;
            this.fixedRouteNo = fixedRouteNo;
            this.fixedRoadHint = fixedRoadHint;
            this.fixedDirection = fixedDirection;
            this.roadHint = roadHint;
            this.allowExpressway = allowExpressway;
        }

        /** 현재 위치로 도로를 찾아 조회한다. */
        public static Query located(GeoPosition position, double vehicleKmh, int limitKmh) {
            return new Query(position, vehicleKmh, limitKmh, null, null, null, null, true);
        }

        /**
         * 경로 위를 달릴 때: 현재 위치로 찾되, 지금 구간의 도로 이름을 먼저 고르고
         * 고속도로 구간이 아니면 옆을 지나는 고속도로는 고르지 않는다.
         */
        public static Query onRoute(GeoPosition position, double vehicleKmh, int limitKmh,
                                    String roadName, boolean expresswaySpan) {
            return new Query(position, vehicleKmh, limitKmh, null, null, null, roadName, expresswaySpan);
        }

        /** 정해진 고속도로·방향을 조회한다(시연용). */
        public static Query fixed(double lat, double lon, String routeNo, String roadHint, String direction) {
            return new Query(new GeoPosition(lat, lon, Double.NaN, "시연", 0), Double.NaN, 0,
                    routeNo, roadHint, direction, null, true);
        }

        public boolean isFixed() {
            return fixedRouteNo != null;
        }

        String fixedKey() {
            return "fixed|" + fixedRouteNo + "|" + fixedDirection;
        }
    }

    /** 주변 도로 찾기 결과(교체만 하고 고치지 않는다). */
    private static final class Detection {
        final double lat;
        final double lon;
        final List<LinkSpeed> links;

        Detection(double lat, double lon, List<LinkSpeed> links) {
            this.lat = lat;
            this.lon = lon;
            this.links = links;
        }
    }

    /** URL을 받아 응답 본문을 돌려준다(테스트에서 가짜 응답으로 바꾼다). */
    interface Fetcher {
        String fetch(String url) throws IOException;
    }

    private final ItsTrafficClient client; // 키가 없으면 null
    private final Fetcher fetcher;
    private final long refreshMs;
    private final Executor executor;

    private volatile RoadContext road;
    private volatile long fetchedAtMs;
    private volatile String status;
    private volatile boolean inFlight;
    private volatile int callCount;
    /** 마지막으로 흐름을 받은 도로 이름(예: "경부고속도로 상행"). */
    private volatile String roadLabel;

    // 아래는 maybeRefresh(메인 스레드)와 fetch(백그라운드)가 함께 쓰므로 synchronized로 다룬다.
    private long lastRequestMs = Long.MIN_VALUE / 4;
    private Detection detection;
    private String currentRoadName;
    private String currentDirection;
    /** 마지막으로 흐름을 받은 도로·방향(같으면 10분마다만 다시 받는다). */
    private String flowKey;

    public TrafficFlowRepository(String apiKey) {
        this(apiKey, DEFAULT_REFRESH_MS);
    }

    public TrafficFlowRepository(String apiKey, long refreshMs) {
        this(apiKey, refreshMs, null, null);
    }

    /** fetcher/executor가 null이면 실제 네트워크·백그라운드 스레드를 쓴다. */
    TrafficFlowRepository(String apiKey, long refreshMs, Fetcher fetcher, Executor executor) {
        boolean hasKey = apiKey != null && !apiKey.trim().isEmpty();
        this.client = hasKey ? new ItsTrafficClient(apiKey.trim()) : null;
        this.fetcher = fetcher != null ? fetcher : (client != null ? client::fetch : null);
        this.refreshMs = refreshMs;
        this.status = hasKey ? "실시간 교통 조회 대기" : "교통 API 키 없음(가정값 사용)";
        if (executor != null) {
            this.executor = executor;
        } else {
            this.executor = hasKey ? Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "traffic-api");
                t.setDaemon(true);
                return t;
            }) : null;
        }
    }

    public boolean isEnabled() {
        return client != null;
    }

    /** 필요할 때만 백그라운드로 조회한다. 메인 스레드에서 1초마다 불러도 된다. */
    public synchronized void maybeRefresh(Query q, long nowMs) {
        if (client == null || inFlight || nowMs - lastRequestMs < MIN_GAP_MS) return;

        final boolean detect;
        if (q.isFixed()) {
            detect = false;
            if (q.fixedKey().equals(flowKey) && nowMs - fetchedAtMs < refreshMs) return;
        } else {
            GeoPosition p = q.position;
            detect = detection == null
                    || GeoMath.distanceMeters(detection.lat, detection.lon, p.lat, p.lon) > REDETECT_MOVE_M;
            if (!detect) {
                // 이미 받아 둔 주변 도로 목록으로 도로·방향을 다시 고른다(호출 없음)
                String key = choose(detection.links, q);
                if (key == null) return; // 주변에 쓸 만한 도로가 없었다 → 2km 이동 전까지 다시 부르지 않음
                if (key.equals(flowKey) && nowMs - fetchedAtMs < refreshMs) return;
            }
        }
        lastRequestMs = nowMs;
        inFlight = true;
        executor.execute(() -> {
            try {
                if (q.isFixed()) fetchFixed(q, nowMs);
                else fetchLocated(q, detect, nowMs);
            } catch (Exception e) {
                status = "교통정보 오류: " + e.getMessage();
            } finally {
                inFlight = false;
            }
        });
    }

    /**
     * 주변 링크에서 도로와 방향을 고르고 currentRoadName/currentDirection에 둔다.
     * 방향은 잠깐 판단이 안 될 때(정지, 커브) 이전 값을 유지한다.
     * @return "도로|방향" 키, 도로가 없으면 null
     */
    private synchronized String choose(List<LinkSpeed> links, Query q) {
        RoadMatcher.Candidate c = RoadMatcher.pick(links, currentRoadName, q.vehicleKmh, q.limitKmh,
                q.roadHint, q.allowExpressway);
        if (c == null) return null;
        String dir = ExpresswayRoutes.direction(c.route, q.position.lat, q.position.lon, q.position.headingDeg);
        if (dir == null && c.roadName.equals(currentRoadName)) dir = currentDirection;
        currentRoadName = c.roadName;
        currentDirection = dir;
        return c.roadName + "|" + dir;
    }

    private void fetchFixed(Query q, long requestedAtMs) throws Exception {
        GeoPosition p = q.position;
        List<LinkSpeed> links = call(ItsTrafficClient.TYPE_EXPRESSWAY, q.fixedRouteNo, p, ROUTE_DEG);
        if (links == null) return;
        FlowEstimator.Estimate est = FlowEstimator.estimateStrict(links, q.fixedRoadHint, q.fixedDirection);
        if (est == null) {
            status = "주변에 '" + q.fixedRoadHint + " " + q.fixedDirection + "' 구간 없음(" + links.size() + "개 수신)";
            return;
        }
        publish(RoadType.EXPRESSWAY, est, label(est.roadName, q.fixedDirection, false), q.fixedKey(), requestedAtMs);
    }

    private void fetchLocated(Query q, boolean detect, long requestedAtMs) throws Exception {
        GeoPosition p = q.position;
        List<LinkSpeed> nearby = null;
        if (detect) {
            nearby = call(ItsTrafficClient.TYPE_ALL, null, p, DETECT_DEG);
            if (nearby == null) return; // 오류(상태에 기록됨). 1분 뒤 다시 시도
            if (RoadMatcher.candidates(nearby).isEmpty()) {
                // 구간(링크)이 길어서 좁은 범위에 안 걸렸을 수 있다 → 한 번만 넓혀 본다
                List<LinkSpeed> wide = call(ItsTrafficClient.TYPE_ALL, null, p, DETECT_WIDE_DEG);
                if (wide != null) nearby = wide;
            }
            synchronized (this) {
                detection = new Detection(p.lat, p.lon, nearby);
            }
        }
        List<LinkSpeed> pool;
        synchronized (this) {
            pool = detection != null ? detection.links : Collections.<LinkSpeed>emptyList();
        }
        String key = choose(pool, q);
        if (key == null) {
            status = String.format(Locale.KOREA, "주변 1km에 교통정보가 있는 도로 없음(%s)", p);
            return;
        }
        String roadName;
        String dir;
        synchronized (this) {
            roadName = currentRoadName;
            dir = currentDirection;
        }
        RoadMatcher.Candidate c = RoadMatcher.pick(pool, roadName, q.vehicleKmh, q.limitKmh,
                q.roadHint, q.allowExpressway);

        // 고속도로 + 방향을 알면: 노선별 조회로 내 방향 구간만
        if (c != null && c.route != null && dir != null) {
            List<LinkSpeed> ex = call(ItsTrafficClient.TYPE_EXPRESSWAY, c.route.routeNo(), p, ROUTE_DEG);
            FlowEstimator.Estimate est = ex == null ? null : FlowEstimator.estimateStrict(ex, c.route.keyword, dir);
            if (est != null) {
                publish(RoadType.EXPRESSWAY, est, label(est.roadName, dir, false), key, requestedAtMs);
                return;
            }
        }
        // 그 밖: 주변 구간 중 그 도로만(방향 섞임). 방금 찾은 게 아니면 새로 받는다
        if (!detect) {
            nearby = call(ItsTrafficClient.TYPE_ALL, null, p, DETECT_DEG);
            if (nearby == null) return;
            synchronized (this) {
                detection = new Detection(p.lat, p.lon, nearby);
            }
            pool = nearby;
        }
        FlowEstimator.Estimate est = FlowEstimator.estimateStrict(pool, roadName, null);
        if (est == null) {
            status = "'" + roadName + "' 구간 속도 없음";
            return;
        }
        RoadType type = c != null && c.expressway ? RoadType.EXPRESSWAY : guessRoadType(est.roadName, null);
        publish(type, est, label(est.roadName, null, true), key, requestedAtMs);
    }

    /** API를 한 번 부른다. 오류면 상태에 남기고 null. */
    private List<LinkSpeed> call(String type, String routeNo, GeoPosition p, double deg) throws Exception {
        String url = client.buildUrl(type, routeNo, p.lon - deg, p.lon + deg, p.lat - deg, p.lat + deg);
        callCount++;
        ItsTrafficParser.Result result = ItsTrafficParser.parse(fetcher.fetch(url));
        if (!result.ok()) {
            status = "교통정보 오류: " + result.error;
            return null;
        }
        return result.links;
    }

    private void publish(RoadType type, FlowEstimator.Estimate est, String label, String key, long atMs) {
        road = new RoadContext(type, type.defaultLimitKmh, est.speedKmh);
        roadLabel = label;
        synchronized (this) {
            fetchedAtMs = atMs;
            flowKey = key;
        }
        status = String.format(Locale.KOREA, "실시간 교통: %s %.0fkm/h (%d개 구간) · API %d회",
                label, est.speedKmh, est.linkCount, callCount);
    }

    private static String label(String roadName, String dir, boolean mixed) {
        String name = roadName == null || roadName.isEmpty() ? "주변 도로" : roadName;
        if (dir != null) return name + " " + dir;
        return mixed ? name + "(양방향)" : name;
    }

    /** 도로 이름으로 도로 종류를 추정한다(type=all 응답에는 도로 종류 필드가 없다). */
    static RoadType guessRoadType(String roadName, String hint) {
        String n = roadName == null ? "" : roadName;
        if (n.contains("고속") || ExpresswayRoutes.find(n) != null
                || (hint != null && !hint.isEmpty() && n.contains(hint) && n.endsWith("선"))) {
            return RoadType.EXPRESSWAY;
        }
        for (String m : URBAN_MOTORWAYS) {
            if (n.contains(m)) return RoadType.MOTORWAY;
        }
        if (n.contains("국도")) return RoadType.NATIONAL;
        return RoadType.UNKNOWN;
    }

    /** 신호 없는 도시고속도로·자동차전용도로(일부). */
    private static final String[] URBAN_MOTORWAYS = {
            "올림픽대로", "강변북로", "내부순환", "동부간선", "서부간선", "북부간선", "분당수서", "분당내곡", "자동차전용"
    };

    /** 최근 정보가 있으면 그것을, 없거나 오래됐으면 fallback을 돌려준다. */
    public RoadContext roadOr(RoadContext fallback, long nowMs) {
        RoadContext r = road;
        return (r != null && nowMs - fetchedAtMs < STALE_MS) ? r : fallback;
    }

    public String status() {
        return status;
    }

    /** 지금 흐름 속도를 가져온 도로(화면·알림용). 아직 없으면 null. */
    public String roadLabel() {
        return roadLabel;
    }

    /** 이번 실행에서 부른 API 횟수(월 한도 확인용). */
    public int callCount() {
        return callCount;
    }

    /** 지금 고른 도로·방향(화면·테스트용). 없으면 null. */
    public synchronized String currentRoad() {
        if (currentRoadName == null) return null;
        return currentDirection != null ? currentRoadName + " " + currentDirection : currentRoadName;
    }

    public void shutdown() {
        if (executor instanceof ExecutorService) ((ExecutorService) executor).shutdownNow();
    }
}
