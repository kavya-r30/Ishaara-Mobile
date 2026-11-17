package com.ishaara.mobile

import android.app.Application
import android.util.Log
import com.ishaara.mobile.core.AgentConfig
import com.ishaara.mobile.core.AgentSession
import com.ishaara.mobile.core.RunRecord
import com.ishaara.mobile.data.HistoryEntry
import com.ishaara.mobile.data.HistoryStore
import com.ishaara.mobile.data.HistoryTask
import com.ishaara.mobile.data.SettingsStore
import com.ishaara.mobile.llm.AzureOpenAiClient
import com.ishaara.mobile.service.IshaaraService
import com.ishaara.mobile.voice.SarvamTts
import com.ishaara.mobile.voice.Speaker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Builds the object graph by hand. */
class IshaaraApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val http: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    val settings by lazy { SettingsStore(this) }
    val history by lazy { HistoryStore(this) }
    val llm by lazy { AzureOpenAiClient(http, { settings.current().azure() }) }
    val tts by lazy { SarvamTts(http, { settings.current().sarvam() }) }
    val speaker by lazy { Speaker(this, tts) }

    /** True while InputActivity is on screen, so the overlay panel doesn't cover its bar. */
    val inputOpen = MutableStateFlow(false)

    val session by lazy {
        AgentSession(
            scope = scope,
            llm = llm,
            deviceProvider = { IshaaraService.instance.value?.device },
            configProvider = { AgentConfig(visionEnabled = settings.current().visionEnabled) },
            onFinished = ::onFinished,
            trace = { if (BuildConfig.DEBUG) Log.d("Ishaara", it) },
        )
    }

    private suspend fun onFinished(record: RunRecord) {
        history.add(
            HistoryEntry(
                command = record.command,
                summary = record.summary,
                success = record.success,
                timeMillis = System.currentTimeMillis(),
                tasks = record.tasks.map { HistoryTask(it.title, it.status.name) },
            ),
        )
        if (record.spoken && settings.current().spokenReplies) {
            scope.launch { speaker.speak(record.summary) }
        }
    }
}
