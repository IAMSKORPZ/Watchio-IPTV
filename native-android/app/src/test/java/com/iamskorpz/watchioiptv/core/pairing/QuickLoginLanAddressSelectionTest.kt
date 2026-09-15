package com.iamskorpz.watchioiptv.core.pairing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.InetAddress

class QuickLoginLanAddressSelectionTest {
    @Test
    fun `wlan private IPv4 wins over dummy link-local IPv6`() {
        assertSelected("192.168.1.49", candidate("dummy0", "fe80::1234"), candidate("wlan0", "192.168.1.49"))
    }

    @Test
    fun `ethernet private IPv4 wins over dummy link-local IPv6`() {
        assertSelected("10.0.0.25", candidate("dummy0", "fe80::1234"), candidate("eth0", "10.0.0.25"))
    }

    @Test
    fun `loopback is rejected when wlan is available`() {
        assertSelected("192.168.1.49", candidate("lo", "127.0.0.1", loopback = true), candidate("wlan0", "192.168.1.49"))
    }

    @Test
    fun `IPv4 link-local is rejected when private IPv4 is available`() {
        assertSelected("192.168.1.49", candidate("eth1", "169.254.10.20"), candidate("wlan0", "192.168.1.49"))
    }

    @Test
    fun `dummy private-looking IPv4 is rejected`() {
        assertSelected("192.168.1.49", candidate("dummy0", "10.0.0.9"), candidate("wlan0", "192.168.1.49"))
    }

    @Test
    fun `only link-local IPv6 has no usable address`() {
        assertNull(selectLanAddress(listOf(candidate("dummy0", "fe80::1234"), candidate("wlan0", "fe80::5678"))))
    }

    @Test
    fun `172 private range is accepted`() {
        assertSelected("172.31.4.5", candidate("eth0", "172.31.4.5"))
    }

    @Test
    fun `192 private range is accepted`() {
        assertSelected("192.168.20.5", candidate("wlan0", "192.168.20.5"))
    }

    @Test
    fun `10 private range is accepted`() {
        assertSelected("10.20.30.40", candidate("wlan0", "10.20.30.40"))
    }

    @Test
    fun `private IPv4 wins over global IPv4`() {
        assertSelected("10.0.0.25", candidate("wlan0", "8.8.8.8"), candidate("eth0", "10.0.0.25"))
    }

    @Test
    fun `global IPv4 is fallback when no private IPv4 exists`() {
        assertSelected("8.8.8.8", candidate("wlan0", "8.8.8.8"))
    }

    @Test
    fun `inactive and virtual interfaces are rejected`() {
        assertNull(
            selectLanAddress(
                listOf(
                    candidate("wlan0", "192.168.1.49", up = false),
                    candidate("tun0", "10.0.0.2", virtual = true),
                ),
            ),
        )
    }

    private fun assertSelected(expected: String, vararg candidates: LanAddressCandidate) {
        assertEquals(expected, selectLanAddress(candidates.toList())?.address?.hostAddress)
    }

    private fun candidate(
        name: String,
        address: String,
        up: Boolean = true,
        loopback: Boolean = false,
        virtual: Boolean = false,
    ) = LanAddressCandidate(name, up, loopback, virtual, InetAddress.getByName(address))
}
