package com.cclilshy.tayc.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cclilshy.tayc.R
import com.cclilshy.tayc.gateway.data.GatewayLogStore
import com.cclilshy.tayc.ui.components.MonospaceText

@Composable
fun LogScreen(
    logKey: String,
    log: String,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val emptyText = stringResource(
        if (logKey == GatewayLogStore.KEY_EVENT_LISTENER_LOG) {
            R.string.no_events_yet
        } else {
            R.string.no_logs_yet
        },
    )
    LaunchedEffect(log) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 30.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            SelectionContainer {
                MonospaceText(
                    text = log.ifBlank { emptyText },
                    modifier = Modifier.padding(17.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
