package dev.southstar21.kaloria.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class UserPreferences(
    /** Solid surfaces instead of blurred glass, for legibility or slower GPUs. */
    val reduceTransparency: Boolean = false,
)

@Singleton
class UserPreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val preferences: Flow<UserPreferences> = dataStore.data.map { prefs ->
        UserPreferences(
            reduceTransparency = prefs[REDUCE_TRANSPARENCY] ?: false,
        )
    }

    suspend fun setReduceTransparency(enabled: Boolean) {
        dataStore.edit { it[REDUCE_TRANSPARENCY] = enabled }
    }

    private companion object {
        val REDUCE_TRANSPARENCY = booleanPreferencesKey("reduce_transparency")
    }
}

private val Context.userPreferences by preferencesDataStore("user_preferences")

@Module
@InstallIn(SingletonComponent::class)
internal object DataStoreModule {
    @Provides
    @Singleton
    fun userPreferences(@ApplicationContext context: Context): DataStore<Preferences> = context.userPreferences
}
