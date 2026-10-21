package com.auto.rr.data.preset

import android.content.Context

/** A saved preset as shown in the list. */
data class PresetInfo(
    val name: String,
    val savedAt: Long,
    val timeframe: String?
)

/**
 * Named presets, kept on the device. Presets are global (not per timeframe): one saved on M5 can be
 * loaded on H1. Each entry is the same JSON document that import / export uses, so what you
 * export is exactly what is stored.
 */
object ConfigPresetStore {

    private const val PREFS = "auto_rr.presets"
    const val MAX_NAME = 40

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun prefix(kind: PresetKind) = kind.id + ":"

    /** Trims, collapses whitespace and caps the length. Null when nothing usable is left. */
    fun cleanName(raw: String): String? =
        raw.replace(Regex("\\s+"), " ").trim().take(MAX_NAME).trim().ifEmpty { null }

    fun list(ctx: Context, kind: PresetKind): List<PresetInfo> {
        val pre = prefix(kind)
        return prefs(ctx).all.entries
            .filter { it.key.startsWith(pre) && it.value is String }
            .map { (key, value) ->
                val head = ConfigJson.peek(value as String)
                PresetInfo(
                    name = key.removePrefix(pre),
                    savedAt = head?.first ?: 0L,
                    timeframe = head?.second
                )
            }
            .sortedByDescending { it.savedAt }
    }

    fun get(ctx: Context, kind: PresetKind, name: String): String? {
        val key = findKey(ctx, kind, name) ?: return null
        return prefs(ctx).getString(key, null)
    }

    fun exists(ctx: Context, kind: PresetKind, name: String): Boolean = findKey(ctx, kind, name) != null

    /** Saves [json] under [name]; a case-insensitive match is overwritten. Returns the stored name. */
    fun save(ctx: Context, kind: PresetKind, name: String, json: String): String? {
        val clean = cleanName(name) ?: return null
        val key = findKey(ctx, kind, clean) ?: (prefix(kind) + clean)
        prefs(ctx).edit().putString(key, json).apply()
        return key.removePrefix(prefix(kind))
    }

    fun delete(ctx: Context, kind: PresetKind, name: String) {
        val key = findKey(ctx, kind, name) ?: return
        prefs(ctx).edit().remove(key).apply()
    }

    /** "Base", then "Base (2)", "Base (3)" ... whichever is free. */
    fun uniqueName(ctx: Context, kind: PresetKind, base: String): String {
        val b = cleanName(base) ?: "Imported"
        if (!exists(ctx, kind, b)) return b
        var n = 2
        while (true) {
            val suffix = " ($n)"
            val candidate = b.take(MAX_NAME - suffix.length).trimEnd() + suffix
            if (!exists(ctx, kind, candidate)) return candidate
            n++
        }
    }

    private fun findKey(ctx: Context, kind: PresetKind, name: String): String? {
        val pre = prefix(kind)
        val wanted = cleanName(name)?.lowercase() ?: return null
        return prefs(ctx).all.keys.firstOrNull { it.startsWith(pre) && it.removePrefix(pre).lowercase() == wanted }
    }
}
