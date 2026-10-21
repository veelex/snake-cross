package com.auto.rr.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.auto.rr.domain.model.Timeframe

/**
 * Segmented timeframe control. Every timeframe is one tap away, and the active one is
 * always visible, instead of hiding the choice inside a dropdown.
 */
@Composable
fun TimeframeStrip(
    selected: Timeframe,
    onSelect: (Timeframe) -> Unit,
    modifier: Modifier = Modifier
) {
    val track = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .clip(track)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (tf in Timeframe.values()) {
            Segment(label = tf.label, active = tf == selected) { onSelect(tf) }
        }
    }
}

@Composable
private fun RowScope.Segment(label: String, active: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Box(
        modifier = Modifier
            .weight(1f)
            .defaultMinSize(minHeight = 36.dp)
            .clip(shape)
            .background(if (active) MaterialTheme.colorScheme.surface else androidx.compose.ui.graphics.Color.Transparent)
            .selectable(selected = active, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (active) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
