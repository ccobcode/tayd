package com.cclilshy.tayc.app

import android.Manifest
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.cclilshy.tayc.R
import com.cclilshy.tayc.common.permission.AutoStartPermissionPolicy
import com.cclilshy.tayc.common.permission.NotificationPermissionPolicy
import com.cclilshy.tayc.event.permission.EventListenerPermissions
import com.cclilshy.tayc.event.permission.NotificationListenerConnectionPolicy
import com.cclilshy.tayc.event.runtime.EventNotificationListenerService
import com.cclilshy.tayc.gateway.data.GatewayPrefs
import com.cclilshy.tayc.gateway.domain.GatewayStartupPolicy
import com.cclilshy.tayc.gateway.runtime.GatewayRuntimeState
import com.cclilshy.tayc.qr.ui.QrScanActivity
import com.cclilshy.tayc.ui.ExtensionForm
import com.cclilshy.tayc.ui.ProxyMappingForm
import com.cclilshy.tayc.ui.TaycActions
import com.cclilshy.tayc.ui.TaycApp
import com.cclilshy.tayc.ui.UiFeedback
import com.cclilshy.tayc.ui.WebhookChannelForm
import com.cclilshy.tayc.ui.theme.TaycTheme
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : ComponentActivity(), TaycActions {
    private enum class PendingSetting {
        None,
        AutoStart,
        PersistentNotification,
        PersistentNotificationAfterStart,
    }

    private val viewModel by viewModels<GatewayViewModel>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingEventType: String? = null
    private var pendingSetting = PendingSetting.None
    private var pendingManualNotificationCheck = false

    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> completeEventPermission("call", granted) }

    private val smsPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> completeEventPermission("sms", granted) }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> completeNotificationPermission(granted) }

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val contents = result.contents ?: return@registerForActivityResult
        val feedback = viewModel.applyScannedServer(contents)
        Toast.makeText(this, feedback.message, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            TaycTheme {
                TaycApp(
                    state = viewModel.uiState,
                    actions = this@MainActivity,
                )
            }
        }

        applyNoWindowModePreference()
        viewModel.startWithAppIfEnabled()
    }

    override fun onResume() {
        super.onResume()
        applyNoWindowModePreference()
        completePendingSettingsReturn()
        completeNotificationListenerReturn()
        viewModel.syncGuardedSettings(
            autoStartReady = AutoStartPermissionPolicy.isReady(this),
            notificationReady = NotificationPermissionPolicy.canPost(this),
        )
        viewModel.syncEventPermissions()
        requestNotificationListenerRebindIfNeeded()
        viewModel.refresh()
        maybeRequestNotificationPermissionAfterManualStart()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun toggleGateway() {
        val starting = !GatewayRuntimeState.isActive()
        viewModel.toggleGateway()
        pendingManualNotificationCheck = starting
        if (starting) {
            mainHandler.postDelayed(::maybeRequestNotificationPermissionAfterManualStart, 750)
            mainHandler.postDelayed(::maybeRequestNotificationPermissionAfterManualStart, 1_750)
        }
    }

    override fun setExtensionEnabled(extensionId: String, enabled: Boolean) {
        viewModel.setExtensionEnabled(extensionId, enabled)
    }

    override fun saveExtension(extensionId: String, form: ExtensionForm): UiFeedback =
        viewModel.saveExtension(extensionId, form)

    override fun scanServerQr() {
        scanLauncher.launch(
            ScanOptions()
                .setCaptureActivity(QrScanActivity::class.java)
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(getString(R.string.scan_server_qr))
                .setBeepEnabled(false)
                .setOrientationLocked(true),
        )
    }

    override fun saveMapping(previousName: String?, form: ProxyMappingForm): UiFeedback =
        viewModel.saveMapping(previousName, form)

    override fun removeMapping(name: String) {
        viewModel.removeMapping(name)
    }

    override fun saveWebhook(previousName: String?, form: WebhookChannelForm): UiFeedback =
        viewModel.saveWebhook(previousName, form)

    override fun removeWebhook(name: String) {
        viewModel.removeWebhook(name)
    }

    override fun setEventEnabled(eventType: String, enabled: Boolean) {
        if (GatewayRuntimeState.isActive()) {
            Toast.makeText(this, getString(R.string.stop_gateway_before_editing_services), Toast.LENGTH_SHORT).show()
            return
        }
        if (!enabled) {
            viewModel.setEventEnabled(eventType, false)
            return
        }
        val permissions = EventListenerPermissions.snapshot(this)
        val granted = when (eventType) {
            "call" -> permissions.isCallGranted
            "sms" -> permissions.isSmsGranted
            else -> permissions.isNotificationGranted
        }
        if (granted) {
            viewModel.setEventEnabled(eventType, true)
            return
        }
        pendingEventType = eventType
        when (eventType) {
            "call" -> callPermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
            "sms" -> smsPermissionLauncher.launch(Manifest.permission.RECEIVE_SMS)
            else -> {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                Toast.makeText(this, getString(R.string.enable_notification_access), Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun setEventChannel(eventType: String, channel: String, selected: Boolean) {
        viewModel.setEventChannel(eventType, channel, selected)
    }

    override fun setAutoStartEnabled(enabled: Boolean) {
        if (!enabled) {
            viewModel.setAutoStartEnabled(false)
            return
        }
        if (AutoStartPermissionPolicy.isReady(this)) {
            viewModel.setAutoStartEnabled(true)
            return
        }
        viewModel.setAutoStartEnabled(false)
        pendingSetting = PendingSetting.AutoStart
        startActivity(AutoStartPermissionPolicy.guideIntent(this))
        Toast.makeText(this, getString(R.string.allow_unrestricted_battery), Toast.LENGTH_SHORT).show()
    }

    override fun setStartWithAppEnabled(enabled: Boolean) {
        viewModel.setStartWithAppEnabled(enabled)
    }

    override fun setPersistentNotificationEnabled(enabled: Boolean) {
        if (!enabled) {
            viewModel.setPersistentNotificationEnabled(false)
            return
        }
        if (NotificationPermissionPolicy.canPost(this)) {
            viewModel.setPersistentNotificationEnabled(true)
            return
        }
        requestServiceNotificationPermission(PendingSetting.PersistentNotification)
    }

    override fun setNoWindowModeEnabled(enabled: Boolean) {
        viewModel.setNoWindowModeEnabled(enabled)
        applyNoWindowModePreference()
    }

    override fun enableServiceNotification() {
        if (NotificationPermissionPolicy.canPost(this)) {
            viewModel.setPersistentNotificationEnabled(true)
        } else {
            requestServiceNotificationPermission(PendingSetting.PersistentNotification)
        }
    }

    override fun clearLog(logKey: String) {
        viewModel.clearLog(logKey)
    }

    override fun refreshNetwork() {
        viewModel.refresh()
    }

    private fun completeEventPermission(eventType: String, granted: Boolean) {
        if (pendingEventType != eventType) return
        pendingEventType = null
        viewModel.setEventEnabled(eventType, granted)
        if (!granted) {
            Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
            openAppPermissionSettings()
        }
    }

    private fun completeNotificationListenerReturn() {
        if (pendingEventType != "notification") return
        pendingEventType = null
        val granted = EventListenerPermissions.snapshot(this).isNotificationGranted
        viewModel.setEventEnabled("notification", granted)
        if (!granted) Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
    }

    private fun completeNotificationPermission(granted: Boolean) {
        val reason = pendingSetting
        pendingSetting = PendingSetting.None
        if (reason != PendingSetting.PersistentNotification &&
            reason != PendingSetting.PersistentNotificationAfterStart
        ) {
            return
        }
        viewModel.setPersistentNotificationEnabled(granted)
        if (!granted) {
            Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
            openNotificationSettings()
        }
    }

    private fun requestServiceNotificationPermission(reason: PendingSetting) {
        pendingSetting = reason
        if (NotificationPermissionPolicy.requiresRuntimePermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            openNotificationSettings()
            Toast.makeText(this, getString(R.string.enable_notifications), Toast.LENGTH_SHORT).show()
        }
    }

    private fun completePendingSettingsReturn() {
        when (pendingSetting) {
            PendingSetting.AutoStart -> {
                val granted = AutoStartPermissionPolicy.isReady(this)
                pendingSetting = PendingSetting.None
                viewModel.setAutoStartEnabled(granted)
                if (!granted) Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
            }

            PendingSetting.PersistentNotification,
            PendingSetting.PersistentNotificationAfterStart
            -> {
                if (NotificationPermissionPolicy.requiresRuntimePermission()) return
                val granted = NotificationPermissionPolicy.canPost(this)
                pendingSetting = PendingSetting.None
                viewModel.setPersistentNotificationEnabled(granted)
                if (!granted) Toast.makeText(this, getString(R.string.permission_denied), Toast.LENGTH_SHORT).show()
            }

            PendingSetting.None -> Unit
        }
    }

    private fun maybeRequestNotificationPermissionAfterManualStart() {
        if (!pendingManualNotificationCheck) return
        if (!viewModel.isGatewayStartedSuccessfully()) return
        pendingManualNotificationCheck = false
        if (!GatewayStartupPolicy.shouldRequestNotificationPermissionAfterManualStart(
                true,
                true,
                viewModel.hasPromptedForNotificationAfterStart(),
                NotificationPermissionPolicy.canPost(this),
            )
        ) {
            return
        }
        viewModel.markNotificationPromptedAfterStart()
        requestServiceNotificationPermission(PendingSetting.PersistentNotificationAfterStart)
    }

    private fun requestNotificationListenerRebindIfNeeded() {
        val prefs = GatewayPrefs.get(this)
        if (!NotificationListenerConnectionPolicy.shouldRequestRebind(
                EventListenerPermissions.snapshot(this).isNotificationGranted,
                prefs.getBoolean(GatewayPrefs.KEY_EVENT_NOTIFICATION_ENABLED, false),
                prefs.getBoolean(GatewayPrefs.KEY_RUNNING, false),
                prefs.getBoolean(GatewayPrefs.KEY_EVENT_LISTENER_ENABLED, false),
            )
        ) {
            return
        }
        NotificationListenerService.requestRebind(
            ComponentName(this, EventNotificationListenerService::class.java),
        )
    }

    private fun applyNoWindowModePreference() {
        val manager = getSystemService(ACTIVITY_SERVICE) as? ActivityManager ?: return
        val exclude = GatewayPrefs.get(this).getBoolean(GatewayPrefs.KEY_NO_WINDOW_MODE, false)
        manager.appTasks.forEach { it.setExcludeFromRecents(exclude) }
    }

    private fun openAppPermissionSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            },
        )
    }

    private fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        startActivity(intent)
    }
}
