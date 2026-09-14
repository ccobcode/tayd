package com.cclilshy.tayc.gateway.runtime;

import com.cclilshy.tayc.proxy.engine.Socks5ProxyServer;
import com.cclilshy.tayc.proxy.engine.HttpConnectProxyServer;
import com.cclilshy.tayc.proxy.domain.ProxyAuth;
import com.cclilshy.tayc.frpc.runtime.FrpcProcess;
import com.cclilshy.tayc.gateway.data.GatewaySettings;
import com.cclilshy.tayc.gateway.data.GatewayLogStore;
import com.cclilshy.tayc.gateway.data.GatewayPrefs;
import com.cclilshy.tayc.gateway.domain.GatewayStartupPolicy;
import com.cclilshy.tayc.R;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import java.net.InetAddress;

public final class GatewayService extends Service {
    public static final String ACTION_START = "com.cclilshy.tayc.START";
    public static final String ACTION_STOP = "com.cclilshy.tayc.STOP";
    public static final String EXTRA_FORCE_FOREGROUND = "force_foreground";
    private static final String CHANNEL_ID = "gateway";
    private static final int NOTIFICATION_ID = 1001;

    private HttpConnectProxyServer httpProxy;
    private Socks5ProxyServer socksProxy;
    private FrpcProcess frpcProcess;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopGateway(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        boolean foreground = shouldRunForeground(intent);
        if (foreground) {
            startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.status_starting)));
        }
        startGateway(foreground);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        stopGateway(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void startGateway(boolean foreground) {
        stopGateway(false);
        SharedPreferences prefs = GatewayPrefs.get(this);
        GatewaySettings settings = GatewaySettings.from(prefs);
        try {
            InetAddress bindAddress = InetAddress.getByName(settings.bindHost);
            if (settings.httpEnabled) {
                httpProxy = new HttpConnectProxyServer(
                        bindAddress,
                        settings.httpPort,
                        ProxyAuth.from(settings.httpAuthUsername, settings.httpAuthPassword),
                        line -> appendLog(GatewayLogStore.KEY_HTTP_LOG, "http", line));
                httpProxy.start();
            }
            if (settings.socksEnabled) {
                socksProxy = new Socks5ProxyServer(
                        bindAddress,
                        settings.socksPort,
                        ProxyAuth.from(settings.socksAuthUsername, settings.socksAuthPassword),
                        line -> appendLog(GatewayLogStore.KEY_SOCKS_LOG, "socks5", line));
                socksProxy.start();
            }
            if (settings.frpcEnabled) {
                if (settings.frpcServer.trim().isEmpty() || settings.frpcToken.trim().isEmpty()) {
                    appendLog(GatewayLogStore.KEY_FRPC_LOG, "frpc", "disabled: server/token missing");
                } else {
                    frpcProcess = new FrpcProcess();
                    frpcProcess.start(this, settings, line -> appendLog(GatewayLogStore.KEY_FRPC_LOG, "frpc", line));
                }
            }
            String text = "is running";
            appendLog(GatewayLogStore.KEY_TOTAL_LOG, "", "gateway started");
            GatewayRuntimeState.setActive(true);
            prefs.edit()
                    .putBoolean(GatewayPrefs.KEY_RUNNING, true)
                    .putString(GatewayPrefs.KEY_STATUS, text)
                    .apply();
            if (foreground) {
                startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.status_running)));
            }
        } catch (Exception err) {
            stopGateway(false);
            String message = "failed: " + err.getMessage();
            appendLog(GatewayLogStore.KEY_TOTAL_LOG, "", message);
            GatewayRuntimeState.setActive(false);
            prefs.edit()
                    .putBoolean(GatewayPrefs.KEY_RUNNING, false)
                    .putString(GatewayPrefs.KEY_STATUS, message)
                    .apply();
            if (foreground) {
                startForeground(
                        NOTIFICATION_ID,
                        buildNotification(getString(R.string.gateway_failed, err.getMessage())));
            }
        }
    }

    private void stopGateway(boolean writeLog) {
        if (httpProxy != null) {
            httpProxy.close();
            httpProxy = null;
        }
        if (socksProxy != null) {
            socksProxy.close();
            socksProxy = null;
        }
        if (frpcProcess != null) {
            frpcProcess.close();
            frpcProcess = null;
        }
        GatewayRuntimeState.setActive(false);
        if (writeLog) {
            GatewayLogStore.append(GatewayPrefs.get(this), GatewayLogStore.KEY_TOTAL_LOG, "gateway stopped");
        }
        stopForeground(true);
        GatewayPrefs.get(this).edit()
                .putBoolean(GatewayPrefs.KEY_RUNNING, false)
                .putString(GatewayPrefs.KEY_STATUS, "stopped")
                .apply();
    }

    private void appendLog(String key, String label, String line) {
        GatewayLogStore.append(GatewayPrefs.get(this), key, line, label);
    }

    private Notification buildNotification(String text) {
        ensureNotificationChannel();
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOngoing(true)
                .build();
    }

    private boolean shouldRunForeground(Intent intent) {
        if (intent != null && intent.getBooleanExtra(EXTRA_FORCE_FOREGROUND, false)) {
            return true;
        }
        SharedPreferences prefs = GatewayPrefs.get(this);
        return prefs.getBoolean(
                GatewayPrefs.KEY_PERSISTENT_NOTIFICATION_ENABLED,
                GatewayStartupPolicy.isPersistentNotificationEnabledByDefault());
    }

    private void ensureNotificationChannel() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel existing = manager.getNotificationChannel(CHANNEL_ID);
        if (existing == null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.gateway),
                    NotificationManager.IMPORTANCE_LOW);
            manager.createNotificationChannel(channel);
        }
    }
}
