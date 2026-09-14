package com.cclilshy.tayc.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cclilshy.tayc.R
import com.cclilshy.tayc.ui.NetworkInterfaceUiState
import com.cclilshy.tayc.ui.TaycUiState
import com.cclilshy.tayc.ui.components.EmptyState
import com.cclilshy.tayc.ui.components.MonospaceText
import com.cclilshy.tayc.ui.components.SectionHeading
import com.cclilshy.tayc.ui.components.SettingItem
import com.cclilshy.tayc.ui.components.SettingSwitchItem
import com.cclilshy.tayc.ui.components.StatusBadge

@Composable
fun SettingsScreen(
    state: TaycUiState,
    onOpenNetwork: () -> Unit,
    onAutoStartChange: (Boolean) -> Unit,
    onStartWithAppChange: (Boolean) -> Unit,
    onPersistentNotificationChange: (Boolean) -> Unit,
    onNoWindowModeChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SettingsGroupLabel(stringResource(R.string.connectivity)) }
        item {
            val up = state.networkInterfaces.count { it.up }
            val addresses = state.networkInterfaces.sumOf { it.addresses.size }
            SettingItem(
                iconRes = R.drawable.ic_network_24,
                title = stringResource(R.string.local_network),
                subtitle = stringResource(
                    R.string.network_summary,
                    state.networkInterfaces.size,
                    up,
                    addresses,
                ),
                onClick = onOpenNetwork,
            )
        }

        item { SettingsGroupLabel(stringResource(R.string.background_behavior)) }
        item {
            SettingSwitchItem(
                iconRes = R.drawable.ic_settings_24,
                title = stringResource(R.string.auto_start),
                subtitle = stringResource(R.string.auto_start_description),
                checked = state.autoStartEnabled,
                onCheckedChange = onAutoStartChange,
            )
        }
        item {
            SettingSwitchItem(
                iconRes = R.drawable.ic_home_24,
                title = stringResource(R.string.start_with_tayc),
                subtitle = stringResource(R.string.start_with_tayc_description),
                checked = state.startWithAppEnabled,
                onCheckedChange = onStartWithAppChange,
            )
        }
        item {
            SettingSwitchItem(
                iconRes = R.drawable.ic_info_24,
                title = stringResource(R.string.service_notification),
                subtitle = stringResource(R.string.service_notification_description),
                checked = state.persistentNotificationEnabled,
                onCheckedChange = onPersistentNotificationChange,
            )
        }
        item {
            SettingSwitchItem(
                iconRes = R.drawable.ic_no_window_24,
                title = stringResource(R.string.no_window_mode),
                subtitle = stringResource(R.string.no_window_mode_description),
                checked = state.noWindowModeEnabled,
                onCheckedChange = onNoWindowModeChange,
            )
        }

        item { SettingsGroupLabel(stringResource(R.string.about)) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(modifier = Modifier.padding(17.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        StatusBadge("0.1.0")
                    }
                    Text(
                        text = stringResource(R.string.about_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupLabel(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 5.dp, top = 9.dp, bottom = 2.dp),
    )
}

@Composable
fun NetworkScreen(
    state: TaycUiState,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val up = state.networkInterfaces.count { it.up }
    val addresses = state.networkInterfaces.sumOf { it.addresses.size }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            SectionHeading(
                title = stringResource(R.string.local_network),
                supportingText = stringResource(
                    R.string.network_summary,
                    state.networkInterfaces.size,
                    up,
                    addresses,
                ),
                action = {
                    FilledTonalIconButton(onClick = onRefresh) {
                        Icon(
                            painter = painterResource(R.drawable.ic_network_24),
                            contentDescription = stringResource(R.string.refresh_network),
                        )
                    }
                },
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )
        }

        if (state.networkInterfaces.isEmpty()) {
            item { EmptyState(stringResource(R.string.no_network_interfaces)) }
        } else {
            items(state.networkInterfaces, key = { it.name }) { network ->
                NetworkCard(network)
            }
        }
    }
}

@Composable
private fun NetworkCard(network: NetworkInterfaceUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(17.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(network.displayName, style = MaterialTheme.typography.titleMedium)
                    if (network.displayName != network.name) {
                        Text(
                            network.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusBadge(
                        stringResource(if (network.up) R.string.active else R.string.down),
                        active = network.up,
                    )
                    StatusBadge(stringResource(R.string.mtu_value, network.mtu))
                }
            }
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusBadge(
                    stringResource(if (network.loopback) R.string.loopback else R.string.network),
                )
                StatusBadge(stringResource(R.string.address_count, network.addresses.size))
            }

            if (network.addresses.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 13.dp))
                Text(stringResource(R.string.addresses), style = MaterialTheme.typography.labelLarge)
                SelectionContainer {
                    Column(modifier = Modifier.padding(top = 6.dp)) {
                        network.addresses.forEachIndexed { index, address ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Text(
                                    if (address.contains(':')) "IPv6" else "IPv4",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(46.dp),
                                )
                                MonospaceText(
                                    address,
                                    modifier = Modifier.weight(1f),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (index < network.addresses.lastIndex) HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}
