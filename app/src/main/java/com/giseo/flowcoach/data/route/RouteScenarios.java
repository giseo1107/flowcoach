package com.giseo.flowcoach.data.route;

import android.content.Context;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 앱에 들어 있는 실제 도로 시연 목록(assets/routes/*.json). */
public final class RouteScenarios {

    private static final String TAG = "RouteScenarios";
    private static final String DIR = "routes";

    /** 이번 실행에서 만든 '내 경로'(검색으로 만든 경로, 앱을 다시 켜면 사라진다). */
    private static volatile RouteScenario custom;

    private RouteScenarios() {
    }

    public static void setCustom(RouteScenario r) {
        custom = r;
    }

    public static RouteScenario custom() {
        return custom;
    }

    /** 시나리오의 order 순서로 읽는다. 읽지 못한 파일은 건너뛴다. */
    public static List<RouteScenario> all(Context context) {
        List<RouteScenario> list = new ArrayList<>();
        try {
            String[] names = context.getAssets().list(DIR);
            if (names == null) return list;
            Arrays.sort(names);
            for (String name : names) {
                if (!name.endsWith(".json")) continue;
                RouteScenario r = load(context, name);
                if (r != null) list.add(r);
            }
            list.sort((a, b) -> Integer.compare(a.order, b.order));
        } catch (Exception e) {
            Log.w(TAG, "경로 목록 읽기 실패", e);
        }
        return list;
    }

    /** id(= 파일 이름에서 .json을 뺀 것)로 찾는다. */
    public static RouteScenario byId(Context context, String id) {
        if (OsrmRouter.CUSTOM_ID.equals(id)) return custom;
        return load(context, id + ".json");
    }

    private static RouteScenario load(Context context, String fileName) {
        try (InputStream in = context.getAssets().open(DIR + "/" + fileName)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return RouteScenario.parse(new String(out.toByteArray(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            Log.w(TAG, "경로 파일 읽기 실패: " + fileName, e);
            return null;
        }
    }
}
