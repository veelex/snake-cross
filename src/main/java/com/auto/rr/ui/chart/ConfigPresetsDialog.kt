package com.auto.rr.ui.chart

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.auto.rr.data.preset.ConfigJson
import com.auto.rr.data.preset.ConfigPresetStore
import com.auto.rr.data.preset.PresetInfo
import com.auto.rr.data.preset.PresetKind
import com.auto.rr.domain.model.Timeframe
import com.auto.rr.ui.status.StatusBus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Outcome of trying to apply a preset's JSON to the live configuration. */
data class PresetApplyResult(
    val ok: Boolean,
    val message: String,
    /** Name stored inside the file, used when an imported file is added to the saved list. */
    val name: String? = null
)

private const val MAX_IMPORT_BYTES = 512 * 1024

/**
 * Save, load, import and export for one kind of configuration (Indicators or Tune).
 *
 *  - **Save** keeps the current settings under a name on this device.
 *  - **Load** applies a saved preset to the current timeframe.
 *  - **Export** writes a `.json` file anywhere the system file picker allows (Drive, Downloads, ...).
 *  - **Import** reads such a file, applies it, and adds it to the saved list.
 */
@Composable
fun ConfigPresetsDialog(
    kind: PresetKind,
    timeframe: Timeframe,
    buildJson: (name: String) -> String,
    onApply: (json: String) -> PresetApplyResult,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }
    val presets = remember(refresh, kind) { ConfigPresetStore.list(context, kind) }
    var message by remember { mutableStateOf<PresetApplyResult?>(null) }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var pendingExport by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        val text = pendingExport
        pendingExport = null
        if (uri != null && text != null) {
            message = writeText(context, uri, text)
            if (message?.ok == true) StatusBus.info(kind.label + " settings exported to a file")
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val read = readText(context, uri)
        if (read.first == null) {
            message = PresetApplyResult(false, read.second)
            return@rememberLauncherForActivityResult
        }
        val json = read.first!!
        val result = onApply(json)
        if (result.ok) {
            val saved = ConfigPresetStore.save(
                context, kind,
                ConfigPresetStore.uniqueName(context, kind, result.name ?: "Imported"),
                json
            )
            refresh++
            StatusBus.info(kind.label + " settings imported", detail = saved ?: "")
            message = result.copy(message = result.message + if (saved != null) " Added to saved presets as \"$saved\"." else "")
        } else {
            message = result
        }
    }

    val trimmed = ConfigPresetStore.cleanName(name)
    val willOverwrite = trimmed != null && ConfigPresetStore.exists(context, kind, trimmed)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(kind.label + " presets (" + timeframe.label + ")") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // ---------------------------------------------------- save current
                Text(
                    "Save the current settings under a name, or load one you saved earlier. " +
                        "Loading applies to the timeframe you are viewing.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(ConfigPresetStore.MAX_NAME); message = null },
                    label = { Text("Preset name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        enabled = trimmed != null,
                        onClick = {
                            val n = trimmed ?: return@Button
                            val stored = ConfigPresetStore.save(context, kind, n, buildJson(n))
                            refresh++
                            if (stored != null) StatusBus.info(kind.label + " preset saved", detail = stored)
                            message = if (stored != null) PresetApplyResult(true, "Saved \"$stored\".")
                            else PresetApplyResult(false, "Enter a name first.")
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text(if (willOverwrite) "Overwrite" else "Save current") }
                    OutlinedButton(
                        onClick = {
                            val n = trimmed ?: ("Current " + kind.label.lowercase() + " " + timeframe.label)
                            pendingExport = buildJson(n)
                            exportLauncher.launch(fileName(kind, n, timeframe))
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("Export file") }
                }
                OutlinedButton(
                    onClick = {
                        message = null
                        importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Import from file") }

                message?.let {
                    Text(
                        it.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }

                // ---------------------------------------------------- saved list
                Text("Saved presets", style = MaterialTheme.typography.titleSmall)
                if (presets.isEmpty()) {
                    Text(
                        "Nothing saved yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                for (p in presets) {
                    PresetRow(
                        info = p,
                        confirmingDelete = confirmDelete == p.name,
                        onLoad = {
                            val json = ConfigPresetStore.get(context, kind, p.name)
                            message = if (json == null) PresetApplyResult(false, "That preset no longer exists.")
                            else onApply(json).let { r ->
                                if (r.ok) {
                                    StatusBus.info(kind.label + " preset loaded", detail = p.name + " on " + timeframe.label)
                                    r.copy(message = "Loaded \"" + p.name + "\" on " + timeframe.label + ".")
                                } else r
                            }
                            confirmDelete = null
                        },
                        onExport = {
                            val json = ConfigPresetStore.get(context, kind, p.name)
                            if (json != null) {
                                pendingExport = json
                                exportLauncher.launch(fileName(kind, p.name, timeframe))
                            }
                        },
                        onDelete = {
                            if (confirmDelete == p.name) {
                                ConfigPresetStore.delete(context, kind, p.name)
                                confirmDelete = null
                                refresh++
                                StatusBus.info(kind.label + " preset deleted", detail = p.name)
                                message = PresetApplyResult(true, "Deleted \"" + p.name + "\".")
                            } else {
                                confirmDelete = p.name
                            }
                        }
                    )
                }
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun PresetRow(
    info: PresetInfo,
    confirmingDelete: Boolean,
    onLoad: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 2.dp)) {
            Text(info.name, style = MaterialTheme.typography.titleSmall)
            val sub = buildList {
                info.timeframe?.let { add("from $it") }
                if (info.savedAt > 0) add(SimpleDateFormat("d MMM yyyy HH:mm", Locale.getDefault()).format(Date(info.savedAt)))
            }.joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onLoad) { Text("Load") }
                TextButton(onClick = onExport) { Text("Export") }
                TextButton(onClick = onDelete) {
                    Text(
                        if (confirmingDelete) "Confirm delete" else "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

private fun fileName(kind: PresetKind, name: String, tf: Timeframe): String {
    val safe = name.lowercase(Locale.US).replace(Regex("[^a-z0-9]+"), "-").trim('-').take(32).ifEmpty { "config" }
    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())
    return "autorr-${kind.fileTag}-$safe-${tf.wire}-$stamp.json"
}

private fun writeText(context: Context, uri: Uri, text: String): PresetApplyResult = try {
    val out = context.contentResolver.openOutputStream(uri, "wt")
        ?: error("Cannot open the destination.")
    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    PresetApplyResult(true, "Exported.")
} catch (e: Exception) {
    PresetApplyResult(false, "Export failed: " + (e.message ?: e.javaClass.simpleName))
}

/** Returns (text, errorMessage); exactly one of them is non-null. */
private fun readText(context: Context, uri: Uri): Pair<String?, String> = try {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    when {
        bytes == null -> null to "Cannot open that file."
        bytes.size > MAX_IMPORT_BYTES -> null to "That file is too large to be a configuration."
        else -> String(bytes, Charsets.UTF_8) to ""
    }
} catch (e: Exception) {
    null to ("Import failed: " + (e.message ?: e.javaClass.simpleName))
}
