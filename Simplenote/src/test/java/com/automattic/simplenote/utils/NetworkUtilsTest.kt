package com.automattic.simplenote.utils

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo
import org.robolectric.shadows.ShadowWifiInfo
import java.util.Locale

/**
 * Pins NetworkUtils against a shadowed ConnectivityManager / WifiManager: the null-context and
 * no-active-network paths (TYPE_NULL), the disconnected path (TYPE_NONE), the per-transport speed
 * strings, and the speed formatting: unit index = (int) (log10(n) / log10(1024)), two decimals with
 * default-locale grouping, and "0.00" without a unit for non-positive speeds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
@Suppress("DEPRECATION")
class NetworkUtilsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private lateinit var defaultLocale: Locale

    @Before
    fun setUp() {
        defaultLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun nullContextReportsUnknownNetwork() {
        assertEquals(TYPE_NULL, NetworkUtils.getNetworkType(null))
        assertEquals("Could not get network type", NetworkUtils.getNetworkTypeString(null))
        assertEquals("Could not get network type (?)", NetworkUtils.getNetworkInfo(null))
        assertFalse(NetworkUtils.isNetworkAvailable(null))
    }

    @Test
    fun noActiveNetworkReportsUnknownNetwork() {
        shadowOf(connectivityManager).setActiveNetworkInfo(null)

        assertEquals(TYPE_NULL, NetworkUtils.getNetworkType(context))
        assertEquals("Could not get network type", NetworkUtils.getNetworkTypeString(context))
        assertEquals("Could not get network type (?)", NetworkUtils.getNetworkInfo(context))
        assertFalse(NetworkUtils.isNetworkAvailable(context))
    }

    @Test
    fun disconnectedNetworkReportsNoConnection() {
        setActiveNetwork(ConnectivityManager.TYPE_WIFI, connected = false)

        assertEquals(TYPE_NONE, NetworkUtils.getNetworkType(context))
        assertEquals("No network connection", NetworkUtils.getNetworkTypeString(context))
        assertEquals("No network connection (?)", NetworkUtils.getNetworkInfo(context))
        assertFalse(NetworkUtils.isNetworkAvailable(context))
    }

    @Test
    fun connectedWifiReportsWifiLinkSpeed() {
        setActiveNetwork(ConnectivityManager.TYPE_WIFI, connected = true)
        setWifiLinkSpeed(54)

        assertEquals(ConnectivityManager.TYPE_WIFI, NetworkUtils.getNetworkType(context))
        assertEquals("WIFI", NetworkUtils.getNetworkTypeString(context))
        assertEquals("WIFI (54.00 Mbps)", NetworkUtils.getNetworkInfo(context))
        assertTrue(NetworkUtils.isNetworkAvailable(context))
    }

    @Test
    fun connectedMobileReportsDownstreamBandwidth() {
        setActiveNetwork(ConnectivityManager.TYPE_MOBILE, connected = true)
        setDownstreamKbps(12345)

        assertEquals(ConnectivityManager.TYPE_MOBILE, NetworkUtils.getNetworkType(context))
        assertEquals("MOBILE", NetworkUtils.getNetworkTypeString(context))
        assertEquals("MOBILE (12.06 Mbps)", NetworkUtils.getNetworkInfo(context))
        assertTrue(NetworkUtils.isNetworkAvailable(context))
    }

    @Test
    fun connectedMobileWithoutCapabilitiesCannotReportSpeed() {
        setActiveNetwork(ConnectivityManager.TYPE_MOBILE, connected = true)
        // No active Network object, so getNetworkCapabilities(null) has nothing to return.
        shadowOf(connectivityManager).setDefaultNetworkActive(false)

        assertEquals("MOBILE (Could not get network speed)", NetworkUtils.getNetworkInfo(context))
        assertEquals("Could not get network speed", NetworkUtils.getNetworkSpeed(context))
    }

    @Test
    fun connectedOtherTransportIsAvailableButUnnamed() {
        setActiveNetwork(ConnectivityManager.TYPE_ETHERNET, connected = true)

        assertEquals(ConnectivityManager.TYPE_ETHERNET, NetworkUtils.getNetworkType(context))
        assertEquals("Could not get network type", NetworkUtils.getNetworkTypeString(context))
        assertEquals("Could not get network type (?)", NetworkUtils.getNetworkInfo(context))
        assertTrue(NetworkUtils.isNetworkAvailable(context))
    }

    @Test
    fun wifiSpeedFormatting() {
        assertWifiSpeed(0, "0.00")
        assertWifiSpeed(-1, "0.00")
        assertWifiSpeed(1, "1.00 Mbps")
        assertWifiSpeed(1023, "1,023.00 Mbps")
        assertWifiSpeed(1024, "1.00 Gbps")
        assertWifiSpeed(1536, "1.50 Gbps")
    }

    @Test
    fun mobileSpeedFormatting() {
        setActiveNetwork(ConnectivityManager.TYPE_MOBILE, connected = true)

        assertMobileSpeed(0, "0.00")
        assertMobileSpeed(-5, "0.00")
        assertMobileSpeed(500, "500.00 Kbps")
        assertMobileSpeed(1024, "1.00 Mbps")
        assertMobileSpeed(1536, "1.50 Mbps")
        assertMobileSpeed(1048576, "1.00 Gbps")
    }

    @Test
    fun speedFormattingFollowsDefaultLocale() {
        Locale.setDefault(Locale.GERMANY)

        assertWifiSpeed(1536, "1,50 Gbps")
        assertWifiSpeed(1023, "1.023,00 Mbps")
    }

    private fun assertWifiSpeed(linkSpeed: Int, expected: String) {
        setWifiLinkSpeed(linkSpeed)
        assertEquals(expected, NetworkUtils.getNetworkSpeedWifi(context))
    }

    private fun assertMobileSpeed(kbps: Int, expected: String) {
        setDownstreamKbps(kbps)
        assertEquals(expected, NetworkUtils.getNetworkSpeed(context))
    }

    private fun setActiveNetwork(type: Int, connected: Boolean) {
        val info = ShadowNetworkInfo.newInstance(
            if (connected) NetworkInfo.DetailedState.CONNECTED else NetworkInfo.DetailedState.DISCONNECTED,
            type,
            0,
            true,
            if (connected) NetworkInfo.State.CONNECTED else NetworkInfo.State.DISCONNECTED
        )
        shadowOf(connectivityManager).setActiveNetworkInfo(info)
    }

    private fun setWifiLinkSpeed(linkSpeed: Int) {
        val wifiInfo = ShadowWifiInfo.newInstance()
        shadowOf(wifiInfo).setLinkSpeed(linkSpeed)
        shadowOf(wifiManager).setConnectionInfo(wifiInfo)
    }

    private fun setDownstreamKbps(kbps: Int) {
        val capabilities = ShadowNetworkCapabilities.newInstance()
        shadowOf(capabilities).setLinkDownstreamBandwidthKbps(kbps)
        shadowOf(connectivityManager).setNetworkCapabilities(connectivityManager.activeNetwork, capabilities)
    }

    private companion object {
        // NetworkUtils' private TYPE_NULL / TYPE_NONE sentinels.
        const val TYPE_NULL = -2
        const val TYPE_NONE = -1
    }
}
