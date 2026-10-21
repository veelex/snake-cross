package com.auto.rr.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.auto.rr.data.ApiKeyStore
import com.auto.rr.data.remote.ws.WsConnectionState
import com.auto.rr.di.ServiceLocator
import com.auto.rr.ui.theme.AppStatus
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private data class Developer(
    val name: String,
    val role: String,
    /** Leave a field empty to hide its button. */
    val website: String = "",
    val email: String = "",
    /** International format, e.g. "+254700000000". */
    val phone: String = ""
)

/** Edit these to personalise the About section. */
private object DeveloperInfo {
    const val BIO = "Snake Cross is a trading bot for EUR/USD : it reads candles from Twelve Data, finds double and triple tops and bottoms " +
    "using swing pivots, waits for a neckline break to confirm them, and backtests every entry with fees and slippage included. " +
    "There's an Elliott wave detector, fair value gaps, EMA sweeps, and session shading — all optional, all tunable from a bottom sheet that keeps the " +
    "chart visible while you adjust it." +
    "The engine underneath is AutoRR, an early version build of Snake Cross. We reused it, tightened it, and shipped it here. " +"\n" + "\n" +
    "Most importantly, Snake Cross is built to make pattern-based risk/reward backtesting fast, " +
    "transparent and pleasant on a phone."

    val DEVELOPERS = listOf(
        Developer(name = "Lennin Jesse Oruko", role = "Developer of Snake Cross", website = "https://www.google.com", email = "len@gmail.com"),
        Developer(name = "Jay Mohammed", role = "Developer of Snake Cross", website = "https://www.google.com", email = "jay@gmail.com")
    )
}

/**
 * Profile hub that opens from the action bar. Everything account-like lives here:
 * data-provider key management, the (future) live broker link and the developer info.
 */
@Composable
fun ProfileDialog(onDismiss: () -> Unit) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .heightIn(max = maxHeight)
        ) {
            Column(modifier = Modifier.padding(top = 20.dp, start = 20.dp, end = 20.dp, bottom = 8.dp)) {
                Text("Profile", style = MaterialTheme.typography.titleLarge)
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp)
                ) {
                    ProfileSection("Data connection", ProfileGlyph.DATA_KEY, trailing = { ConnectionBadge() }) {
                        ConnectionDetail()
                        ApiKeyManager()
                    }
                    BrokerSection()
                    AboutSection()
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    }
}

@Composable
private fun ProfileSection(
    title: String,
    glyph: ProfileGlyph,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileIcon(glyph, MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            content()
        }
    }
}

/** Placeholder for the planned live broker integration. Intentionally inert in this version. */
@Composable
private fun BrokerSection() {
    ProfileSection("Live broker", ProfileGlyph.BROKER) {
        Text(
            "Link a broker account to run Snake Cross signals on live markets.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(onClick = {}, enabled = false) { Text("Connect to live broker") }
        Text(
            "Coming in a future version.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull()
    }
    ProfileSection("About the developers", ProfileGlyph.DEVELOPER) {
        Text(DeveloperInfo.BIO, style = MaterialTheme.typography.bodySmall)
        for (dev in DeveloperInfo.DEVELOPERS) DeveloperBlock(dev)
        if (version != null) {
            Text(
                "Version $version" + "Copyright © 2026 Lennin",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DeveloperBlock(dev: Developer) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
        Text(dev.name, style = MaterialTheme.typography.titleMedium)
        Text(
            dev.role,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (dev.website.isNotBlank() || dev.email.isNotBlank() || dev.phone.isNotBlank()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp)
            ) {
                if (dev.website.isNotBlank()) {
                    ContactButton("Website", ProfileGlyph.WEBSITE) {
                        open(context, Intent(Intent.ACTION_VIEW, Uri.parse(dev.website)))
                    }
                }
                if (dev.email.isNotBlank()) {
                    ContactButton("Email", ProfileGlyph.EMAIL) {
                        open(context, Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + dev.email)))
                    }
                }
                if (dev.phone.isNotBlank()) {
                    // ACTION_DIAL opens the dialer without needing the CALL_PHONE permission.
                    ContactButton("Call", ProfileGlyph.PHONE) {
                        open(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + dev.phone)))
                    }
                }
            }
            if (dev.phone.isNotBlank()) {
                Text(
                    dev.phone,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun open(context: android.content.Context, intent: Intent) {
    runCatching { context.startActivity(intent) }
}

@Composable
private fun ContactButton(label: String, glyph: ProfileGlyph, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
        ProfileIcon(glyph, MaterialTheme.colorScheme.primary, size = 18.dp)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

private data class LinkVisual(val label: String, val detail: String, val color: Color)

@Composable
private fun linkVisual(): LinkVisual {
    val ws by ServiceLocator.wsClient.state.collectAsStateWithLifecycle()
    val key by ApiKeyStore.key.collectAsStateWithLifecycle()
    val p = AppStatus.colors
    return when {
        key.isNullOrBlank() -> LinkVisual("Offline", "No API key set. Add one below to get live prices.", p.warn)
        else -> when (val st = ws) {
            is WsConnectionState.Connected ->
            LinkVisual("Live", "Receiving live prices.", p.ok)
            is WsConnectionState.DrainingQueue ->
            LinkVisual("Live", "Catching up on queued prices.", p.sync)
            is WsConnectionState.Connecting ->
            LinkVisual("Connecting", "Opening the live price feed.", p.sync)
            is WsConnectionState.BackingOff ->
            LinkVisual(
                "Offline",
                "Connection lost. Retrying in about " + ((st.nextAttemptInMs + 999) / 1000) +
                " s (attempt " + st.attempt + "). Saved candles still work.",
                p.err
            )
            is WsConnectionState.Disconnected ->
            LinkVisual("Offline", "Live feed is not connected. Saved candles still work.", p.warn)
        }
    }
}

/** Pill next to the section title: coloured dot plus Live / Connecting / Offline. */
@Composable
private fun ConnectionBadge() {
    val v = linkVisual()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
        .clip(RoundedCornerShape(50))
        .background(v.color.copy(alpha = 0.14f))
        .semantics { contentDescription = "Connection status: " + v.label }
        .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Box(Modifier.size(8.dp).background(v.color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(v.label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ConnectionDetail() {
    Text(
        linkVisual().detail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
