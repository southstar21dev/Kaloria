package dev.southstar21.kaloria.core.sync

import dev.southstar21.kaloria.core.datastore.SyncedAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalDataPlanTest {
    private val me = SyncedAccount("https://pb.example.com", "user1", "me@example.com")

    private fun plan(synced: SyncedAccount?, signingIn: SyncedAccount, hasData: Boolean = true, hasSyncedRows: Boolean = true) = planLocalData(synced, signingIn, hasData, hasSyncedRows)

    @Test
    fun `same account on the same server syncs as usual`() {
        assertEquals(LocalDataPlan.Keep, plan(me, me))
        // Typed differently, same server; and an email changed on the server, same user.
        assertEquals(LocalDataPlan.Keep, plan(me, me.copy(serverUrl = "https://PB.example.com/")))
        assertEquals(LocalDataPlan.Keep, plan(me, me.copy(email = "new@example.com")))
    }

    @Test
    fun `same account on another server uploads everything again`() {
        assertEquals(LocalDataPlan.Reupload, plan(me, me.copy(serverUrl = "https://new.example.com", userId = "other")))
        assertEquals(LocalDataPlan.Reupload, plan(me, me.copy(serverUrl = "https://new.example.com", email = "ME@example.com")))
    }

    @Test
    fun `a reset server, where the same email has a new user id, gets everything again`() {
        assertEquals(LocalDataPlan.Reupload, plan(me, me.copy(userId = "user2")))
    }

    @Test
    fun `another account with local data present asks first`() {
        val other = SyncedAccount("https://pb.example.com", "user2", "partner@example.com")
        assertEquals(LocalDataPlan.Ask, plan(me, other))
        assertEquals(LocalDataPlan.Ask, plan(me, other.copy(serverUrl = "https://elsewhere.example.com")))
    }

    @Test
    fun `nothing to ask about when there's no local data`() {
        val other = SyncedAccount("https://pb.example.com", "user2", "partner@example.com")
        assertEquals(LocalDataPlan.Keep, plan(me, other, hasData = false))
    }

    @Test
    fun `data never synced belongs to whoever signs in first`() {
        assertEquals(LocalDataPlan.Keep, plan(null, me, hasSyncedRows = false))
    }

    @Test
    fun `synced data with no record of its account asks first`() {
        // Signed out before the app kept track of which account the data was synced with.
        assertEquals(LocalDataPlan.Ask, plan(null, me, hasSyncedRows = true))
    }

    @Test
    fun `server addresses are normalised`() {
        assertEquals("https://pb.example.com", normalizedServerUrl(" pb.example.com/ "))
        assertEquals("http://10.0.2.2:8090", normalizedServerUrl("http://10.0.2.2:8090//"))
        assertNull(normalizedServerUrl("   "))
    }
}
