package com.giseo.flowcoach.data.route;

import com.giseo.flowcoach.data.demo.DemoScene;
import com.giseo.flowcoach.data.traffic.FlowEstimator;
import com.giseo.flowcoach.data.traffic.ItsTrafficClient;
import com.giseo.flowcoach.data.traffic.ItsTrafficParser;
import com.giseo.flowcoach.domain.CoachConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 실제 도로 시연을 시작하기 전에, 경로의 지금 실제 교통이 시나리오에 맞는지 확인한다.
 *
 * 경로 길이에 따라 1~3곳(출발·중간·도착)을 조회한다(곳마다 API 1회).
 * 그중 가장 느린 흐름으로 각 상황이 실제로 안내될 수 있는지 따진다. 예)
 *  - 흐름 40km/h 미만 = 정체: 과속 말고는 안내가 나오지 않는다
 *  - 추월 후 미복귀: 흐름 80km/h 미만이면 안내하지 않는다(도로교통법)
 * 맞지 않으면 화면에서 '실제 교통대로' / '시나리오 흐름으로' 중 고르게 한다.
 */
public final class RouteTrafficCheck {

    /** 조회 범위(위·경도 ±). 고속도로 노선 조회와 같은 크기. */
    static final double BOX_DEG = 0.02;

    /** 조회한 곳 하나. */
    public static final class Point {
        public final String label;
        /** 내 방향 흐름 속도. 못 받았으면 NaN. */
        public final double flowKmh;
        public final String error;

        public Point(String label, double flowKmh, String error) {
            this.label = label;
            this.flowKmh = flowKmh;
            this.error = error;
        }

        public boolean ok() {
            return !Double.isNaN(flowKmh);
        }
    }

    public static final class Result {
        public final List<Point> points;
        /** 받은 곳 중 가장 느린 흐름. 하나도 못 받았으면 NaN. */
        public final double minFlowKmh;
        /** 상황별 문제(맞으면 비어 있음). */
        public final List<String> problems;
        public final int apiCalls;

        Result(List<Point> points, double minFlowKmh, List<String> problems, int apiCalls) {
            this.points = Collections.unmodifiableList(points);
            this.minFlowKmh = minFlowKmh;
            this.problems = Collections.unmodifiableList(problems);
            this.apiCalls = apiCalls;
        }

        public boolean gotData() {
            return !Double.isNaN(minFlowKmh);
        }

        /** 실제 교통으로 시나리오가 그대로 보이는지. */
        public boolean suits() {
            return gotData() && problems.isEmpty();
        }

        /** "출발 87 · 중간 85 · 도착 62km/h" */
        public String summary() {
            StringBuilder sb = new StringBuilder();
            for (Point p : points) {
                if (sb.length() > 0) sb.append(" · ");
                sb.append(p.label).append(' ').append(p.ok() ? String.valueOf(Math.round(p.flowKmh)) : "-");
            }
            return sb.append("km/h").toString();
        }

        /** 못 받은 이유(첫 번째). */
        public String firstError() {
            for (Point p : points) if (p.error != null) return p.error;
            return "";
        }
    }

    public interface Fetcher {
        String fetch(String url) throws Exception;
    }

    private RouteTrafficCheck() {
    }

    /**
     * 조회할 곳: 상황을 두는 가장 긴 구간(파일 경로는 경로 전체)에서
     * 3km 미만이면 중간 1곳, 8km 미만이면 출발·도착 2곳, 그 이상이면 3곳. {위도, 경도}
     */
    static List<double[]> places(RouteScenario r, List<String> labels) {
        RoadSpan span = r.checkSpan();
        double len = span.lengthM();
        double[] at;
        if (len < 3_000) at = new double[]{0.5};
        else if (len < 8_000) at = new double[]{0.15, 0.85};
        else at = new double[]{0.1, 0.5, 0.9};
        String[] names = at.length == 1 ? new String[]{"구간"}
                : at.length == 2 ? new String[]{"출발", "도착"} : new String[]{"출발", "중간", "도착"};
        List<double[]> out = new ArrayList<>();
        for (int i = 0; i < at.length; i++) {
            out.add(r.line.at(span.startM + len * at[i]));
            labels.add(names[i]);
        }
        return out;
    }

    /** 실제로 조회한다(네트워크, 백그라운드 스레드에서). */
    public static Result run(RouteScenario r, ItsTrafficClient client, Fetcher fetcher, CoachConfig c) {
        List<String> labels = new ArrayList<>();
        List<double[]> places = places(r, labels);
        List<Point> points = new ArrayList<>();
        int calls = 0;
        for (int i = 0; i < places.size(); i++) {
            double[] p = places.get(i);
            String url = r.its != null
                    ? client.buildUrl(ItsTrafficClient.TYPE_EXPRESSWAY, r.its.routeNo,
                    p[1] - BOX_DEG, p[1] + BOX_DEG, p[0] - BOX_DEG, p[0] + BOX_DEG)
                    : client.buildUrl(ItsTrafficClient.TYPE_ALL,
                    p[1] - BOX_DEG / 4, p[1] + BOX_DEG / 4, p[0] - BOX_DEG / 4, p[0] + BOX_DEG / 4);
            try {
                calls++;
                ItsTrafficParser.Result res = ItsTrafficParser.parse(fetcher.fetch(url));
                if (!res.ok()) {
                    points.add(new Point(labels.get(i), Double.NaN, res.error));
                    continue;
                }
                FlowEstimator.Estimate est = r.its != null
                        ? FlowEstimator.estimateStrict(res.links, r.its.keyword, r.its.direction)
                        : FlowEstimator.estimateStrict(res.links, null);
                points.add(est != null ? new Point(labels.get(i), est.speedKmh, null)
                        : new Point(labels.get(i), Double.NaN, "구간 속도 없음"));
            } catch (Exception e) {
                points.add(new Point(labels.get(i), Double.NaN, e.getMessage()));
            }
        }
        return evaluate(r, points, c, calls);
    }

    /** 받은 흐름으로 상황별 문제를 찾는다. 테스트를 위해 분리. */
    static Result evaluate(RouteScenario r, List<Point> points, CoachConfig c, int calls) {
        double min = Double.NaN;
        for (Point p : points) if (p.ok() && (Double.isNaN(min) || p.flowKmh < min)) min = p.flowKmh;
        List<String> problems = new ArrayList<>();
        if (!Double.isNaN(min)) {
            double rec = Math.min(min, r.limitKmh);
            String flow = String.format(Locale.KOREA, "%.0fkm/h", min);
            boolean jam = min < c.congestionFlowKmh;
            for (DemoScene s : r.situations) {
                String why = null;
                if (jam && s != DemoScene.OVER_LIMIT) {
                    why = "정체(흐름 " + flow + ")라 안내가 나오지 않아요";
                } else if (s == DemoScene.PASSING && min < c.passingRuleMinFlowKmh) {
                    why = String.format(Locale.KOREA, "흐름 %s — %.0fkm/h보다 느리면 법상 안내하지 않아요",
                            flow, c.passingRuleMinFlowKmh);
                } else if (s == DemoScene.TOO_SLOW
                        && Math.max(30, rec - c.slowMarginKmh - RouteDriver.SLOW_BELOW_KMH) >= rec - c.slowMarginKmh) {
                    why = "흐름(" + flow + ")이 느려서 ‘더 느린’ 주행을 만들기 어려워요";
                } else if (s == DemoScene.HARD_BRAKE && rec - 2 < c.hardBrakeMinSpeedKmh + 4) {
                    why = "흐름(" + flow + ")이 느려서 급제동으로 판단되지 않아요";
                } else if (s == DemoScene.LANE_CHANGES && rec - 2 < c.laneChangeMinSpeedKmh) {
                    why = "흐름(" + flow + ")이 느려서 차로 변경으로 판단되지 않아요";
                }
                if (why != null) problems.add(s.title + ": " + why);
            }
        }
        return new Result(points, min, problems, calls);
    }
}
