package com.cclilshy.tayc.ui

import androidx.compose.runtime.Immutable

@Immutable
data class TaycUiState(
    val running: Boolean = false,
    val enabledServiceCount: Int = 0,
    val extensions: List<ExtensionUiState> = emptyList(),
    val mappings: List<ProxyMappingUiState> = emptyList(),
    val webhookChannels: List<WebhookChannelUiState> = emptyList(),
    val eventOptions: List<EventOptionUiState> = emptyList(),
    val autoStartEnabled: Boolean = false,
    val startWithAppEnabled: Boolean = false,
    val persistentNotificationEnabled: Boolean = false,
    val noWindowModeEnabled: Boolean = false,
    val notificationWarningVisible: Boolean = false,
    val networkInterfaces: List<NetworkInterfaceUiState> = emptyList(),
    val logs: Map<String, String> = emptyMap(),
    val revision: Long = 0,
) {
    val serviceCount: Int get() = extensions.size
}

@Immutable
data class ExtensionUiState(
    val id: String,
    val title: String,
    val description: String,
    val badgeLabel: String,
    val enabled: Boolean,
    val endpointSummary: String,
    val fields: List<ExtensionFieldUiState>,
    val publishViaFrpc: Boolean = false,
    val remotePort: String = "",
)

@Immutable
data class ExtensionFieldUiState(
    val label: String,
    val prefKey: String,
    val value: String,
    val kind: FieldKind,
)

enum class FieldKind {
    Text,
    Number,
    Secret,
}

@Immutable
data class ProxyMappingUiState(
    val name: String,
    val type: String,
    val localIp: String,
    val localPort: Int,
    val remotePort: Int,
)

@Immutable
data class WebhookChannelUiState(
    val name: String,
    val targetUrl: String,
    val proxyUrl: String,
)

@Immutable
data class EventOptionUiState(
    val id: String,
    val label: String,
    val enabled: Boolean,
    val permissionGranted: Boolean,
    val selectedChannels: Set<String>,
)

@Immutable
data class NetworkInterfaceUiState(
    val name: String,
    val displayName: String,
    val up: Boolean,
    val loopback: Boolean,
    val mtu: Int,
    val addresses: List<String>,
)

data class ExtensionForm(
    val values: Map<String, String>,
    val publishViaFrpc: Boolean,
    val remotePort: String,
)

data class ProxyMappingForm(
    val name: String,
    val type: String,
    val localIp: String,
    val localPort: String,
    val remotePort: String,
)

data class WebhookChannelForm(
    val name: String,
    val targetUrl: String,
    val proxyUrl: String,
)

@Immutable
data class UiFeedback(
    val success: Boolean,
    val message: String,
) {
    companion object {
        fun success(message: String) = UiFeedback(true, message)
        fun error(message: String) = UiFeedback(false, message)
    }
}

interface TaycActions {
    fun toggleGateway()
    fun setExtensionEnabled(extensionId: String, enabled: Boolean)
    fun saveExtension(extensionId: String, form: ExtensionForm): UiFeedback
    fun scanServerQr()
    fun saveMapping(previousName: String?, form: ProxyMappingForm): UiFeedback
    fun removeMapping(name: String)
    fun saveWebhook(previousName: String?, form: WebhookChannelForm): UiFeedback
    fun removeWebhook(name: String)
    fun setEventEnabled(eventType: String, enabled: Boolean)
    fun setEventChannel(eventType: String, channel: String, selected: Boolean)
    fun setAutoStartEnabled(enabled: Boolean)
    fun setStartWithAppEnabled(enabled: Boolean)
    fun setPersistentNotificationEnabled(enabled: Boolean)
    fun setNoWindowModeEnabled(enabled: Boolean)
    fun enableServiceNotification()
    fun clearLog(logKey: String)
    fun refreshNetwork()
}
