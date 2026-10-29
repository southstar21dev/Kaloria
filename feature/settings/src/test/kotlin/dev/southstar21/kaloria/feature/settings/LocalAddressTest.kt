package dev.southstar21.kaloria.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAddressTest {
    @Test
    fun `private and loopback hosts need local network access`() {
        listOf("http://10.0.2.2:8090", "http://192.168.1.20:8090/", "http://172.20.0.5", "http://localhost:8090", "http://pb.local")
            .forEach { assertTrue(it, isLocalAddress(it)) }
    }

    @Test
    fun `public hosts do not`() {
        listOf("https://apikaloria.southstar21.dev", "https://172.32.0.1", "https://8.8.8.8", "apikaloria.southstar21.dev")
            .forEach { assertFalse(it, isLocalAddress(it)) }
    }
}
