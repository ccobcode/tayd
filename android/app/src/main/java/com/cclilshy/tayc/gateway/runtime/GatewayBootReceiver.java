package com.cclilshy.tayc.gateway.runtime;

import com.cclilshy.tayc.gateway.data.GatewayPrefs;
import com.cclilshy.tayc.gateway.domain.GatewayStartupPolicy;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public final class GatewayBootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        SharedPreferences prefs = GatewayPrefs.get(context);
        if (!prefs.getBoolean(
                GatewayPrefs.KEY_AUTO_START_ENABLED,
                GatewayStartupPolicy.isAutoStartEnabledByDefault())) {
            return;
        }
        Intent service = new Intent(context, GatewayService.class)
                .setAction(GatewayService.ACTION_START)
                .putExtra(GatewayService.EXTRA_FORCE_FOREGROUND, true);
        context.startForegroundService(service);
    }
}
