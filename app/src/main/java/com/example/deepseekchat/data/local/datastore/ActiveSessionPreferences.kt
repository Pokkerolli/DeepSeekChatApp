package com.example.deepseekchat.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.activeSessionDataStore by preferencesDataStore(name = "active_session")

class ActiveSessionPreferences(
    private val context: Context
) {
    private companion object {
        val ACTIVE_SESSION_ID: Preferences.Key<String> = stringPreferencesKey("active_session_id")
    }

    fun observeActiveSessionId(): Flow<String?> {
        return context.activeSessionDataStore.data.map { preferences ->
            preferences[ACTIVE_SESSION_ID]
        }
    }

    suspend fun setActiveSessionId(sessionId: String) {
        context.activeSessionDataStore.edit { preferences ->
            preferences[ACTIVE_SESSION_ID] = sessionId
        }
    }
}
