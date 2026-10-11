package com.findmissingpep.app.data.api

import android.content.Context
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.tokenDataStore by preferencesDataStore(name = "auth_tokens")

/**
 * Provides the Firebase token for the AuthInterceptor without
 * depending on AuthRepository (avoiding circular DI).
 */
@Singleton
class TokenProvider @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val tokenKey = stringPreferencesKey("firebase_token")

    suspend fun getToken(): String? {
        return context.tokenDataStore.data
            .map { it[tokenKey] }
            .firstOrNull()
    }

    suspend fun saveToken(token: String) {
        context.tokenDataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                set(tokenKey, token)
            }
        }
    }

    suspend fun clearToken() {
        context.tokenDataStore.updateData { prefs ->
            prefs.toMutablePreferences().apply {
                remove(tokenKey)
            }
        }
    }
}
