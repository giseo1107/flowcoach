package com.giseo.flowcoach.session;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.giseo.flowcoach.domain.CoachMode;

/**
 * 주행 중 백그라운드 유지용 포그라운드 서비스 + 알림 3종.
 * D 자동 시작을 켜 두면 주행 전·후에도 '대기 중'으로 떠 있다(앱이 뒤에 있어도 D로 바꾸면 시작).
 *
 * 운전자는 보통 내비게이션 화면을 띄워 두므로, 흐름 코치 화면이 가려져도
 * 음성 코칭이 계속돼야 한다. 실제 판단·음성은 CoachRuntime이 하고,
 * 이 서비스는 "주행 중"이라는 상태를 시스템에 알려 앱이 종료되지 않게 한다.
 *
 * 알림(AlertPolicy 참고)
 *  - 상시 알림(조용함): "흐름에 맞음 · 권장 98 · 경부고속도로 상행" + [조용한 모드] 버튼. 화면을 덮지 않는다.
 *  - 위험 경고 팝업: 흐름 코치 화면이 안 보일 때만, 과속·급제동 반복을 몇 초 띄운다(소리는 음성 안내가 대신).
 *  - 주행 끝 알림: 화면이 안 보일 때 한 번. 누르면 리포트.
 */
public class CoachService extends Service {

    private static final String TAG = "CoachService";
    /** 예전부터 쓰던 상시 알림 채널(IMPORTANCE_LOW). */
    private static final String CHANNEL_DRIVE = "flowcoach_drive";
    private static final String CHANNEL_ALERT = "flowcoach_alert";
    private static final String CHANNEL_TRIP = "flowcoach_trip";
    private static final int ID_DRIVE = 1001;
    private static final int ID_ALERT = 1002;
    private static final int ID_TRIP = 1003;
    private static final long ALERT_SHOW_MS = 5_000;

    private static final String EXTRA_TEXT = "text";
    private static final String ACTION_TOGGLE_MODE = "com.giseo.flowcoach.TOGGLE_MODE";

    /** 서비스가 포그라운드로 떠 있는지(대기 중 포함). */
    private static volatile boolean running = false;

    /**
     * 대기: 주행 전·후에도 서비스를 유지해, 앱이 뒤에 있을 때 D로 바꿔도 코칭을 시작할 수 있게 한다.
     * (Android는 앱이 뒤에 있을 때 새 포그라운드 서비스를 띄우지 못하게 막으므로, 화면이 보일 때 미리 띄워 둔다.)
     * 이미 떠 있으면 알림 문구만 바꾼다.
     */
    public static void standby(Context context, String text) {
        if (running) {
            update(context, text);
            NotificationManager nm = context.getSystemService(NotificationManager.class);
            if (nm != null) nm.cancel(ID_ALERT);
            return;
        }
        start(context, text);
    }

    /** 주행 시작 시 호출. 대기 중이라 이미 떠 있으면 문구만 바꾼다(앱이 뒤에 있어도 된다). */
    public static void start(Context context, String text) {
        if (running) {
            update(context, text);
            return;
        }
        // location 타입 포그라운드 서비스는 위치 권한이 있어야 한다.
        // 권한 없이 시작하면 시스템이 앱을 종료시키므로, 그때는 화면이 켜져 있는 동안만 동작한다.
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "위치 권한 없음 → 백그라운드 유지 없이 동작");
            return;
        }
        Intent intent = new Intent(context, CoachService.class).putExtra(EXTRA_TEXT, text);
        try {
            context.startForegroundService(intent);
        } catch (RuntimeException e) {
            Log.w(TAG, "포그라운드 서비스 시작 실패", e);
        }
    }

    /** 상시 알림 문구만 조용히 갱신(백그라운드에서도 가능). */
    public static void update(Context context, String text) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(ID_DRIVE, buildDrive(context, text));
    }

    /** 위험 경고 팝업(흐름 코치 화면이 안 보일 때만 호출). 몇 초 뒤 저절로 사라진다. */
    public static void alert(Context context, String text) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        ensureChannels(nm);
        Notification n = new Notification.Builder(context, CHANNEL_ALERT)
                .setSmallIcon(context.getApplicationInfo().icon)
                .setContentTitle("흐름 코치")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                // Android Automotive는 주행 중(UX 제한)에는 일반 알림을 상단 팝업으로 띄우지 않는다.
                // 주행 안내로 분류해야 주행 중에도 잠깐 뜬다. 5초 뒤 저절로 사라지므로 화면을 계속 가리지 않는다.
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .setOnlyAlertOnce(false)
                .setTimeoutAfter(ALERT_SHOW_MS)
                .setAutoCancel(true)
                .setContentIntent(openApp(context, 1))
                .build();
        nm.notify(ID_ALERT, n);
    }

    /** 주행이 끝났는데 화면이 안 보일 때: 점수 알림 한 번. 누르면 앱이 열리며 리포트를 보여준다. */
    public static void tripEnded(Context context, String text) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;
        ensureChannels(nm);
        Notification n = new Notification.Builder(context, CHANNEL_TRIP)
                .setSmallIcon(context.getApplicationInfo().icon)
                .setContentTitle("주행이 끝났어요")
                .setContentText(text)
                .setCategory(Notification.CATEGORY_STATUS)
                .setAutoCancel(true)
                .setContentIntent(openApp(context, 2))
                .build();
        nm.notify(ID_TRIP, n);
    }

    public static void stop(Context context) {
        running = false;
        context.stopService(new Intent(context, CoachService.class));
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) {
            nm.cancel(ID_DRIVE); // update()로 띄운 알림도 정리
            nm.cancel(ID_ALERT);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_TOGGLE_MODE.equals(intent.getAction())) {
            // 상시 알림의 [조용한 모드 / 코치 모드] 버튼
            CoachRuntime.get(this).toggleMode();
            CoachRuntime.get(this).refreshNotification();
            return START_NOT_STICKY;
        }
        String text = intent != null ? intent.getStringExtra(EXTRA_TEXT) : null;
        Notification notification = buildDrive(this, text != null ? text : "주행을 지켜보고 있어요");
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(ID_DRIVE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
            } else {
                startForeground(ID_DRIVE, notification);
            }
            running = true;
        } catch (RuntimeException e) {
            // 위치 권한이 없으면 포그라운드로 올릴 수 없다 → 화면이 켜져 있는 동안만 동작
            Log.w(TAG, "startForeground 실패(위치 권한 확인)", e);
            stopSelf();
        }
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        running = false;
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static void ensureChannels(NotificationManager nm) {
        if (nm.getNotificationChannel(CHANNEL_DRIVE) == null) {
            NotificationChannel c = new NotificationChannel(CHANNEL_DRIVE, "주행 중 상태", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("주행 중 흐름 상태와 권장 속도. 화면을 가리지 않아요.");
            nm.createNotificationChannel(c);
        }
        if (nm.getNotificationChannel(CHANNEL_ALERT) == null) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ALERT, "위험 경고", NotificationManager.IMPORTANCE_HIGH);
            c.setDescription("과속·급제동 반복. 흐름 코치 화면이 안 보일 때만 잠깐 떠요.");
            c.setSound(null, null); // 소리는 음성 안내가 대신한다
            c.enableVibration(false);
            nm.createNotificationChannel(c);
        }
        if (nm.getNotificationChannel(CHANNEL_TRIP) == null) {
            NotificationChannel c = new NotificationChannel(CHANNEL_TRIP, "주행 리포트", NotificationManager.IMPORTANCE_DEFAULT);
            c.setDescription("주행이 끝나면 흐름 점수를 알려줘요.");
            c.setSound(null, null);
            nm.createNotificationChannel(c);
        }
    }

    private static Notification buildDrive(Context context, String text) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm != null) ensureChannels(nm);

        boolean quiet = CoachRuntime.get(context).getMode() == CoachMode.QUIET;
        Intent toggle = new Intent(context, CoachService.class).setAction(ACTION_TOGGLE_MODE);
        PendingIntent toggleIntent = PendingIntent.getService(context, 10, toggle,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Action modeAction = new Notification.Action.Builder(null,
                quiet ? "코치 모드로" : "조용한 모드로", toggleIntent).build();

        return new Notification.Builder(context, CHANNEL_DRIVE)
                .setSmallIcon(context.getApplicationInfo().icon)
                .setContentTitle(!CoachRuntime.get(context).isDriving() ? "흐름 코치 대기 중"
                        : quiet ? "흐름 코치 · 조용한 모드" : "흐름 코치 주행 중")
                .setContentText(text)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                // NAVIGATION 분류는 차량 화면에서 상단에 계속 떠 있는 알림으로 보일 수 있어 STATUS로 둔다
                .setCategory(Notification.CATEGORY_STATUS)
                .setContentIntent(openApp(context, 0))
                .addAction(modeAction)
                .build();
    }

    private static PendingIntent openApp(Context context, int requestCode) {
        Intent open = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        return open == null ? null
                : PendingIntent.getActivity(context, requestCode, open, PendingIntent.FLAG_IMMUTABLE);
    }
}
