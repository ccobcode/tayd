package com.cclilshy.tayc.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cclilshy.tayc.R
import com.cclilshy.tayc.gateway.data.GatewayLogStore
import com.cclilshy.tayc.ui.EventOptionUiState
import com.cclilshy.tayc.ui.ExtensionForm
import com.cclilshy.tayc.ui.ExtensionUiState
import com.cclilshy.tayc.ui.FieldKind
import com.cclilshy.tayc.ui.ProxyMappingForm
import com.cclilshy.tayc.ui.ProxyMappingUiState
import com.cclilshy.tayc.ui.TaycUiState
import com.cclilshy.tayc.ui.UiFeedback
import com.cclilshy.tayc.ui.WebhookChannelForm
import com.cclilshy.tayc.ui.WebhookChannelUiState
import com.cclilshy.tayc.ui.components.EmptyState
import com.cclilshy.tayc.ui.components.SectionHeading
import com.cclilshy.tayc.ui.components.ServiceLeadingIcon
import com.cclilshy.tayc.ui.components.StatusBadge

@Composable
fun ServicesScreen(
    state: TaycUiState,
    onOpenExtension: (String) -> Unit,
    onSetEnabled: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.running) {
            item {
                LockedWhileRunningBanner()
            }
        }

        items(state.extensions, key = { it.id }) { extension ->
            ExtensionListCard(
                extension = extension,
                editable = !state.running,
                onClick = { onOpenExtension(extension.id) },
                onSetEnabled = { onSetEnabled(extension.id, it) },
            )
        }
    }
}

@Composable
private fun ExtensionListCard(
    extension: ExtensionUiState,
    editable: Boolean,
    onClick: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServiceLeadingIcon(
                    extension.id,
                    contentDescription = stringResource(
                        R.string.service_icon_description,
                        extension.title,
                    ),
                )
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = extension.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = extension.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = extension.enabled,
                    onCheckedChange = onSetEnabled,
                    enabled = editable,
                )
            }
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (extension.badgeLabel.isNotBlank()) {
                    StatusBadge(extension.badgeLabel)
                }
                StatusBadge(extension.endpointSummary, active = extension.enabled)
            }
        }
    }
}

@Composable
private fun LockedWhileRunningBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_info_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.stop_gateway_before_editing_services),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionDetailScreen(
    state: TaycUiState,
    extensionId: String,
    onSaveExtension: (String, ExtensionForm) -> UiFeedback,
    onScanServer: () -> Unit,
    onSaveMapping: (String?, ProxyMappingForm) -> UiFeedback,
    onRemoveMapping: (String) -> Unit,
    onSaveWebhook: (String?, WebhookChannelForm) -> UiFeedback,
    onRemoveWebhook: (String) -> Unit,
    onSetEventEnabled: (String, Boolean) -> Unit,
    onSetEventChannel: (String, String, Boolean) -> Unit,
    onFeedback: (UiFeedback) -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extension = state.extensions.firstOrNull { it.id == extensionId } ?: return
    when (extensionId) {
        "webhook" -> WebhookDetail(
            state = state,
            extension = extension,
            onSave = onSaveWebhook,
            onRemove = onRemoveWebhook,
            onFeedback = onFeedback,
            modifier = modifier,
        )

        else -> StandardExtensionDetail(
            state = state,
            extension = extension,
            onSaveExtension = onSaveExtension,
            onScanServer = onScanServer,
            onSaveMapping = onSaveMapping,
            onRemoveMapping = onRemoveMapping,
            onSetEventEnabled = onSetEventEnabled,
            onSetEventChannel = onSetEventChannel,
            onFeedback = onFeedback,
            onSaved = onSaved,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StandardExtensionDetail(
    state: TaycUiState,
    extension: ExtensionUiState,
    onSaveExtension: (String, ExtensionForm) -> UiFeedback,
    onScanServer: () -> Unit,
    onSaveMapping: (String?, ProxyMappingForm) -> UiFeedback,
    onRemoveMapping: (String) -> Unit,
    onSetEventEnabled: (String, Boolean) -> Unit,
    onSetEventChannel: (String, String, Boolean) -> Unit,
    onFeedback: (UiFeedback) -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier,
) {
    val values = remember(extension.id, extension.fields) {
        mutableStateMapOf<String, String>().apply {
            extension.fields.forEach { put(it.prefKey, it.value) }
        }
    }
    var publish by rememberSaveable(extension.id, extension.publishViaFrpc) {
        mutableStateOf(extension.publishViaFrpc)
    }
    var remotePort by rememberSaveable(extension.id, extension.remotePort) {
        mutableStateOf(extension.remotePort)
    }
    var mappingSheet by remember { mutableStateOf<ProxyMappingUiState?>(null) }
    var showNewMappingSheet by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<ProxyMappingUiState?>(null) }
    val mappingRemovedFeedback = stringResource(R.string.mapping_removed)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ExtensionHero(extension = extension, running = state.running)
        }

        if (state.running) {
            item { LockedWhileRunningBanner() }
        }

        if (extension.id == "event_listener") {
            item {
                SectionHeading(
                    title = stringResource(R.string.event_sources),
                    supportingText = stringResource(R.string.event_sources_description),
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
            }
            items(state.eventOptions, key = { it.id }) { event ->
                EventOptionCard(
                    event = event,
                    channels = state.webhookChannels,
                    webhookEnabled = state.extensions.firstOrNull { it.id == "webhook" }?.enabled == true,
                    editable = !state.running,
                    onEnabledChange = { onSetEventEnabled(event.id, it) },
                    onChannelChange = { channel, selected ->
                        onSetEventChannel(event.id, channel, selected)
                    },
                )
            }
        } else {
            item {
                SectionHeading(
                    title = stringResource(
                        if (extension.id == "frpc") R.string.server else R.string.configuration,
                    ),
                    supportingText = extension.endpointSummary,
                    action = if (extension.id == "frpc") {
                        {
                            FilledTonalIconButton(
                                onClick = onScanServer,
                                enabled = !state.running,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_qr_scan_24),
                                    contentDescription = stringResource(R.string.scan_server_qr),
                                )
                            }
                        }
                    } else {
                        null
                    },
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        extension.fields.forEach { field ->
                            OutlinedTextField(
                                value = values[field.prefKey].orEmpty(),
                                onValueChange = { values[field.prefKey] = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(field.label) },
                                enabled = !state.running,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = when (field.kind) {
                                        FieldKind.Number -> KeyboardType.Number
                                        FieldKind.Secret -> KeyboardType.Password
                                        FieldKind.Text -> KeyboardType.Text
                                    },
                                ),
                                visualTransformation = if (field.kind == FieldKind.Secret) {
                                    PasswordVisualTransformation()
                                } else {
                                    androidx.compose.ui.text.input.VisualTransformation.None
                                },
                                shape = MaterialTheme.shapes.medium,
                            )
                        }

                        if (extension.id == "http" || extension.id == "socks5") {
                            HorizontalDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !state.running) { publish = !publish },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        stringResource(R.string.publish_through_frpc),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        stringResource(R.string.publish_through_frpc_description),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = publish,
                                    onCheckedChange = { publish = it },
                                    enabled = !state.running,
                                )
                            }
                            OutlinedTextField(
                                value = remotePort,
                                onValueChange = { remotePort = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.remote_port)) },
                                enabled = !state.running && publish,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                shape = MaterialTheme.shapes.medium,
                            )
                        }
                    }
                }
            }

            if (extension.id == "frpc") {
                item {
                    SectionHeading(
                        title = stringResource(R.string.proxy_mappings),
                        supportingText = if (state.mappings.isEmpty()) {
                            stringResource(R.string.no_mappings_configured)
                        } else {
                            stringResource(R.string.configured_count, state.mappings.size)
                        },
                        action = {
                            FilledTonalIconButton(
                                onClick = { showNewMappingSheet = true },
                                enabled = !state.running,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_add_24),
                                    contentDescription = stringResource(R.string.add_mapping),
                                )
                            }
                        },
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                    )
                }
                if (state.mappings.isEmpty()) {
                    item { EmptyState(stringResource(R.string.no_proxy_mappings_yet)) }
                } else {
                    items(state.mappings, key = { it.name }) { mapping ->
                        MappingCard(
                            mapping = mapping,
                            editable = !state.running,
                            onEdit = { mappingSheet = mapping },
                            onRemove = { pendingRemoval = mapping },
                        )
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val feedback = onSaveExtension(
                            extension.id,
                            ExtensionForm(values.toMap(), publish, remotePort),
                        )
                        onFeedback(feedback)
                        if (feedback.success) onSaved()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.running,
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(vertical = 15.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check_24),
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.save_changes))
                }
            }
        }
    }

    if (showNewMappingSheet || mappingSheet != null) {
        MappingSheet(
            mapping = mappingSheet,
            onDismiss = {
                showNewMappingSheet = false
                mappingSheet = null
            },
            onSave = { previous, form ->
                val feedback = onSaveMapping(previous, form)
                onFeedback(feedback)
                if (feedback.success) {
                    showNewMappingSheet = false
                    mappingSheet = null
                }
            },
        )
    }

    pendingRemoval?.let { mapping ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            icon = {
                Icon(painterResource(R.drawable.ic_delete_24), contentDescription = null)
            },
            title = { Text(stringResource(R.string.remove_mapping_title)) },
            text = { Text(stringResource(R.string.remove_mapping_message, mapping.name)) },
            confirmButton = {
                Button(onClick = {
                    onRemoveMapping(mapping.name)
                    pendingRemoval = null
                    onFeedback(UiFeedback.success(mappingRemovedFeedback))
                }) {
                    Text(stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun ExtensionHero(extension: ExtensionUiState, running: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServiceLeadingIcon(extension.id)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = extension.endpointSummary,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = extension.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.72f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusBadge(
                label = when {
                    running && extension.enabled -> stringResource(R.string.running)
                    extension.enabled -> stringResource(R.string.ready)
                    else -> stringResource(R.string.off)
                },
                active = extension.enabled,
            )
        }
    }
}

@Composable
private fun MappingCard(
    mapping: ProxyMappingUiState,
    editable: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = editable, onClick = onEdit),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(mapping.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${mapping.type.uppercase()}  ${mapping.localIp}:${mapping.localPort} → ${mapping.remotePort}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            IconButton(onClick = onRemove, enabled = editable) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_24),
                    contentDescription = stringResource(R.string.remove_mapping),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MappingSheet(
    mapping: ProxyMappingUiState?,
    onDismiss: () -> Unit,
    onSave: (String?, ProxyMappingForm) -> Unit,
) {
    var name by rememberSaveable(mapping?.name) { mutableStateOf(mapping?.name.orEmpty()) }
    var type by rememberSaveable(mapping?.name) { mutableStateOf(mapping?.type ?: "tcp") }
    var localIp by rememberSaveable(mapping?.name) { mutableStateOf(mapping?.localIp ?: "127.0.0.1") }
    var localPort by rememberSaveable(mapping?.name) { mutableStateOf(mapping?.localPort?.toString().orEmpty()) }
    var remotePort by rememberSaveable(mapping?.name) { mutableStateOf(mapping?.remotePort?.toString().orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (mapping == null) R.string.add_mapping else R.string.edit_mapping),
                style = MaterialTheme.typography.headlineMedium,
            )
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(type, { type = it }, label = { Text(stringResource(R.string.field_type)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(localIp, { localIp = it }, label = { Text(stringResource(R.string.local_ip)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(
                localPort,
                { localPort = it },
                label = { Text(stringResource(R.string.local_port)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                remotePort,
                { remotePort = it },
                label = { Text(stringResource(R.string.remote_port)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Button(
                onClick = {
                    onSave(
                        mapping?.name,
                        ProxyMappingForm(name, type, localIp, localPort, remotePort),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 15.dp),
            ) {
                Text(stringResource(R.string.save_mapping))
            }
        }
    }
}

@Composable
private fun EventOptionCard(
    event: EventOptionUiState,
    channels: List<WebhookChannelUiState>,
    webhookEnabled: Boolean,
    editable: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    onChannelChange: (String, Boolean) -> Unit,
) {
    var expanded by rememberSaveable(event.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(event.label, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(
                            if (event.permissionGranted) R.string.permission_granted
                            else R.string.permission_required,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = event.enabled,
                    onCheckedChange = onEnabledChange,
                    enabled = editable,
                )
                Spacer(Modifier.width(6.dp))
                Icon(
                    painter = painterResource(
                        if (expanded) R.drawable.ic_expand_less_24 else R.drawable.ic_expand_more_24,
                    ),
                    contentDescription = stringResource(
                        if (expanded) R.string.collapse_channels else R.string.expand_channels,
                    ),
                )
            }

            AnimatedVisibility(expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider()
                    Text(
                        stringResource(R.string.webhook_channels),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                    if (!webhookEnabled) {
                        Text(
                            stringResource(R.string.webhook_extension_off),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    if (channels.isEmpty()) {
                        Text(
                            stringResource(R.string.no_webhook_channels_configured),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        channels.forEach { channel ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = editable) {
                                        onChannelChange(
                                            channel.name,
                                            channel.name !in event.selectedChannels,
                                        )
                                    }
                                    .padding(vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = channel.name in event.selectedChannels,
                                    onCheckedChange = { onChannelChange(channel.name, it) },
                                    enabled = editable,
                                )
                                Text(channel.name, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebhookDetail(
    state: TaycUiState,
    extension: ExtensionUiState,
    onSave: (String?, WebhookChannelForm) -> UiFeedback,
    onRemove: (String) -> Unit,
    onFeedback: (UiFeedback) -> Unit,
    modifier: Modifier,
) {
    var editing by remember { mutableStateOf<WebhookChannelUiState?>(null) }
    var addNew by remember { mutableStateOf(false) }
    var pendingRemoval by remember { mutableStateOf<WebhookChannelUiState?>(null) }
    val webhookRemovedFeedback = stringResource(R.string.webhook_removed)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { ExtensionHero(extension, state.running) }
        if (state.running) item { LockedWhileRunningBanner() }
        item {
            SectionHeading(
                title = stringResource(R.string.webhook_channels),
                supportingText = if (state.webhookChannels.isEmpty()) {
                    stringResource(R.string.no_channels_configured)
                } else {
                    stringResource(R.string.configured_count, state.webhookChannels.size)
                },
                action = {
                    FilledTonalIconButton(onClick = { addNew = true }, enabled = !state.running) {
                        Icon(
                            painterResource(R.drawable.ic_add_24),
                            contentDescription = stringResource(R.string.add_channel),
                        )
                    }
                },
                modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 2.dp),
            )
        }
        if (state.webhookChannels.isEmpty()) {
            item { EmptyState(stringResource(R.string.add_webhook_channel_hint)) }
        } else {
            items(state.webhookChannels, key = { it.name }) { channel ->
                WebhookCard(
                    channel = channel,
                    editable = !state.running,
                    onEdit = { editing = channel },
                    onRemove = { pendingRemoval = channel },
                )
            }
        }
    }

    if (addNew || editing != null) {
        WebhookSheet(
            channel = editing,
            onDismiss = {
                addNew = false
                editing = null
            },
            onSave = { previous, form ->
                val feedback = onSave(previous, form)
                onFeedback(feedback)
                if (feedback.success) {
                    addNew = false
                    editing = null
                }
            },
        )
    }

    pendingRemoval?.let { channel ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text(stringResource(R.string.remove_webhook_title)) },
            text = { Text(stringResource(R.string.remove_webhook_message, channel.name)) },
            confirmButton = {
                Button(onClick = {
                    onRemove(channel.name)
                    pendingRemoval = null
                    onFeedback(UiFeedback.success(webhookRemovedFeedback))
                }) { Text(stringResource(R.string.remove)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun WebhookCard(
    channel: WebhookChannelUiState,
    editable: Boolean,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = editable, onClick = onEdit),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(channel.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    channel.targetUrl,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (channel.proxyUrl.isNotBlank()) {
                    Text(
                        stringResource(R.string.proxy_value, channel.proxyUrl),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onRemove, enabled = editable) {
                Icon(
                    painterResource(R.drawable.ic_delete_24),
                    contentDescription = stringResource(R.string.remove_channel),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebhookSheet(
    channel: WebhookChannelUiState?,
    onDismiss: () -> Unit,
    onSave: (String?, WebhookChannelForm) -> Unit,
) {
    var name by rememberSaveable(channel?.name) { mutableStateOf(channel?.name.orEmpty()) }
    var targetUrl by rememberSaveable(channel?.name) { mutableStateOf(channel?.targetUrl.orEmpty()) }
    var proxyUrl by rememberSaveable(channel?.name) { mutableStateOf(channel?.proxyUrl.orEmpty()) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(if (channel == null) R.string.add_webhook else R.string.edit_webhook),
                style = MaterialTheme.typography.headlineMedium,
            )
            OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.field_name)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(targetUrl, { targetUrl = it }, label = { Text(stringResource(R.string.target_url)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(proxyUrl, { proxyUrl = it }, label = { Text(stringResource(R.string.proxy_url_optional)) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(
                onClick = {
                    onSave(channel?.name, WebhookChannelForm(name, targetUrl, proxyUrl))
                },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 15.dp),
            ) {
                Text(stringResource(R.string.save_channel))
            }
        }
    }
}

fun extensionLogKey(extensionId: String): String = when (extensionId) {
    "http" -> GatewayLogStore.KEY_HTTP_LOG
    "socks5" -> GatewayLogStore.KEY_SOCKS_LOG
    "frpc" -> GatewayLogStore.KEY_FRPC_LOG
    "webhook" -> GatewayLogStore.KEY_WEBHOOK_LOG
    "event_listener" -> GatewayLogStore.KEY_EVENT_LISTENER_LOG
    else -> GatewayLogStore.KEY_TOTAL_LOG
}

@Composable
fun extensionLogTitle(extension: ExtensionUiState): String =
    if (extension.id == "event_listener") {
        stringResource(R.string.events)
    } else {
        stringResource(R.string.extension_logs, extension.title)
    }
