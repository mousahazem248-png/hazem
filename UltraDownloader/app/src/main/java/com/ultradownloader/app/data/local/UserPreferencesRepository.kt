package com.ultradownloader.app.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "ultra_downloader_prefs")

enum class ThemePreference {
    DARK,   // Default Luxury Obsidian Dark
    LIGHT,  // Clean Daylight
    SYSTEM
}

enum class AppLanguage(val code: String) {
    ARABIC("ar"),
    ENGLISH("en")
}

data class UserSettings(
    val theme: ThemePreference = ThemePreference.DARK,
    val language: AppLanguage = AppLanguage.ARABIC,
    val wifiOnly: Boolean = false,
    val maxConcurrentDownloads: Int = 3,
    val onboardingCompleted: Boolean = false
)

@Singleton
class UserPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme_pref")
        val LANGUAGE = stringPreferencesKey("app_language")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val MAX_CONCURRENT = intPreferencesKey("max_concurrent_downloads")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_completed")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val themeStr = prefs[Keys.THEME] ?: ThemePreference.DARK.name
        val langStr = prefs[Keys.LANGUAGE] ?: AppLanguage.ARABIC.code
        UserSettings(
            theme = runCatching { ThemePreference.valueOf(themeStr) }.getOrDefault(ThemePreference.DARK),
            language = if (langStr == "en") AppLanguage.ENGLISH else AppLanguage.ARABIC,
            wifiOnly = prefs[Keys.WIFI_ONLY] ?: false,
            maxConcurrentDownloads = (prefs[Keys.MAX_CONCURRENT] ?: 3).coerceIn(1, 5),
            onboardingCompleted = prefs[Keys.ONBOARDING_DONE] ?: false
        )
    }

    suspend fun setTheme(theme: ThemePreference) {
        context.dataStore.edit { it[Keys.THEME] = theme.name }
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setWifiOnly(wifiOnly: Boolean) {
        context.dataStore.edit { it[Keys.WIFI_ONLY] = wifiOnly }
    }

    suspend fun setMaxConcurrentDownloads(limit: Int) {
        context.dataStore.edit { it[Keys.MAX_CONCURRENT] = limit.coerceIn(1, 5) }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = completed }
    }
}
