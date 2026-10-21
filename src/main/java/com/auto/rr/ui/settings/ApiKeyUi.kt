package com.auto.rr.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.auto.rr.data.ApiKeyStore
import com.auto.rr.ui.status.StatusBus
import com.auto.rr.data.remote.ApiConfig
import com.auto.rr.data.remote.ForexRestApi
import kotlinx.coroutines.launch

/** Shared state + verify/save logic for the first-run screen and the Settings dialog. */
@Composable
private fun rememberKeyEntry(onSaved: () -> Unit): KeyEntryState {
    val scope = rememberCoroutineScope()
    val state = remember { KeyEntryState() }
    state.scope = scope
    state.onSaved = onSaved
    return state
}

private class KeyEntryState {
    var input by mutableStateOf("")
    var reveal by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    /** Set when the key could not be verified (offline etc.) so the user may still keep it. */
    var unverified by mutableStateOf<String?>(null)

    lateinit var scope: kotlinx.coroutines.CoroutineScope
    lateinit var onSaved: () -> Unit

    fun submit(api: ForexRestApi, force: Boolean = false) {
        val clean = ApiKeyStore.sanitize(input)
        if (clean.length < 8) {
            error = "That key looks too short. Copy the full key from your Twelve Data dashboard."
            return
        }
        error = null
        if (force) {
            persist(clean)
            return
        }
        busy = true
        unverified = null
        scope.launch {
            when (val r = api.verifyKey(clean)) {
                is ForexRestApi.KeyCheck.Valid -> persist(clean)
                is ForexRestApi.KeyCheck.Invalid -> error = r.message
                is ForexRestApi.KeyCheck.Unverified -> unverified = r.message
            }
            busy = false
        }
    }

    private fun persist(clean: String) {
        if (ApiKeyStore.save(clean)) {
            StatusBus.info("API key saved")
            input = ""
            onSaved()
        } else {
            error = "We couldn't store the key securely on this device."
        }
    }
}

@Composable
private fun KeyField(s: KeyEntryState, onDone: () -> Unit) {
    OutlinedTextField(
        value = s.input,
        onValueChange = { s.input = it; s.error = null; s.unverified = null },
        label = { Text("Twelve Data API key") },
        singleLine = true,
        enabled = !s.busy,
        isError = s.error != null,
        visualTransformation = if (s.reveal) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        supportingText = { s.error?.let { Text(it) } },
        trailingIcon = {
            TextButton(onClick = { s.reveal = !s.reveal }) { Text(if (s.reveal) "Hide" else "Show") }
        },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium
    )
}

/** Full-screen gate shown until the user has entered a key. */
@Composable
fun ApiKeySetupScreen() {
    val context = LocalContext.current
    val api = remember { ForexRestApi() }
    val s = rememberKeyEntry(onSaved = {})

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Snake Cross",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Connect your market data",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(end = 24.dp)
                )
                Text(
                    "Snake Cross reads EUR/USD candles from Twelve Data with your own API key. " +
                        "The key is encrypted on this device and only ever sent to Twelve Data.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    SetupStep(
                        number = "1",
                        title = "Get a free key",
                        body = "Create a Twelve Data account and copy the key from your dashboard."
                    ) {
                        OutlinedButton(
                            onClick = {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(ApiConfig.SIGNUP_URL))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            },
                            shape = MaterialTheme.shapes.medium
                        ) { Text("Open Twelve Data") }
                    }
                    SetupStep(
                        number = "2",
                        title = "Paste it here",
                        body = "We check it with Twelve Data before saving."
                    ) {
                        KeyField(s) { s.submit(api) }
                        s.unverified?.let { msg ->
                            Text(
                                "We couldn't verify the key right now: $msg",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                            OutlinedButton(
                                onClick = { s.submit(api, force = true) },
                                shape = MaterialTheme.shapes.medium
                            ) { Text("Save without verifying") }
                        }
                    }
                }
            }

            Button(
                onClick = { s.submit(api) },
                enabled = !s.busy && s.input.isNotBlank(),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (s.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Checking key")
                } else {
                    Text("Verify and continue", style = MaterialTheme.typography.labelLarge)
                }
            }

            Text(
                "Twelve Data's free plan covers historical candles. The live tick stream may need a paid plan; " +
                    "without it the chart still loads and refreshes through periodic sync.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SetupStep(
    number: String,
    title: String,
    body: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.shapes.small),
            contentAlignment = Alignment.Center
        ) {
            Text(
                number,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            content()
        }
    }
}

/**
 * Key management, embedded in the Profile dialog: shows the masked key and lets the user
 * replace or remove it. Removal asks for confirmation because it stops live data and syncing.
 */
@Composable
fun ApiKeyManager(onKeyChanged: () -> Unit = {}) {
    val api = remember { ForexRestApi() }
    val saved by ApiKeyStore.key.collectAsStateWithLifecycle()
    var replacing by rememberSaveable { mutableStateOf(false) }
    var confirmRemove by rememberSaveable { mutableStateOf(false) }
    val s = rememberKeyEntry(onSaved = { replacing = false; onKeyChanged() })

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Twelve Data API key: " + (if (saved.isNullOrBlank()) "not set" else ApiKeyStore.masked(saved)),
            style = MaterialTheme.typography.bodyMedium
        )
        if (replacing) {
            KeyField(s) { s.submit(api) }
            s.unverified?.let { msg ->
                Text(
                    "Couldn't verify: $msg",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { s.submit(api, force = true) }) { Text("Save anyway") }
            }
            if (s.busy) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { s.submit(api) }, enabled = !s.busy && s.input.isNotBlank()) {
                    Text("Verify & save")
                }
                TextButton(
                    onClick = { replacing = false; s.input = ""; s.error = null; s.unverified = null },
                    enabled = !s.busy
                ) { Text("Cancel") }
            }
        } else {
            Text(
                "Removing the key stops live data and syncing until you add one again.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { replacing = true }) { Text("Replace") }
                TextButton(onClick = { confirmRemove = true }, enabled = saved != null) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Remove API key?") },
            text = { Text("Live data and syncing stop until you add a key again.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemove = false
                    ApiKeyStore.clear()
                    StatusBus.info("API key removed")
                    onKeyChanged()
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Cancel") } }
        )
    }
}
