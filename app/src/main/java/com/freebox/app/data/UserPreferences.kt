package com.freebox.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

class UserPreferences(private val context: Context) {

    private object Keys {
        val ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
        val INTERESTS = stringSetPreferencesKey("interests")
    }

    val onboardingComplete: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[Keys.ONBOARDING_COMPLETE] ?: false }

    val interests: Flow<Set<String>> = context.dataStore.data
        .map { prefs -> prefs[Keys.INTERESTS] ?: emptySet() }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { prefs -> prefs[Keys.ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setInterests(values: Set<String>) {
        context.dataStore.edit { prefs -> prefs[Keys.INTERESTS] = values }
    }
}
