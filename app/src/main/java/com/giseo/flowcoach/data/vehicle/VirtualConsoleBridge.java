package com.giseo.flowcoach.data.vehicle;

import android.os.Build;
import android.util.Log;

import com.giseo.flowcoach.domain.DrivingSnapshot;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 시연 주행 때 에뮬레이터의 가상 차량(왼쪽 계기판)도 장면에 맞게 움직인다.
 *
 * Pleos Connect 에뮬레이터 안에서 도는 Virtual Console 서버(127.0.0.1:8088)의 주소를 쓴다.
 *  - PUT /vehicle/gear?gear=Drive|Park
 *  - PUT /vehicle/controls?speed=96&accelerator=0&brake=0
 *  - PUT /vehicle/turn-signal?mode=Off (시작·끝에 꺼 두기만 한다)
 *
 * 방향지시등은 차에 보내지 않는다: 켜면 Pleos의 후측방 카메라 창(BVM)이 떠서 흐름 코치 화면을 가린다.
 * 시연 장면의 방향지시등은 앱 게이지 안의 ◀ ▶ 로만 보여준다.
 *
 * 세션: 서버는 '연결된 조작 화면'이 하나도 없으면 조작 요청을 503으로 거절한다
 * (Virtual Console 웹 페이지는 1초마다 POST /browser/session 으로 연결을 알리고, 3초 끊기면 끝난 것으로 본다).
 * 그래서 시연 중에는 앱도 같은 방식으로 1초마다 연결을 알리고, 끝나면 closing=1로 닫는다.
 *
 * 안전장치: 에뮬레이터 하드웨어이고, 서버가 Virtual Console 형식으로 응답할 때만 켠다.
 * 실제 차량에는 이 서버가 없으므로 아무것도 보내지 않는다(시연은 앱 화면만).
 * 모든 요청은 한 줄로 세운 백그라운드 스레드에서 순서대로 보낸다.
 */
public final class VirtualConsoleBridge {

    private static final String TAG = "VirtualConsole";
    private static final String BASE = "http://127.0.0.1:8088";

    private static final int UNKNOWN = 0;
    private static final int AVAILABLE = 1;
    private static final int UNAVAILABLE = 2;

    /** 연동이 잘 되고 있을 때의 상태 문구. */
    private static final String OK_STATUS = "에뮬레이터 차도 함께 움직여요";
    /**
     * Virtual Console 웹 페이지가 화면에 열려 있으면, 페이지가 자체 물리 계산(가속 페달 0 → 감속)으로
     * 속도를 계속 덮어쓴다. 보낸 속도와 실제 속도가 이만큼 벌어지면 경고한다.
     */
    private static final int FIGHT_GAP_KMH = 10;
    private static final Pattern SPEED_KPH = Pattern.compile("\"speed_kph\"\\s*:\\s*([0-9.]+)");
    private static final Pattern SESSION_ACTIVE = Pattern.compile("\"browser\"\\s*:\\s*\\{\\s*\"active\"\\s*:\\s*true");
    /** 조작 화면 연결 이름(Virtual Console의 browser session client). */
    private static final String SESSION = "/browser/session?client=flowcoach-demo";

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "virtual-console");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> heartbeat; // executor 스레드에서만 쓴다

    private volatile int state = UNKNOWN;
    private volatile String status = "에뮬레이터 차 연동 확인 전";

    // 아래는 executor 스레드에서만 쓴다
    private int lastSpeed = -1;
    private int lastBrake = -1;
    private int frameCount = 0;
    private int fightCount = 0;
    private volatile boolean overwritten = false;

    /** 에뮬레이터(qemu) 하드웨어인지. 실제 차에서 차량 제어 주소로 요청을 보내지 않기 위한 첫 번째 확인. */
    public static boolean looksLikeEmulator() {
        String hw = lower(Build.HARDWARE);
        String all = lower(Build.FINGERPRINT) + " " + lower(Build.PRODUCT) + " " + lower(Build.MODEL);
        return hw.contains("ranchu") || hw.contains("goldfish")
                || all.contains("emulator") || all.contains("sdk_") || all.contains("_avd") || all.contains("generic");
    }

    /** 시연 시작: 연결을 알리고, 차를 D·정지 상태로. */
    public void begin() {
        executor.execute(() -> {
            if (!ensureAvailable()) return;
            startHeartbeat();
            lastSpeed = -1;
            lastBrake = -1;
            frameCount = 0;
            fightCount = 0;
            overwritten = false;
            status = OK_STATUS;
            put("/vehicle/turn-signal?mode=Off");
            put("/vehicle/controls?speed=0&accelerator=0&brake=0");
            put("/vehicle/gear?gear=Drive");
        });
    }

    /** 시연 한 프레임을 차에 반영한다(속도·브레이크 중 바뀐 값만 보낸다. 방향지시등은 보내지 않는다). */
    public void apply(DrivingSnapshot s) {
        final int speed = (int) Math.round(s.speedKmh);
        final int brake = s.brakePedalPercent > 0 ? s.brakePedalPercent : (s.brakePressed ? 50 : 0);
        executor.execute(() -> {
            if (state != AVAILABLE) return;
            // 3초마다: 1초 전에 보낸 속도가 그대로 있는지 확인(웹 페이지가 덮어쓰는지)
            if (++frameCount % 3 == 0 && lastSpeed > FIGHT_GAP_KMH) checkOverwrite(lastSpeed);
            if (speed != lastSpeed || brake != lastBrake) {
                put(String.format(Locale.ROOT, "/vehicle/controls?speed=%d&accelerator=0&brake=%d", speed, brake));
                lastSpeed = speed;
                lastBrake = brake;
            }
        });
    }

    /** 시연 끝: 차를 세우고 P로 되돌린다. */
    public void end() {
        executor.execute(() -> {
            if (state != AVAILABLE) return;
            put("/vehicle/turn-signal?mode=Off");
            put("/vehicle/controls?speed=0&accelerator=0&brake=0");
            put("/vehicle/gear?gear=Park");
            lastSpeed = -1;
            lastBrake = -1;
            stopHeartbeat();
        });
    }

    /** 1초마다 연결을 알린다. 처음 연결이면 서버가 상태를 준비할 시간을 잠깐 준다. */
    private void startHeartbeat() {
        boolean wasActive = false;
        try {
            wasActive = SESSION_ACTIVE.matcher(request("GET", "/vehicle/state", 800)).find();
        } catch (Exception ignored) {
            // 아래 연결 알림이 실패하면 put()에서 상태로 알린다
        }
        post(SESSION);
        if (heartbeat == null) {
            heartbeat = executor.scheduleWithFixedDelay(() -> post(SESSION), 1, 1, TimeUnit.SECONDS);
        }
        if (!wasActive) {
            try {
                Thread.sleep(700); // 연결이 처음 활성화될 때 서버가 기본 상태를 다시 적용하는 시간
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void stopHeartbeat() {
        if (heartbeat != null) {
            heartbeat.cancel(false);
            heartbeat = null;
        }
        post(SESSION + "&closing=1");
    }

    private void post(String pathAndQuery) {
        try {
            request("POST", pathAndQuery, 1000);
        } catch (Exception e) {
            Log.w(TAG, "POST 실패 " + pathAndQuery, e);
        }
    }

    private void checkOverwrite(int sentKmh) {
        try {
            Matcher m = SPEED_KPH.matcher(request("GET", "/vehicle/state", 800));
            if (!m.find()) return;
            double actual = Double.parseDouble(m.group(1));
            if (actual < sentKmh - FIGHT_GAP_KMH) {
                if (++fightCount >= 2) {
                    overwritten = true;
                    status = "Virtual Console 창이 열려 있으면 차 속도가 덮어써져요. 시연 중엔 그 창을 닫아 주세요";
                }
            } else {
                fightCount = 0;
                if (overwritten) {
                    overwritten = false;
                    status = OK_STATUS;
                }
            }
        } catch (Exception e) {
            // 확인 실패는 무시(시연은 계속)
        }
    }

    /** 다른 곳(Virtual Console 웹 페이지)이 차 속도를 덮어쓰고 있는지. */
    public boolean isOverwritten() {
        return overwritten;
    }

    public boolean isAvailable() {
        return state == AVAILABLE;
    }

    public String status() {
        return status;
    }

    /** 처음 한 번(또는 전에 실패했으면 다시) Virtual Console이 있는지 확인한다. */
    private boolean ensureAvailable() {
        if (state == AVAILABLE) return true;
        if (!looksLikeEmulator()) {
            state = UNAVAILABLE;
            status = "실제 차량: 앱 화면만 시연";
            return false;
        }
        try {
            String body = request("GET", "/vehicle/state", 800);
            if (body.contains("\"gear\"") && body.contains("\"controls\"")) {
                state = AVAILABLE;
                status = OK_STATUS;
                return true;
            }
            status = "Virtual Console 응답 형식이 다름: 앱 화면만 시연";
        } catch (Exception e) {
            status = "Virtual Console 연결 안 됨: 앱 화면만 시연";
        }
        state = UNAVAILABLE;
        return false;
    }

    private void put(String pathAndQuery) {
        try {
            request("PUT", pathAndQuery, 1500);
        } catch (Exception e) {
            status = "에뮬레이터 차 연동 오류: " + e.getMessage();
            Log.w(TAG, "PUT 실패 " + pathAndQuery, e);
        }
    }

    private static String request(String method, String pathAndQuery, int timeoutMs) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(BASE + pathAndQuery).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(timeoutMs);
        conn.setReadTimeout(timeoutMs);
        if (!"GET".equals(method)) {
            // PUT·POST는 본문이 꼭 있어야 하는 HTTP 구현이 있어서 빈 본문을 명시적으로 보낸다
            conn.setDoOutput(true);
            conn.setFixedLengthStreamingMode(0);
            conn.getOutputStream().close();
        }
        try {
            int code = conn.getResponseCode();
            InputStream in = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String body = in == null ? "" : readAll(in);
            if (code >= 400) throw new IllegalStateException("HTTP " + code);
            return body;
        } finally {
            conn.disconnect();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static String lower(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT);
    }
}
