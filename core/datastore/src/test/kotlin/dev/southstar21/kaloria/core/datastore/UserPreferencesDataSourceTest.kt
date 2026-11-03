package dev.southstar21.kaloria.core.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UserPreferencesDataSourceTest {
    @get:Rule val folder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After fun tearDown() = scope.cancel()

    @Test
    fun `reduce transparency defaults to off and can be toggled`() = runBlocking {
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { folder.newFile("prefs.preferences_pb").also { it.delete() } }
        val source = UserPreferencesDataSource(dataStore)

        assertEquals(UserPreferences(reduceTransparency = false), source.preferences.first())

        source.setReduceTransparency(true)
        assertEquals(UserPreferences(reduceTransparency = true), source.preferences.first())

        source.setReduceTransparency(false)
        assertEquals(UserPreferences(reduceTransparency = false), source.preferences.first())
    }
}
