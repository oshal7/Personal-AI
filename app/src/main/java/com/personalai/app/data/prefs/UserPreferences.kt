package com.personalai.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.personalai.app.domain.model.ModelInfo
import com.personalai.app.domain.model.ModelRegistry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "user_preferences")
private val ACTIVE_MODEL_ID = stringPreferencesKey("active_model_id")

/** Persists lightweight user settings — currently just which model is active. */
interface UserPreferences {
    val activeModel: Flow<ModelInfo>
    suspend fun setActiveModel(model: ModelInfo)
}

/** DataStore-backed implementation used in the running app. */
class UserPreferencesImpl(private val context: Context) : UserPreferences {

    override val activeModel: Flow<ModelInfo> = context.dataStore.data.map { prefs ->
        prefs[ACTIVE_MODEL_ID]?.let { ModelRegistry.byId(it) } ?: ModelRegistry.default
    }

    override suspend fun setActiveModel(model: ModelInfo) {
        context.dataStore.edit { prefs -> prefs[ACTIVE_MODEL_ID] = model.id }
    }
}
