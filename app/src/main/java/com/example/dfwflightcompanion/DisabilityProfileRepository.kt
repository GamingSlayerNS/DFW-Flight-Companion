package com.example.dfwflightcompanion

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.disabilityDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "disability_profile"
)

class DisabilityProfileRepository(private val context: Context) {
    private val gson = Gson()
    private val key = stringPreferencesKey("profile_json")

    val profileFlow: Flow<DisabilityProfile?> =
        context.disabilityDataStore.data.map { prefs ->
            prefs[key]?.let { json ->
                runCatching { gson.fromJson(json, DisabilityProfile::class.java) }
                    .getOrNull()
            }
        }

    suspend fun save(profile: DisabilityProfile) {
        val now = System.currentTimeMillis()
        val toSave = profile.copy(
            createdAt = if (profile.createdAt == 0L) now else profile.createdAt,
            updatedAt = now
        )
        context.disabilityDataStore.edit { prefs ->
            prefs[key] = gson.toJson(toSave)
        }
    }

    suspend fun delete() {
        context.disabilityDataStore.edit { prefs -> prefs.remove(key) }
    }
}