package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.Locale

private val Context.languageDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "language_preferences"
)

enum class AppLanguage(val tag: String) {
    PERSIAN("fa"),
    ENGLISH("en");

    companion object {
        fun fromTag(tag: String?): AppLanguage? {
            if (tag.isNullOrBlank()) return null
            val normalized = tag.lowercase(Locale.ROOT)
            return when {
                normalized.startsWith("fa") -> PERSIAN
                normalized.startsWith("en") -> ENGLISH
                else -> null
            }
        }

        fun resolveDeviceDefault(deviceLocale: Locale = Locale.getDefault()): AppLanguage {
            return if (deviceLocale.language.equals("fa", ignoreCase = true)) {
                PERSIAN
            } else {
                ENGLISH
            }
        }
    }
}

class LanguagePreferences(private val context: Context) {

    private val languageKey = stringPreferencesKey("selected_language_tag")

    val savedLanguageFlow: Flow<AppLanguage?> = context.languageDataStore.data.map { prefs ->
        AppLanguage.fromTag(prefs[languageKey])
    }

    suspend fun getSavedLanguage(): AppLanguage? {
        return savedLanguageFlow.first()
    }

    suspend fun saveLanguage(language: AppLanguage) {
        context.languageDataStore.edit { prefs ->
            prefs[languageKey] = language.tag
        }
    }
}
