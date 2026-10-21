package com.auto.rr.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.auto.rr.ui.status.StatusState
import com.auto.rr.ui.theme.AppStatus
import com.auto.rr.ui.theme.StatusPalette

private data class BarVisual(val color: Color, val label: String, val detail: String)

private fun visualFor(state: StatusState, p: StatusPalette): BarVisual = when (state) {
    is StatusState.Ok -> BarVisual(p.ok, "Live data OK", "")
    is StatusState.Syncing -> BarVisual(p.sync, "Syncing", "")
    is StatusState.Warning -> BarVisual(p.warn, "Warning", state.msg)
    is StatusState.Error -> BarVisual(p.err, "Error", state.msg)
    is StatusState.Testing -> BarVisual(p.test, "Test", state.name)
}

@Composable
fun StatusBar(
    state: StatusState,
    logSize: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val visual = visualFor(state, AppStatus.colors)
    val text = if (visual.detail.isEmpty()) visual.label
               else visual.label + "  \u00B7  " + visual.detail

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClickLabel = "Open activity log", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(visual.color, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (logSize == 1) "1 event" else "$logSize events",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
