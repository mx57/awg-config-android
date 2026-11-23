package com.awgconfig.android.data

import android.content.Context
import com.awgconfig.android.core.model.HistoryEntry
import com.awgconfig.android.core.model.ProtocolKind
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** История генераций — до 100 записей, локально в filesDir/history.json. */
class HistoryStore(context: Context) {

    private val file: File = File(context.filesDir, "history.json")
    private val lock = Any()

    fun entries(): List<HistoryEntry> {
        synchronized(lock) {
            if (!file.exists()) return emptyList()
            return try {
                val arr = JSONArray(file.readText())
                (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                    entryFromJson(obj, i)
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    fun add(entry: HistoryEntry) {
        synchronized(lock) {
            val current = entries().toMutableList()
            current.add(0, entry)
            val trimmed = current.take(MAX_ENTRIES)
            file.parentFile?.mkdirs()
            file.writeText(JSONArray().apply { trimmed.forEach { put(toJson(it)) } }.toString())
        }
    }

    fun removeAt(index: Int) {
        synchronized(lock) {
            val current = entries().toMutableList()
            if (index in current.indices) {
                current.removeAt(index)
                file.writeText(JSONArray().apply { current.forEach { put(toJson(it)) } }.toString())
            }
        }
    }

    fun removeById(id: String) {
        synchronized(lock) {
            val current = entries().filterNot { it.id == id }
            file.writeText(JSONArray().apply { current.forEach { put(toJson(it)) } }.toString())
        }
    }

    fun clear() {
        synchronized(lock) { file.delete() }
    }

    companion object {
        const val MAX_ENTRIES = 100

        private fun toJson(e: HistoryEntry): JSONObject = JSONObject().apply {
            put("id", e.id)
            put("createdAt", e.createdAt)
            put("kind", e.protocolKind.key)
            put("title", e.title)
            put("text", e.text)
            e.vpnLink?.let { put("vpnLink", it) }
            put("details", JSONObject(e.details))
            put("tunnelCapable", e.tunnelCapable)
        }

        private fun entryFromJson(o: JSONObject, index: Int): HistoryEntry? {
            val kind = runCatching { ProtocolKind.entries.firstOrNull { it.key == o.optString("kind") } }
                .getOrNull() ?: return null
            return HistoryEntry(
                id = o.optString("id").takeIf { it.isNotBlank() } ?: "history-$index",
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                protocolKind = kind,
                title = o.optString("title", "?"),
                text = o.optString("text", ""),
                vpnLink = o.optString("vpnLink").takeIf { it.isNotBlank() },
                details = o.optJSONObject("details")?.let { d ->
                    val map = mutableMapOf<String, String>()
                    for (k in d.keys()) map[k] = d.optString(k)
                    map
                } ?: emptyMap(),
                tunnelCapable = o.optBoolean("tunnelCapable", false),
            )
        }
    }
}
