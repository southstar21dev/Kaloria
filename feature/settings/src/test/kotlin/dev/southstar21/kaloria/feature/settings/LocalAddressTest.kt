package dev.southstar21.kaloria.feature.settings

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** IP literals and names decided by name only, so nothing here needs DNS. */
class LocalAddressTest {
    @Test
    fun `private, link-local and loopback addresses need local network access`() = runTest {
        listOf(
            "http://10.0.2.2:8090",
            "http://192.168.1.20:8090/",
            "http://172.20.0.5",
            "http://169.254.10.1",
            "http://127.0.0.1:8090",
            "http://[fd12:3456::1]:8090",
            "http://[fe80::1]",
            "http://[::1]:8090",
            "http://localhost:8090",
            "http://pb.local",
            "192.168.1.20:8090",
        ).forEach { assertTrue(it, needsLocalNetwork(it)) }
    }

    @Test
    fun `public addresses do not`() = runTest {
        listOf("https://172.32.0.1", "https://8.8.8.8", "https://[2001:4860:4860::8888]", "", "https://")
            .forEach { assertFalse(it, needsLocalNetwork(it)) }
    }

    @Test
    fun `the host is read with or without a scheme`() {
        assertEquals("pb.example.com", hostOf("pb.example.com"))
        assertEquals("pb.example.com", hostOf("https://PB.example.com:8443/base/"))
        assertEquals("fd12::1", hostOf("http://[fd12::1]:8090"))
    }
}
