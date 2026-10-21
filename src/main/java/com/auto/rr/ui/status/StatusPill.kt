package com.auto.rr.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.auto.rr.ui.theme.AppStatus
import com.auto.rr.ui.theme.StatusPalette

private data class PillVisual(val color: Color, val text: String)

private fun visualFor(state: StatusState, p: StatusPalette): PillVisual = when (state) {
    is StatusState.Ok -> PillVisual(p.ok, "OK")
    is StatusState.Syncing -> PillVisual(p.sync, "SYNC")
    is StatusState.Warning -> PillVisual(p.warn, "WARN: " + state.msg)
    is StatusState.Error -> PillVisual(p.err, "ERR: " + state.msg)
    is StatusState.Testing -> PillVisual(p.test, "TEST: " + state.name)
}

@Composable
fun StatusPill(
    state: StatusState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val v = visualFor(state, AppStatus.colors)
    Text(
        text = v.text,
        color = MaterialTheme.colorScheme.background,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        modifier = modifier
            .background(v.color, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}
