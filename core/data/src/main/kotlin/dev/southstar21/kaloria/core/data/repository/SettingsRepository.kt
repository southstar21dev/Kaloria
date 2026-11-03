package dev.southstar21.kaloria.core.data.repository

import dev.southstar21.kaloria.core.datastore.UserPreferences
import dev.southstar21.kaloria.core.datastore.UserPreferencesDataSource
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class SettingsRepository @Inject constructor(private val source: UserPreferencesDataSource) {
    val preferences: Flow<UserPreferences> = source.preferences
    suspend fun setReduceTransparency(enabled: Boolean) = source.setReduceTransparency(enabled)
}
