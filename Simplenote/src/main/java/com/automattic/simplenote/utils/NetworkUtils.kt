package com.automattic.simplenote.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.wifi.WifiManager
import java.text.DecimalFormat
import kotlin.math.log10
import kotlin.math.pow

object NetworkUtils {
    private const val TYPE_NONE = -1
    private const val TYPE_NULL = -2

    /**
     * @return [String] formatting speed of network in bps (e.g. 12.34 Kbps)
     */
    private fun formatSpeedFromKbps(speed: Int): String {
        if (speed <= 0) {
            return "0.00"
        }

        val units = arrayOf("Kbps", "Mbps", "Gbps", "Tbps")
        val index = (log10(speed.toDouble()) / log10(1024.0)).toInt()
        return DecimalFormat("#,##0.00").format(speed / 1024.0.pow(index)) + " " + units[index]
    }

    /**
     * @return [String] formatting speed of network in bps (e.g. 12.34 Mbps)
     */
    private fun formatSpeedFromMbps(speed: Int): String {
        if (speed <= 0) {
            return "0.00"
        }

        val units = arrayOf("Mbps", "Gbps", "Tbps")
        val index = (log10(speed.toDouble()) / log10(1024.0)).toInt()
        return DecimalFormat("#,##0.00").format(speed / 1024.0.pow(index)) + " " + units[index]
    }

    /**
     * @return [NetworkInfo] on the active network connection
     */
    private fun getActiveNetworkInfo(context: Context?): NetworkInfo? {
        if (context == null) {
            return null
        }

        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
        return cm?.activeNetworkInfo
    }

    /**
     * @return [String] type and speed of network (e.g. WIFI (123.45 Mbps))
     */
    @JvmStatic
    fun getNetworkInfo(context: Context?): String {
        val type = getNetworkTypeString(context)
        // A null context resolves to TYPE_NULL, so the speed branches always have a context.
        val speed = when (getNetworkType(context)) {
            ConnectivityManager.TYPE_MOBILE -> getNetworkSpeed(context!!)
            ConnectivityManager.TYPE_WIFI -> getNetworkSpeedWifi(context!!)
            else -> "?"
        }

        return "$type ($speed)"
    }

    /**
     * @return [String] speed of network in Kbps (e.g. 12.34)
     */
    @JvmStatic
    fun getNetworkSpeed(context: Context): String {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager?
            ?: return "Could not get network speed"

        val nc = cm.getNetworkCapabilities(cm.activeNetwork)
            ?: return "Could not get network speed"

        return formatSpeedFromKbps(nc.linkDownstreamBandwidthKbps)
    }

    /**
     * @return [String] speed of network in Mbps (e.g. 12.34)
     */
    @JvmStatic
    fun getNetworkSpeedWifi(context: Context): String {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager?
            ?: return "Could not get network speed"

        return formatSpeedFromMbps(wm.connectionInfo.linkSpeed)
    }

    /**
     * @return integer constant of the network type
     */
    @JvmStatic
    fun getNetworkType(context: Context?): Int {
        val info = getActiveNetworkInfo(context) ?: return TYPE_NULL

        if (!info.isConnected) {
            return TYPE_NONE
        }

        return info.type
    }

    /**
     * @return [String] representation of the network type
     */
    @JvmStatic
    fun getNetworkTypeString(context: Context?): String {
        return when (getNetworkType(context)) {
            ConnectivityManager.TYPE_MOBILE -> "MOBILE"
            ConnectivityManager.TYPE_WIFI -> "WIFI"
            TYPE_NONE -> "No network connection"
            else -> "Could not get network type"
        }
    }

    /**
     * @return true if a network connection is available; false otherwise
     */
    @JvmStatic
    fun isNetworkAvailable(context: Context?): Boolean {
        val info = getActiveNetworkInfo(context)
        return info != null && info.isConnected
    }
}
