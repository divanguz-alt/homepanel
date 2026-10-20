package hr.divanguz.homepanel.ha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HaHelpersTest {
    @Test fun onlyLocalHostsCountAsLocal() {
        listOf("192.168.1.10", "10.0.0.5", "172.20.1.1", "100.101.102.103", "homeassistant", "ha.local", "localhost")
            .forEach { assertTrue(it, Lan.isLocal(it)) }
        listOf("8.8.8.8", "example.com", "172.32.0.1", "100.128.0.1", "my.ha.duckdns.org")
            .forEach { assertFalse(it, Lan.isLocal(it)) }
    }

    @Test fun splitsAddresses() {
        assertEquals("Main Street 1" to "Springfield", splitAddress("Main Street 1, 12345 Springfield, Country"))
        assertEquals("Main Street 1" to "Springfield", splitAddress("Main Street 1, Springfield"))
        assertEquals("Somewhere" to "", splitAddress("Somewhere"))
    }
}
