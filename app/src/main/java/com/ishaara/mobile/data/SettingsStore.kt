package com.ishaara.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ishaara.mobile.BuildConfig
import com.ishaara.mobile.llm.AzureConfig
import com.ishaara.mobile.voice.SarvamConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class AppSettings(
    val azureEndpoint: String = "",
    val azureDeployment: String = "",
    val azureApiVersion: String = "",
    val azureApiKey: String = "",
    val sarvamApiKey: String = "",
    val ttsModel: String = "bulbul:v4-flash",
    val ttsSpeaker: String = "aparna_hi_customer",
    val ttsSpeakerEnglish: String = "aparna_en_companion",
    val visionEnabled: Boolean = true,
    val spokenReplies: Boolean = true,
) {
    fun azure() = AzureConfig(azureEndpoint, azureDeployment, azureApiVersion, azureApiKey)
    fun sarvam() = SarvamConfig(sarvamApiKey, ttsModel, ttsSpeaker, ttsSpeakerEnglish)
}

private val Context.settingsStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val ENDPOINT = stringPreferencesKey("azure_endpoint")
        val DEPLOYMENT = stringPreferencesKey("azure_deployment")
        val API_VERSION = stringPreferencesKey("azure_api_version")
        val AZURE_KEY = stringPreferencesKey("azure_api_key")
        val SARVAM_KEY = stringPreferencesKey("sarvam_api_key")
        val TTS_MODEL = stringPreferencesKey("tts_model")
        // Renamed so a saved copy of the old default doesn't override the new one.
        val TTS_SPEAKER = stringPreferencesKey("voice_aparna")
        val TTS_SPEAKER_ENGLISH = stringPreferencesKey("voice_aparna_english")
        val VISION = booleanPreferencesKey("vision_enabled")
        val SPOKEN = booleanPreferencesKey("spoken_replies")
    }

    val settings: Flow<AppSettings> = context.settingsStore.data.map(::read)

    suspend fun current(): AppSettings = settings.first()

    suspend fun update(change: (AppSettings) -> AppSettings) {
        context.settingsStore.edit { prefs ->
            val next = change(read(prefs))
            prefs[Keys.ENDPOINT] = next.azureEndpoint
            prefs[Keys.DEPLOYMENT] = next.azureDeployment
            prefs[Keys.API_VERSION] = next.azureApiVersion
            prefs[Keys.AZURE_KEY] = next.azureApiKey
            prefs[Keys.SARVAM_KEY] = next.sarvamApiKey
            prefs[Keys.TTS_MODEL] = next.ttsModel
            prefs[Keys.TTS_SPEAKER] = next.ttsSpeaker
            prefs[Keys.TTS_SPEAKER_ENGLISH] = next.ttsSpeakerEnglish
            prefs[Keys.VISION] = next.visionEnabled
            prefs[Keys.SPOKEN] = next.spokenReplies
        }
    }

    /** Debug builds fall back to values from local.properties until the user saves their own. */
    private fun read(p: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            azureEndpoint = p[Keys.ENDPOINT] ?: BuildConfig.AZURE_ENDPOINT,
            azureDeployment = p[Keys.DEPLOYMENT] ?: BuildConfig.AZURE_DEPLOYMENT,
            azureApiVersion = p[Keys.API_VERSION] ?: BuildConfig.AZURE_API_VERSION,
            azureApiKey = p[Keys.AZURE_KEY] ?: BuildConfig.AZURE_API_KEY,
            sarvamApiKey = p[Keys.SARVAM_KEY] ?: BuildConfig.SARVAM_API_KEY,
            ttsModel = p[Keys.TTS_MODEL] ?: defaults.ttsModel,
            ttsSpeaker = p[Keys.TTS_SPEAKER] ?: defaults.ttsSpeaker,
            ttsSpeakerEnglish = p[Keys.TTS_SPEAKER_ENGLISH] ?: defaults.ttsSpeakerEnglish,
            visionEnabled = p[Keys.VISION] ?: defaults.visionEnabled,
            spokenReplies = p[Keys.SPOKEN] ?: defaults.spokenReplies,
        )
    }
}
