package com.giseo.flowcoach.data.route;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** 경로·장소 조회용 작은 HTTP GET(네트워크 스레드에서 호출). */
final class Http {

    private Http() {
    }

    /** @param authorization 없으면 null(예: "KakaoAK 키") */
    static String get(String url, String authorization) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "FlowCoach/1.0 (Pleos Connect contest prototype)");
        if (authorization != null) conn.setRequestProperty("Authorization", authorization);
        try {
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = in == null ? "" : readAll(in);
            if (code >= 400) {
                String one = body.replaceAll("\\s+", " ").trim();
                throw new IOException("HTTP " + code + (one.isEmpty() ? "" : ": " + (one.length() > 120 ? one.substring(0, 120) + "…" : one)));
            }
            return body;
        } finally {
            conn.disconnect();
        }
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
}
