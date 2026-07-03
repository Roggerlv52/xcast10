package com.rogger.xcast10.util

/*
 * Desenvolvido por Roger de Oliveira
 * Data: 03/07/2026
 * Hora: 12:22
 */
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "xcast10_prefs")

/**
 * Gestor de preferências do utilizador, baseado em Jetpack DataStore.
 * Substitui o antigo utilitário SharedPreference por uma API assíncrona (Flow/suspend).
 */
class PreferencesManager(private val context: Context) {

    private val lastVideoTitleKey = stringPreferencesKey("last_video_title")

    val lastVideoTitle: Flow<String?> = context.dataStore.data.map { it[lastVideoTitleKey] }

    suspend fun setLastVideoTitle(title: String) {
        context.dataStore.edit { it[lastVideoTitleKey] = title }
    }
}
