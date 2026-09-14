package com.cclilshy.tayc.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cclilshy.tayc.R
import com.cclilshy.tayc.ui.ExtensionUiState
import com.cclilshy.tayc.ui.TaycUiState
import com.cclilshy.tayc.ui.components.SectionHeading
import com.cclilshy.tayc.ui.components.ServiceLeadingIcon
import com.cclilshy.tayc.ui.theme.TaycBlue
import com.cclilshy.tayc.ui.theme.TaycSky

@Composable
fun HomeScreen(
    state: TaycUiState,
    onToggleGateway: () -> Unit,
    onOpenServices: () -> Unit,
    onOpenExtension: (String) -> Unit,
    onEnableNotification: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            top = 6.dp,
            end = 16.dp,
            bottom = 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            GatewayHero(
                running = state.running,
                enabledCount = state.enabledServiceCount,
                serviceCount = state.serviceCount,
                onToggle = onToggleGateway,
            )
        }

        if (state.notificationWarningVisible) {
            item {
                NotificationWarning(onClick = onEnableNotification)
            }
        }

        item {
            SectionHeading(
                title = stringResource(R.string.quick_access),
                action = {
                    androidx.compose.material3.TextButton(onClick = onOpenServices) {
                        Text(stringResource(R.string.view_all))
                    }
                },
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }

        item {
            val quick = listOfNotNull(
                state.extensions.firstOrNull { it.id == "http" },
                state.extensions.firstOrNull { it.id == "socks5" },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                quick.forEach { extension ->
                    QuickServiceCard(
                        extension = extension,
                        onClick = { onOpenExtension(extension.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            SectionHeading(
                title = stringResource(R.string.gateway_overview),
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }

        item {
            OverviewCard(state)
        }
    }
}

@Composable
private fun GatewayHero(
    running: Boolean,
    enabledCount: Int,
    serviceCount: Int,
    onToggle: () -> Unit,
) {
    val shape = MaterialTheme.shapes.extraLarge
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(18.dp, shape, ambientColor = TaycBlue.copy(alpha = 0.2f))
            .background(
                brush = Brush.linearGradient(listOf(TaycBlue, Color(0xFF0878E8), TaycSky)),
                shape = shape,
            )
            .clip(shape),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(170.dp)
                .background(Color.White.copy(alpha = 0.1f), CircleShape),
        )
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            if (running) Color(0xFF9CFFC3) else Color.White.copy(alpha = 0.72f),
                            CircleShape,
                        ),
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    text = stringResource(
                        if (running) R.string.gateway_running else R.string.gateway_stopped,
                    ),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    if (running) R.string.everything_connected else R.string.ready_when_you_are,
                ),
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge,
            )
            Text(
                text = if (running) {
                    stringResource(R.string.home_running_description)
                } else {
                    stringResource(R.string.home_stopped_description)
                },
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 5.dp),
            )

            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    HeroMetric(enabledCount.toString(), stringResource(R.string.enabled))
                    HeroMetric(serviceCount.toString(), stringResource(R.string.services))
                }
                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = TaycBlue,
                    ),
                    shape = CircleShape,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
                ) {
                    Icon(
                        painter = painterResource(if (running) R.drawable.ic_close_24 else R.drawable.ic_check_24),
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(if (running) R.string.stop else R.string.start))
                }
            }
        }
    }
}

@Composable
private fun HeroMetric(value: String, label: String) {
    Column {
        Text(
            text = value,
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.72f),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun NotificationWarning(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEFB8)),
    ) {
        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_info_24),
                contentDescription = null,
                tint = Color(0xFF765800),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.background_protection_off),
                    color = Color(0xFF4A3900),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.enable_service_notification_hint),
                    color = Color(0xFF665000),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun QuickServiceCard(
    extension: ExtensionUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ServiceLeadingIcon(extension.id)
            Spacer(Modifier.height(8.dp))
            Text(
                text = extension.title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OverviewCard(state: TaycUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(stringResource(R.string.runtime), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OverviewRow(
                stringResource(R.string.gateway),
                stringResource(if (state.running) R.string.active else R.string.inactive),
            )
            OverviewRow(
                stringResource(R.string.service_configuration),
                stringResource(R.string.enabled_count, state.enabledServiceCount, state.serviceCount),
            )
            OverviewRow(
                stringResource(R.string.frpc_mappings),
                if (state.mappings.isEmpty()) stringResource(R.string.none) else state.mappings.size.toString(),
            )
            OverviewRow(
                stringResource(R.string.webhook_channels),
                if (state.webhookChannels.isEmpty()) stringResource(R.string.none) else state.webhookChannels.size.toString(),
            )
        }
    }
}

@Composable
private fun OverviewRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.labelLarge)
    }
}
