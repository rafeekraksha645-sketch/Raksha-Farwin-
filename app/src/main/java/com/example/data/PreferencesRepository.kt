package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.NexoraLanguage
import com.example.model.NexoraMode
import com.example.model.NexoraModelTier
import com.example.model.NexoraPreferencesData
import com.example.model.NexoraResponseStyle
import com.example.model.NexoraThemeMode
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.nexoraDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "nexora_ai_preferences"
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val RESPONSE_STYLE = stringPreferencesKey("response_style")
        val SIMPLE_EXPLANATIONS = booleanPreferencesKey("simple_explanations")
        val DEFAULT_MODE = stringPreferencesKey("default_mode")
        val MODEL_TIER = stringPreferencesKey("model_tier")
    }

    val preferencesFlow: Flow<NexoraPreferencesData> = context.nexoraDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            NexoraPreferencesData(
                language = NexoraLanguage.fromName(prefs[Keys.LANGUAGE]),
                themeMode = NexoraThemeMode.fromName(prefs[Keys.THEME_MODE]),
                responseStyle = NexoraResponseStyle.fromName(prefs[Keys.RESPONSE_STYLE]),
                simpleExplanations = prefs[Keys.SIMPLE_EXPLANATIONS] ?: false,
                defaultMode = NexoraMode.fromName(prefs[Keys.DEFAULT_MODE]),
                modelTier = NexoraModelTier.fromName(prefs[Keys.MODEL_TIER])
            )
        }

    suspend fun updatePreferences(data: NexoraPreferencesData) {
        context.nexoraDataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = data.language.name
            prefs[Keys.THEME_MODE] = data.themeMode.name
            prefs[Keys.RESPONSE_STYLE] = data.responseStyle.name
            prefs[Keys.SIMPLE_EXPLANATIONS] = data.simpleExplanations
            prefs[Keys.DEFAULT_MODE] = data.defaultMode.name
            prefs[Keys.MODEL_TIER] = data.modelTier.name
        }
    }
}
