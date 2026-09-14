package com.cclilshy.tayc.app

import android.app.Application
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.cclilshy.tayc.R
import com.cclilshy.tayc.common.permission.NotificationPermissionPolicy
import com.cclilshy.tayc.event.data.EventWebhookSubscriptionStore
import com.cclilshy.tayc.event.domain.EventListenerSubscriptionPolicy
import com.cclilshy.tayc.event.permission.EventListenerPermissions
import com.cclilshy.tayc.frpc.data.FrpcProxyStore
import com.cclilshy.tayc.frpc.runtime.FrpcProcess
import com.cclilshy.tayc.gateway.data.GatewayLogStore
import com.cclilshy.tayc.gateway.data.GatewayPrefs
import com.cclilshy.tayc.gateway.domain.GatewayExtension
import com.cclilshy.tayc.gateway.domain.GatewayExtensionCatalog
import com.cclilshy.tayc.gateway.domain.GatewayStartupPolicy
import com.cclilshy.tayc.gateway.runtime.GatewayRuntimeState
import com.cclilshy.tayc.gateway.runtime.GatewayService
import com.cclilshy.tayc.network.android.NetworkInfoProvider
import com.cclilshy.tayc.proxy.domain.ProxyMapping
import com.cclilshy.tayc.qr.domain.ServerScanPayload
import com.cclilshy.tayc.ui.EventOptionUiState
import com.cclilshy.tayc.ui.ExtensionFieldUiState
import com.cclilshy.tayc.ui.ExtensionForm
import com.cclilshy.tayc.ui.ExtensionUiState
import com.cclilshy.tayc.ui.FieldKind
import com.cclilshy.tayc.ui.NetworkInterfaceUiState
import com.cclilshy.tayc.ui.ProxyMappingForm
import com.cclilshy.tayc.ui.ProxyMappingUiState
import com.cclilshy.tayc.ui.TaycUiState
import com.cclilshy.tayc.ui.UiFeedback
import com.cclilshy.tayc.ui.WebhookChannelForm
import com.cclilshy.tayc.ui.WebhookChannelUiState
import com.cclilshy.tayc.webhook.data.WebhookChannelStore
import com.cclilshy.tayc.webhook.domain.WebhookChannel
import com.cclilshy.tayc.webhook.runtime.WebhookDispatcher
import com.cclilshy.tayc.webhook.runtime.WebhookScriptProcessor

class GatewayViewModel(application: Application) : AndroidViewModel(application) {
    private val app = getApplication<Application>()
    private val prefs = GatewayPrefs.get(app)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var revision = 0L

    var uiState by mutableStateOf(buildState())
        private set

    private val preferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }

    init {
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
        refresh()
    }

    override fun onCleared() {
        prefs.unregisterOnSharedPreferenceChangeListener(preferenceListener)
        mainHandler.removeCallbacksAndMessages(null)
        super.onCleared()
    }

    fun refresh() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(::refresh)
            return
        }
        revision += 1
        uiState = buildState()
    }

    fun refreshSoon() {
        mainHandler.postDelayed(::refresh, 500)
        mainHandler.postDelayed(::refresh, 1_500)
    }

    fun toggleGateway(): Boolean {
        if (GatewayRuntimeState.isActive()) {
            stopGateway()
            return false
        }
        startGateway()
        return true
    }

    fun startGateway() {
        prefs.edit()
            .putBoolean(GatewayPrefs.KEY_RUNNING, true)
            .putString(GatewayPrefs.KEY_STATUS, "starting")
            .apply()
        GatewayRuntimeState.setActive(true)
        val intent = Intent(app, GatewayService::class.java).setAction(GatewayService.ACTION_START)
        if (shouldStartGatewayInForeground()) {
            app.startForegroundService(intent)
        } else {
            app.startService(intent)
        }
        refresh()
        refreshSoon()
    }

    fun stopGateway() {
        prefs.edit()
            .putBoolean(GatewayPrefs.KEY_RUNNING, false)
            .putString(GatewayPrefs.KEY_STATUS, "stopping")
            .apply()
        GatewayRuntimeState.setActive(false)
        app.startService(Intent(app, GatewayService::class.java).setAction(GatewayService.ACTION_STOP))
        refresh()
        refreshSoon()
    }

    fun restartGatewayIfActive() {
        if (GatewayRuntimeState.isActive()) {
            startGateway()
        }
    }

    fun startWithAppIfEnabled() {
        val enabled = prefs.getBoolean(
            GatewayPrefs.KEY_START_SERVICE_ON_APP_LAUNCH,
            GatewayStartupPolicy.isStartServiceWithAppEnabledByDefault(),
        )
        if (GatewayStartupPolicy.shouldStartServiceWithApp(enabled, GatewayRuntimeState.isActive())) {
            startGateway()
        }
    }

    fun setExtensionEnabled(extensionId: String, enabled: Boolean) {
        if (GatewayRuntimeState.isActive()) return
        val extension = GatewayExtensionCatalog.findById(extensionId)
        prefs.edit().putBoolean(extension.enabledPrefKey, enabled).apply()
    }

    fun saveExtension(extensionId: String, form: ExtensionForm): UiFeedback {
        if (GatewayRuntimeState.isActive()) {
            return UiFeedback.error(text(R.string.feedback_stop_before_services))
        }
        return try {
            val extension = GatewayExtensionCatalog.findById(extensionId)
            val editor = prefs.edit()
            extension.fields.forEach { field ->
                editor.putString(field.prefKey, form.values[field.prefKey].orEmpty())
            }
            if (isBuiltInProxy(extensionId)) {
                editor.putString(remotePortKeyFor(extensionId), form.remotePort)
            }
            editor.apply()
            if (isBuiltInProxy(extensionId)) {
                syncBuiltInFrpcProxy(extensionId, form.publishViaFrpc, form.remotePort)
            }
            refresh()
            UiFeedback.success(text(R.string.feedback_service_saved))
        } catch (_: IllegalArgumentException) {
            UiFeedback.error(text(R.string.feedback_invalid_service))
        }
    }

    fun saveMapping(previousName: String?, form: ProxyMappingForm): UiFeedback {
        if (GatewayRuntimeState.isActive()) {
            return UiFeedback.error(text(R.string.feedback_stop_before_mappings))
        }
        return try {
            val mapping = ProxyMapping(
                form.name.trim(),
                form.type.trim(),
                form.localIp.trim(),
                form.localPort.trim().toIntOrNull() ?: 0,
                form.remotePort.trim().toIntOrNull() ?: 0,
            )
            var stored = GatewayPrefs.getString(prefs, GatewayPrefs.KEY_FRPC_PROXY_MAPPINGS, "")
            if (previousName != null && previousName != mapping.name) {
                stored = FrpcProxyStore.remove(stored, previousName)
            }
            stored = FrpcProxyStore.upsert(stored, mapping)
            prefs.edit().putString(GatewayPrefs.KEY_FRPC_PROXY_MAPPINGS, stored).apply()
            UiFeedback.success(text(R.string.feedback_mapping_saved))
        } catch (_: IllegalArgumentException) {
            UiFeedback.error(text(R.string.feedback_invalid_mapping))
        }
    }

    fun removeMapping(name: String) {
        if (GatewayRuntimeState.isActive()) return
        FrpcProxyStore.remove(prefs, name)
    }

    fun saveWebhook(previousName: String?, form: WebhookChannelForm): UiFeedback {
        if (GatewayRuntimeState.isActive()) {
            return UiFeedback.error(text(R.string.feedback_stop_before_webhooks))
        }
        try {
            WebhookScriptProcessor.validate(form.script)
        } catch (err: IllegalArgumentException) {
            return UiFeedback.error(text(R.string.feedback_invalid_webhook_script, err.message.orEmpty()))
        }
        return try {
            val channel = WebhookChannel(form.name, form.targetUrl, form.proxyUrl, form.script)
            var stored = GatewayPrefs.getString(prefs, GatewayPrefs.KEY_WEBHOOK_CHANNELS, "")
            if (previousName != null && previousName != channel.name) {
                stored = WebhookChannelStore.remove(stored, previousName)
            }
            val next = WebhookChannelStore.serialize(WebhookChannelStore.upsert(stored, channel))
            prefs.edit().putString(GatewayPrefs.KEY_WEBHOOK_CHANNELS, next).apply()
            UiFeedback.success(text(R.string.feedback_webhook_saved))
        } catch (_: IllegalArgumentException) {
            UiFeedback.error(text(R.string.feedback_invalid_webhook))
        }
    }

    fun removeWebhook(name: String) {
        if (GatewayRuntimeState.isActive()) return
        val stored = GatewayPrefs.getString(prefs, GatewayPrefs.KEY_WEBHOOK_CHANNELS, "")
        prefs.edit()
            .putString(GatewayPrefs.KEY_WEBHOOK_CHANNELS, WebhookChannelStore.remove(stored, name))
            .apply()
    }

    fun setEventEnabled(eventType: String, enabled: Boolean) {
        if (GatewayRuntimeState.isActive()) return
        prefs.edit().putBoolean(eventEnabledKey(eventType), enabled).apply()
        syncEventPermissions()
    }

    fun setEventChannel(eventType: String, channel: String, selected: Boolean) {
        if (GatewayRuntimeState.isActive()) return
        val key = WebhookDispatcher.subscriptionKey(eventType)
        val stored = GatewayPrefs.getString(prefs, key, "")
        prefs.edit()
            .putString(key, EventWebhookSubscriptionStore.setSelected(stored, channel, selected))
            .apply()
    }

    fun setAutoStartEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(GatewayPrefs.KEY_AUTO_START_ENABLED, enabled).apply()
    }

    fun setStartWithAppEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(GatewayPrefs.KEY_START_SERVICE_ON_APP_LAUNCH, enabled).apply()
    }

    fun setPersistentNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(GatewayPrefs.KEY_PERSISTENT_NOTIFICATION_ENABLED, enabled).apply()
        restartGatewayIfActive()
    }

    fun setNoWindowModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(GatewayPrefs.KEY_NO_WINDOW_MODE, enabled).apply()
    }

    fun clearLog(logKey: String) {
        if (GatewayLogStore.isLogKey(logKey)) {
            prefs.edit().putString(logKey, "").apply()
        }
    }

    fun syncEventPermissions() {
        val state = EventListenerSubscriptionPolicy.sync(
            prefs.getBoolean(GatewayPrefs.KEY_EVENT_CALL_ENABLED, false),
            prefs.getBoolean(GatewayPrefs.KEY_EVENT_SMS_ENABLED, false),
            prefs.getBoolean(GatewayPrefs.KEY_EVENT_NOTIFICATION_ENABLED, false),
            EventListenerPermissions.snapshot(app),
        )
        val call = prefs.getBoolean(GatewayPrefs.KEY_EVENT_CALL_ENABLED, false)
        val sms = prefs.getBoolean(GatewayPrefs.KEY_EVENT_SMS_ENABLED, false)
        val notification = prefs.getBoolean(GatewayPrefs.KEY_EVENT_NOTIFICATION_ENABLED, false)
        if (call != state.isCallEnabled || sms != state.isSmsEnabled || notification != state.isNotificationEnabled) {
            prefs.edit()
                .putBoolean(GatewayPrefs.KEY_EVENT_CALL_ENABLED, state.isCallEnabled)
                .putBoolean(GatewayPrefs.KEY_EVENT_SMS_ENABLED, state.isSmsEnabled)
                .putBoolean(GatewayPrefs.KEY_EVENT_NOTIFICATION_ENABLED, state.isNotificationEnabled)
                .apply()
        } else {
            refresh()
        }
    }

    fun syncGuardedSettings(autoStartReady: Boolean, notificationReady: Boolean) {
        val autoStart = prefs.getBoolean(
            GatewayPrefs.KEY_AUTO_START_ENABLED,
            GatewayStartupPolicy.isAutoStartEnabledByDefault(),
        )
        val persistentNotification = prefs.getBoolean(
            GatewayPrefs.KEY_PERSISTENT_NOTIFICATION_ENABLED,
            GatewayStartupPolicy.isPersistentNotificationEnabledByDefault(),
        )
        val nextAutoStart = GatewayStartupPolicy.nextGuardedSwitchState(autoStart, autoStartReady)
        val nextPersistent = GatewayStartupPolicy.nextGuardedSwitchState(
            persistentNotification,
            notificationReady,
        )
        if (autoStart != nextAutoStart || persistentNotification != nextPersistent) {
            prefs.edit()
                .putBoolean(GatewayPrefs.KEY_AUTO_START_ENABLED, nextAutoStart)
                .putBoolean(GatewayPrefs.KEY_PERSISTENT_NOTIFICATION_ENABLED, nextPersistent)
                .apply()
            if (persistentNotification != nextPersistent) restartGatewayIfActive()
        } else {
            refresh()
        }
    }

    fun applyScannedServer(value: String): UiFeedback {
        return try {
            val payload = ServerScanPayload.parse(value)
            val wasActive = GatewayRuntimeState.isActive()
            prefs.edit()
                .putBoolean(GatewayPrefs.KEY_FRPC_ENABLED, true)
                .putString(GatewayPrefs.KEY_FRPC_SERVER, payload.server)
                .putString(GatewayPrefs.KEY_FRPC_SERVER_PORT, payload.port.toString())
                .putString(GatewayPrefs.KEY_FRPC_TOKEN, payload.token)
                .apply()
            if (wasActive) restartGatewayIfActive()
            UiFeedback.success(
                text(
                    if (wasActive) R.string.feedback_server_saved_restarted
                    else R.string.feedback_server_saved,
                ),
            )
        } catch (_: IllegalArgumentException) {
            UiFeedback.error(text(R.string.feedback_invalid_server_qr))
        }
    }

    fun isGatewayStartedSuccessfully(): Boolean =
        prefs.getBoolean(GatewayPrefs.KEY_RUNNING, false) &&
            prefs.getString(GatewayPrefs.KEY_STATUS, "") == "is running"

    fun hasPromptedForNotificationAfterStart(): Boolean =
        prefs.getBoolean(GatewayPrefs.KEY_SERVICE_NOTIFICATION_PERMISSION_PROMPTED_AFTER_START, false)

    fun markNotificationPromptedAfterStart() {
        prefs.edit()
            .putBoolean(GatewayPrefs.KEY_SERVICE_NOTIFICATION_PERMISSION_PROMPTED_AFTER_START, true)
            .apply()
    }

    private fun shouldStartGatewayInForeground(): Boolean =
        prefs.getBoolean(
            GatewayPrefs.KEY_PERSISTENT_NOTIFICATION_ENABLED,
            GatewayStartupPolicy.isPersistentNotificationEnabledByDefault(),
        ) && NotificationPermissionPolicy.canPost(app)

    private fun syncBuiltInFrpcProxy(extensionId: String, publish: Boolean, remotePort: String) {
        if (!publish) {
            FrpcProxyStore.remove(prefs, extensionId)
            return
        }
        val bindHost = GatewayPrefs.getString(
            prefs,
            GatewayPrefs.KEY_BIND_HOST,
            GatewayPrefs.DEFAULT_BIND_HOST,
        )
        val remote = remotePort.trim().toIntOrNull() ?: defaultRemotePortFor(extensionId)
        val local = if (extensionId == "http") {
            GatewayPrefs.getInt(prefs, GatewayPrefs.KEY_HTTP_PORT, GatewayPrefs.DEFAULT_HTTP_PORT)
        } else {
            GatewayPrefs.getInt(prefs, GatewayPrefs.KEY_SOCKS_PORT, GatewayPrefs.DEFAULT_SOCKS_PORT)
        }
        FrpcProxyStore.upsert(
            prefs,
            ProxyMapping(
                extensionId,
                "tcp",
                FrpcProcess.frpcLocalIp(bindHost),
                local,
                remote,
            ),
        )
    }

    private fun buildState(): TaycUiState {
        val permissions = EventListenerPermissions.snapshot(app)
        val extensionStates = GatewayExtensionCatalog.all().map { extension ->
            val fields = extension.fields.map { field ->
                ExtensionFieldUiState(
                    label = fieldLabel(field.prefKey, field.label),
                    prefKey = field.prefKey,
                    value = GatewayPrefs.getString(prefs, field.prefKey, field.defaultValue),
                    kind = when (field.type) {
                        GatewayExtension.FieldType.NUMBER -> FieldKind.Number
                        GatewayExtension.FieldType.SECRET -> FieldKind.Secret
                        else -> FieldKind.Text
                    },
                )
            }
            ExtensionUiState(
                id = extension.id,
                title = extensionTitle(extension.id),
                description = extensionDescription(extension.id),
                badgeLabel = if (extension.badgeLabel.isBlank()) "" else text(R.string.built_in),
                enabled = prefs.getBoolean(extension.enabledPrefKey, extension.isEnabledByDefault),
                endpointSummary = extensionSummary(extension, permissions.grantedCount()),
                fields = fields,
                publishViaFrpc = isBuiltInProxy(extension.id) && FrpcProxyStore.hasMapping(prefs, extension.id),
                remotePort = if (isBuiltInProxy(extension.id)) remotePortValueFor(extension.id) else "",
            )
        }

        val mappings = try {
            FrpcProxyStore.parse(GatewayPrefs.getString(prefs, GatewayPrefs.KEY_FRPC_PROXY_MAPPINGS, ""))
        } catch (_: IllegalArgumentException) {
            emptyList()
        }.map { mapping ->
            ProxyMappingUiState(
                name = mapping.name,
                type = mapping.type,
                localIp = mapping.localIp,
                localPort = mapping.localPort,
                remotePort = mapping.remotePort,
            )
        }

        val channels = WebhookChannelStore.parse(
            GatewayPrefs.getString(prefs, GatewayPrefs.KEY_WEBHOOK_CHANNELS, ""),
        ).map { channel ->
            WebhookChannelUiState(channel.name, channel.targetUrl, channel.proxyUrl, channel.script)
        }

        val eventOptions = listOf(
            eventOption(
                "call",
                text(R.string.call_events),
                GatewayPrefs.KEY_EVENT_CALL_ENABLED,
                permissions.isCallGranted,
            ),
            eventOption(
                "sms",
                text(R.string.sms_events),
                GatewayPrefs.KEY_EVENT_SMS_ENABLED,
                permissions.isSmsGranted,
            ),
            eventOption(
                "notification",
                text(R.string.app_notifications),
                GatewayPrefs.KEY_EVENT_NOTIFICATION_ENABLED,
                permissions.isNotificationGranted,
            ),
        )

        val running = GatewayRuntimeState.isActive()
        val enabledCount = extensionStates.count { it.enabled }
        val persistentNotification = prefs.getBoolean(
            GatewayPrefs.KEY_PERSISTENT_NOTIFICATION_ENABLED,
            GatewayStartupPolicy.isPersistentNotificationEnabledByDefault(),
        )
        val warningVisible = GatewayStartupPolicy.shouldShowServiceNotificationWarning(
            isGatewayStartedSuccessfully(),
            persistentNotification,
            NotificationPermissionPolicy.canPost(app),
        )

        val networks = NetworkInfoProvider.collect().map { item ->
            NetworkInterfaceUiState(
                name = item.name,
                displayName = item.displayName?.takeIf { it.isNotBlank() } ?: item.name,
                up = item.isUp,
                loopback = item.isLoopback,
                mtu = item.mtu,
                addresses = item.addresses,
            )
        }

        val logKeys = listOf(
            GatewayLogStore.KEY_TOTAL_LOG,
            GatewayLogStore.KEY_HTTP_LOG,
            GatewayLogStore.KEY_SOCKS_LOG,
            GatewayLogStore.KEY_FRPC_LOG,
            GatewayLogStore.KEY_WEBHOOK_LOG,
            GatewayLogStore.KEY_EVENT_LISTENER_LOG,
        )

        return TaycUiState(
            running = running,
            enabledServiceCount = enabledCount,
            extensions = extensionStates,
            mappings = mappings,
            webhookChannels = channels,
            eventOptions = eventOptions,
            autoStartEnabled = prefs.getBoolean(
                GatewayPrefs.KEY_AUTO_START_ENABLED,
                GatewayStartupPolicy.isAutoStartEnabledByDefault(),
            ),
            startWithAppEnabled = prefs.getBoolean(
                GatewayPrefs.KEY_START_SERVICE_ON_APP_LAUNCH,
                GatewayStartupPolicy.isStartServiceWithAppEnabledByDefault(),
            ),
            persistentNotificationEnabled = persistentNotification,
            noWindowModeEnabled = prefs.getBoolean(GatewayPrefs.KEY_NO_WINDOW_MODE, false),
            notificationWarningVisible = warningVisible,
            networkInterfaces = networks,
            logs = logKeys.associateWith { GatewayLogStore.get(prefs, it).orEmpty() },
            revision = revision,
        )
    }

    private fun eventOption(
        id: String,
        label: String,
        enabledKey: String,
        permissionGranted: Boolean,
    ): EventOptionUiState {
        val channelKey = WebhookDispatcher.subscriptionKey(id)
        return EventOptionUiState(
            id = id,
            label = label,
            enabled = prefs.getBoolean(enabledKey, false),
            permissionGranted = permissionGranted,
            selectedChannels = EventWebhookSubscriptionStore.parse(
                GatewayPrefs.getString(prefs, channelKey, ""),
            ).toSet(),
        )
    }

    private fun extensionSummary(extension: GatewayExtension, permissionCount: Int): String {
        return when (extension.id) {
            "http" -> {
                GatewayPrefs.getString(prefs, GatewayPrefs.KEY_BIND_HOST, GatewayPrefs.DEFAULT_BIND_HOST) +
                    ":" + GatewayPrefs.getString(
                    prefs,
                    GatewayPrefs.KEY_HTTP_PORT,
                    GatewayPrefs.DEFAULT_HTTP_PORT.toString(),
                ) + frpcMappingSummary("http") + authSummary(GatewayPrefs.KEY_HTTP_AUTH_USERNAME)
            }

            "socks5" -> {
                GatewayPrefs.getString(prefs, GatewayPrefs.KEY_BIND_HOST, GatewayPrefs.DEFAULT_BIND_HOST) +
                    ":" + GatewayPrefs.getString(
                    prefs,
                    GatewayPrefs.KEY_SOCKS_PORT,
                    GatewayPrefs.DEFAULT_SOCKS_PORT.toString(),
                ) + frpcMappingSummary("socks5") + authSummary(GatewayPrefs.KEY_SOCKS_AUTH_USERNAME)
            }

            "event_listener" -> text(R.string.permissions_summary, permissionCount)
            "webhook" -> {
                val count = WebhookChannelStore.parse(
                    GatewayPrefs.getString(prefs, GatewayPrefs.KEY_WEBHOOK_CHANNELS, ""),
                ).size
                if (count == 0) text(R.string.no_channels) else text(R.string.channel_count, count)
            }

            else -> {
                val server = GatewayPrefs.getString(prefs, GatewayPrefs.KEY_FRPC_SERVER, "")
                val port = GatewayPrefs.getString(
                    prefs,
                    GatewayPrefs.KEY_FRPC_SERVER_PORT,
                    GatewayPrefs.DEFAULT_FRPC_SERVER_PORT.toString(),
                )
                if (server.isBlank()) text(R.string.not_configured) else "$server:$port"
            }
        }
    }

    private fun authSummary(usernameKey: String): String =
        if (GatewayPrefs.getString(prefs, usernameKey, "").isBlank()) "" else text(R.string.auth_suffix)

    private fun frpcMappingSummary(mappingName: String): String {
        val mapping = FrpcProxyStore.find(prefs, mappingName)
        if (!prefs.getBoolean(GatewayPrefs.KEY_FRPC_ENABLED, false) || mapping == null) {
            return text(R.string.frpc_off_suffix)
        }
        return " → ${mapping.remotePort}"
    }

    private fun remotePortValueFor(extensionId: String): String {
        val mapping = FrpcProxyStore.find(prefs, extensionId)
        if (mapping != null) return mapping.remotePort.toString()
        return GatewayPrefs.getString(
            prefs,
            remotePortKeyFor(extensionId),
            defaultRemotePortFor(extensionId).toString(),
        )
    }

    private fun isBuiltInProxy(extensionId: String): Boolean =
        extensionId == "http" || extensionId == "socks5"

    private fun remotePortKeyFor(extensionId: String): String =
        if (extensionId == "http") GatewayPrefs.KEY_HTTP_REMOTE_PORT else GatewayPrefs.KEY_SOCKS_REMOTE_PORT

    private fun defaultRemotePortFor(extensionId: String): Int =
        if (extensionId == "http") GatewayPrefs.DEFAULT_HTTP_REMOTE_PORT else GatewayPrefs.DEFAULT_SOCKS_REMOTE_PORT

    private fun eventEnabledKey(eventType: String): String = when (eventType) {
        "call" -> GatewayPrefs.KEY_EVENT_CALL_ENABLED
        "sms" -> GatewayPrefs.KEY_EVENT_SMS_ENABLED
        else -> GatewayPrefs.KEY_EVENT_NOTIFICATION_ENABLED
    }

    private fun extensionTitle(extensionId: String): String = text(
        when (extensionId) {
            "frpc" -> R.string.extension_frpc
            "webhook" -> R.string.extension_webhook
            "http" -> R.string.extension_http
            "socks5" -> R.string.extension_socks5
            else -> R.string.extension_event_listener
        },
    )

    private fun extensionDescription(extensionId: String): String = text(
        when (extensionId) {
            "frpc" -> R.string.extension_frpc_description
            "webhook" -> R.string.extension_webhook_description
            "http" -> R.string.extension_http_description
            "socks5" -> R.string.extension_socks5_description
            else -> R.string.extension_event_listener_description
        },
    )

    private fun fieldLabel(prefKey: String, fallback: String): String = when (prefKey) {
        GatewayPrefs.KEY_FRPC_SERVER -> text(R.string.server)
        GatewayPrefs.KEY_FRPC_SERVER_PORT -> text(R.string.server_port)
        GatewayPrefs.KEY_FRPC_TOKEN -> text(R.string.token)
        GatewayPrefs.KEY_HTTP_PORT,
        GatewayPrefs.KEY_SOCKS_PORT,
        -> text(R.string.local_port)
        GatewayPrefs.KEY_BIND_HOST -> text(R.string.bind_host)
        GatewayPrefs.KEY_HTTP_AUTH_USERNAME,
        GatewayPrefs.KEY_SOCKS_AUTH_USERNAME,
        -> text(R.string.auth_username)
        GatewayPrefs.KEY_HTTP_AUTH_PASSWORD,
        GatewayPrefs.KEY_SOCKS_AUTH_PASSWORD,
        -> text(R.string.auth_password)
        else -> fallback
    }

    private fun text(id: Int, vararg args: Any): String = app.getString(id, *args)
}
