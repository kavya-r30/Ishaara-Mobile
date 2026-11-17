package com.ishaara.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class HistoryTask(val title: String, val status: String)

@Serializable
data class HistoryEntry(
    val command: String,
    val summary: String,
    val success: Boolean,
    val timeMillis: Long,
    val tasks: List<HistoryTask>,
)

object HistoryCodec {
    private const val MAX_ENTRIES = 30

    fun encode(entries: List<HistoryEntry>): String = Json.encodeToString(entries)

    fun decode(raw: String?): List<HistoryEntry> = try {
        if (raw == null) emptyList() else Json.decodeFromString<List<HistoryEntry>>(raw)
    } catch (e: SerializationException) {
        emptyList()
    }

    fun add(entries: List<HistoryEntry>, entry: HistoryEntry): List<HistoryEntry> =
        (listOf(entry) + entries).take(MAX_ENTRIES)
}

private val Context.historyStore by preferencesDataStore("history")
private val ENTRIES = stringPreferencesKey("entries")

class HistoryStore(private val context: Context) {
    val entries: Flow<List<HistoryEntry>> = context.historyStore.data.map { HistoryCodec.decode(it[ENTRIES]) }

    suspend fun add(entry: HistoryEntry) {
        context.historyStore.edit { prefs ->
            prefs[ENTRIES] = HistoryCodec.encode(HistoryCodec.add(HistoryCodec.decode(prefs[ENTRIES]), entry))
        }
    }
}
