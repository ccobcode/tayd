package com.cclilshy.tayc.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.ExperimentalMaterial3AdaptiveNavigationSuiteApi
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.cclilshy.tayc.R
import com.cclilshy.tayc.gateway.data.GatewayLogStore
import com.cclilshy.tayc.ui.components.TaycTopBar
import com.cclilshy.tayc.ui.screens.ExtensionDetailScreen
import com.cclilshy.tayc.ui.screens.HomeScreen
import com.cclilshy.tayc.ui.screens.LogScreen
import com.cclilshy.tayc.ui.screens.NetworkScreen
import com.cclilshy.tayc.ui.screens.ServicesScreen
import com.cclilshy.tayc.ui.screens.SettingsScreen
import com.cclilshy.tayc.ui.screens.extensionLogKey
import com.cclilshy.tayc.ui.screens.extensionLogTitle
import kotlinx.coroutines.launch

private enum class RootDestination(
    @param:StringRes val labelRes: Int,
    val iconRes: Int,
) {
    Home(R.string.nav_home, R.drawable.ic_home_24),
    Services(R.string.nav_services, R.drawable.ic_extension_24),
    Settings(R.string.nav_settings, R.drawable.ic_settings_24),
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3AdaptiveNavigationSuiteApi::class,
)
@Composable
fun TaycApp(
    state: TaycUiState,
    actions: TaycActions,
) {
    var root by rememberSaveable { mutableStateOf(RootDestination.Home) }
    var extensionId by rememberSaveable { mutableStateOf<String?>(null) }
    var networkOpen by rememberSaveable { mutableStateOf(false) }
    var logKey by rememberSaveable { mutableStateOf<String?>(null) }
    var logTitle by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val gatewayLogsTitle = stringResource(R.string.gateway_logs)

    val feedback: (UiFeedback) -> Unit = { result ->
        scope.launch { snackbarHost.showSnackbar(result.message) }
    }

    fun closeDetail() {
        when {
            logKey != null -> {
                logKey = null
                logTitle = null
            }
            extensionId != null -> extensionId = null
            networkOpen -> networkOpen = false
        }
    }

    val showingDetail = logKey != null || extensionId != null || networkOpen
    BackHandler(enabled = showingDetail, onBack = ::closeDetail)

    if (!showingDetail) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                RootDestination.entries.forEach { destination ->
                    item(
                        selected = root == destination,
                        onClick = { root = destination },
                        icon = {
                            Icon(
                                painter = painterResource(destination.iconRes),
                                contentDescription = stringResource(destination.labelRes),
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            Scaffold(
                topBar = {
                    TaycTopBar(
                        title = stringResource(
                            when (root) {
                                RootDestination.Home -> R.string.app_name
                                RootDestination.Services -> R.string.nav_services
                                RootDestination.Settings -> R.string.nav_settings
                            },
                        ),
                        subtitle = when (root) {
                            RootDestination.Home -> stringResource(R.string.private_gateway)
                            RootDestination.Services -> stringResource(R.string.endpoints_and_extensions)
                            RootDestination.Settings -> stringResource(R.string.system_preferences)
                        },
                        onLogs = if (root == RootDestination.Home) {
                            {
                                logKey = GatewayLogStore.KEY_TOTAL_LOG
                                logTitle = gatewayLogsTitle
                            }
                        } else {
                            null
                        },
                    )
                },
                snackbarHost = { SnackbarHost(snackbarHost) },
                containerColor = MaterialTheme.colorScheme.background,
            ) { padding ->
                when (root) {
                    RootDestination.Home -> HomeScreen(
                        state = state,
                        onToggleGateway = actions::toggleGateway,
                        onOpenServices = { root = RootDestination.Services },
                        onOpenExtension = {
                            root = RootDestination.Services
                            extensionId = it
                        },
                        onEnableNotification = actions::enableServiceNotification,
                        modifier = Modifier.padding(padding),
                    )

                    RootDestination.Services -> ServicesScreen(
                        state = state,
                        onOpenExtension = { extensionId = it },
                        onSetEnabled = actions::setExtensionEnabled,
                        modifier = Modifier.padding(padding),
                    )

                    RootDestination.Settings -> SettingsScreen(
                        state = state,
                        onOpenNetwork = { networkOpen = true },
                        onAutoStartChange = actions::setAutoStartEnabled,
                        onStartWithAppChange = actions::setStartWithAppEnabled,
                        onPersistentNotificationChange = actions::setPersistentNotificationEnabled,
                        onNoWindowModeChange = actions::setNoWindowModeEnabled,
                        modifier = Modifier.padding(padding),
                    )
                }
            }
        }
        return
    }

    val currentExtension = extensionId?.let { id -> state.extensions.firstOrNull { it.id == id } }
    val currentExtensionLogTitle = if (currentExtension != null) {
        extensionLogTitle(currentExtension)
    } else {
        ""
    }
    Scaffold(
        topBar = {
            when {
                logKey != null -> TaycTopBar(
                    title = logTitle ?: stringResource(R.string.logs),
                    subtitle = stringResource(R.string.live_activity),
                    onBack = ::closeDetail,
                    onClear = { actions.clearLog(logKey.orEmpty()) },
                )

                currentExtension != null -> TaycTopBar(
                    title = currentExtension.title,
                    subtitle = stringResource(R.string.service_settings),
                    onBack = ::closeDetail,
                    onLogs = {
                        logKey = extensionLogKey(currentExtension.id)
                        logTitle = currentExtensionLogTitle
                    },
                )

                networkOpen -> TaycTopBar(
                    title = stringResource(R.string.network),
                    subtitle = stringResource(R.string.local_interfaces),
                    onBack = ::closeDetail,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHost) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            logKey != null -> LogScreen(
                logKey = logKey.orEmpty(),
                log = state.logs[logKey.orEmpty()].orEmpty(),
                modifier = Modifier.padding(padding),
            )

            currentExtension != null -> ExtensionDetailScreen(
                state = state,
                extensionId = currentExtension.id,
                onSaveExtension = actions::saveExtension,
                onScanServer = actions::scanServerQr,
                onSaveMapping = actions::saveMapping,
                onRemoveMapping = actions::removeMapping,
                onSaveWebhook = actions::saveWebhook,
                onRemoveWebhook = actions::removeWebhook,
                onSetEventEnabled = actions::setEventEnabled,
                onSetEventChannel = actions::setEventChannel,
                onFeedback = feedback,
                onSaved = { extensionId = null },
                modifier = Modifier.padding(padding),
            )

            networkOpen -> NetworkScreen(
                state = state,
                onRefresh = actions::refreshNetwork,
                modifier = Modifier.padding(padding),
            )
        }
    }
}
