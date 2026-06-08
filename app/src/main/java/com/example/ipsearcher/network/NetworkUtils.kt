package com.example.ipsearcher.network

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import com.example.ipsearcher.data.model.LocalDeviceInfo
import com.example.ipsearcher.data.model.MacResult
import com.example.ipsearcher.data.model.NetworkType
import java.net.NetworkInterface
import java.net.SocketException

/**
 * 网络工具类
 * 负责获取本机 IP、MAC 地址、网关、子网掩码、SSID 等信息
 *
 * ⚠️ 本类不依赖 HotspotDetector，避免循环依赖
 * 热点模式的检测由 ViewModel 层协调
 */
object NetworkUtils {

    private val FAKE_MAC = "02:00:00:00:00:00"

    // 常见热点网段（用于判断 IP 来源）
    private val HOTSPOT_PREFIXES = listOf(
        "192.168.43", "192.168.44", "192.168.45", "192.168.46",
        "192.168.47", "192.168.48", "192.168.144", "192.168.1", "192.168.0"
    )

    /**
     * 获取本机 IP 地址
     *
     * 优先从 NetworkInterface 遍历（热点模式也有效）
     * 回退到 WifiManager.dhcpInfo（WiFi 客户端模式）
     */
    @JvmStatic
    fun getLocalIpAddress(context: Context): String? {
        val ipFromInterface = getIpFromNetworkInterface()
        if (!ipFromInterface.isNullOrEmpty()) {
            return ipFromInterface
        }

        val wifiManager = getWifiManager(context)
        val dhcpInfo = wifiManager?.dhcpInfo ?: return null
        val ip = dhcpInfo.ipAddress
        return if (ip != 0) intToIpAddress(ip) else null
    }

    /**
     * 从 NetworkInterface 获取本机 IP 地址
     * 这个方法在热点模式下也能正确获取 IP
     */
    private fun getIpFromNetworkInterface(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            var hotspotIp: String? = null
            var wifiIp: String? = null
            var otherIp: String? = null

            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue

                val name = networkInterface.name.lowercase()
                if (name.startsWith("rmnet") || name.startsWith("ccmni")) continue
                if (name.startsWith("usb")) continue

                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr.hostAddress?.contains(":") == false) {
                        val hostAddr = addr.hostAddress!!

                        if (HOTSPOT_PREFIXES.any { prefix -> hostAddr.startsWith(prefix) }) {
                            if (name.startsWith("ap") || name == "wlan0") {
                                hotspotIp = hostAddr
                            }
                        } else {
                            if (name == "wlan0" && wifiIp == null) {
                                wifiIp = hostAddr
                            }
                            if (otherIp == null) {
                                otherIp = hostAddr
                            }
                        }
                    }
                }
            }

            hotspotIp ?: wifiIp ?: otherIp
        } catch (e: SocketException) {
            null
        }
    }

    /**
     * 获取本机 MAC 地址
     * - Android 6-9: WifiInfo.getMacAddress() 返回真实 MAC
     * - Android 10+: 返回 02:00:00:00:00:00 假值
     */
    @SuppressLint("HardwareIds")
    @JvmStatic
    fun getMacAddress(context: Context): MacResult {
        val wifiManager = getWifiManager(context)
        if (wifiManager != null) {
            try {
                val wifiInfo = wifiManager.connectionInfo
                val mac = wifiInfo.macAddress
                if (mac != null && mac != FAKE_MAC) {
                    return MacResult(mac = mac, isReal = true)
                }
            } catch (_: Exception) {}
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return MacResult(mac = FAKE_MAC, isReal = false)
        }

        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (ni.name.equals("wlan0", ignoreCase = true)) {
                    val bytes = ni.hardwareAddress ?: continue
                    val sb = StringBuilder()
                    for ((i, b) in bytes.withIndex()) {
                        sb.append(String.format("%02X:", b))
                    }
                    if (sb.isNotEmpty()) sb.deleteCharAt(sb.length - 1)
                    return MacResult(mac = sb.toString(), isReal = true)
                }
            }
            MacResult(mac = FAKE_MAC, isReal = false)
        } catch (e: Exception) {
            MacResult(mac = FAKE_MAC, isReal = false)
        }
    }

    /**
     * 获取网关地址
     *
     * ⚠️ 此方法不自动检测热点模式，由调用方传入 isHotspotMode
     * 热点模式下，本机就是网关，返回本机 IP
     */
    @JvmStatic
    fun getGatewayAddress(context: Context, isHotspotMode: Boolean = false): String {
        if (isHotspotMode) {
            // 热点模式下，手机自己就是网关
            val localIp = getLocalIpAddress(context) ?: return ""
            val lastDot = localIp.lastIndexOf('.')
            return if (lastDot > 0) {
                "${localIp.substring(0, lastDot)}.1"
            } else {
                localIp
            }
        }

        // WiFi 模式下从 DhcpInfo 获取网关
        val wifiManager = getWifiManager(context)
        val dhcpInfo = wifiManager?.dhcpInfo ?: return ""
        val gateway = dhcpInfo.gateway
        return if (gateway != 0) intToIpAddress(gateway) else ""
    }

    /**
     * 获取子网掩码
     */
    @JvmStatic
    fun getSubnetMask(context: Context): String {
        val wifiManager = getWifiManager(context)
        val dhcpInfo = wifiManager?.dhcpInfo ?: return ""
        val netmask = dhcpInfo.netmask
        return if (netmask != 0) intToIpAddress(netmask) else "255.255.255.0"
    }

    /**
     * 获取网段前缀
     * 例如 "192.168.1" → 用于扫描 192.168.1.x 网段
     */
    @JvmStatic
    fun getSubnetPrefix(context: Context): String {
        val ip = getLocalIpAddress(context) ?: return ""
        val lastDot = ip.lastIndexOf('.')
        return if (lastDot > 0) ip.substring(0, lastDot) else ""
    }

    /**
     * 获取 WiFi SSID
     * Android 9+ 需要位置权限
     */
    @Suppress("DEPRECATION")
    @JvmStatic
    fun getSSID(context: Context): String {
        val wifiManager = getWifiManager(context) ?: return ""
        return try {
            val wifiInfo = wifiManager.connectionInfo
            val ssid = wifiInfo.ssid
            if (ssid == "<unknown ssid>") "" else ssid.removeSurrounding("\"")
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 获取 WiFi 信号强度（RSSI）
     */
    @JvmStatic
    fun getSignalStrength(context: Context): Int {
        val wifiManager = getWifiManager(context) ?: return -1
        return try {
            wifiManager.connectionInfo.rssi
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * 获取信号强度的文字描述
     */
    @JvmStatic
    fun getSignalLevelDescription(rssi: Int): String {
        return when {
            rssi >= -50 -> "强"
            rssi >= -60 -> "良"
            rssi >= -70 -> "中"
            else -> "弱"
        }
    }

    /**
     * 获取当前网络类型
     *
     * ⚠️ 此方法不检测热点模式（避免循环依赖）
     * 热点检测由 HotspotDetector 独立完成
     * 返回值不会是 NetworkType.HOTSPOT
     */
    @JvmStatic
    fun getNetworkType(context: Context): NetworkType {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as? ConnectivityManager ?: return NetworkType.NONE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return NetworkType.NONE

            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.MOBILE
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.WIFI
                else -> NetworkType.NONE
            }
        } else {
            @Suppress("DEPRECATION")
            val activeNetworkInfo = connectivityManager.activeNetworkInfo
            return when (activeNetworkInfo?.type) {
                ConnectivityManager.TYPE_WIFI -> NetworkType.WIFI
                ConnectivityManager.TYPE_MOBILE -> NetworkType.MOBILE
                ConnectivityManager.TYPE_ETHERNET -> NetworkType.WIFI
                else -> NetworkType.NONE
            }
        }
    }

    /**
     * 获取完整的本机设备信息
     *
     * @param isHotspotMode 是否为热点模式（由 ViewModel 层传入）
     */
    @JvmStatic
    fun getLocalDeviceInfo(context: Context, isHotspotMode: Boolean = false): LocalDeviceInfo {
        val macResult = getMacAddress(context)
        return LocalDeviceInfo(
            ipAddress = getLocalIpAddress(context) ?: "",
            macAddress = macResult.mac,
            isMacReal = macResult.isReal,
            gateway = getGatewayAddress(context, isHotspotMode),
            subnetMask = getSubnetMask(context),
            ssid = getSSID(context),
            networkType = if (isHotspotMode) NetworkType.HOTSPOT else getNetworkType(context),
            signalStrength = getSignalStrength(context)
        )
    }

    /**
     * 获取 WifiManager 实例
     */
    private fun getWifiManager(context: Context): WifiManager? {
        return context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }

    /**
     * 将 int 类型 IP 地址转换为点分十进制字符串
     */
    private fun intToIpAddress(ip: Int): String {
        return String.format(
            "%d.%d.%d.%d",
            (ip and 0xFF),
            (ip shr 8 and 0xFF),
            (ip shr 16 and 0xFF),
            (ip shr 24 and 0xFF)
        )
    }

    /**
     * 判断 MAC 是否为系统返回的假值
     */
    @JvmStatic
    fun isMacFake(mac: String): Boolean {
        return mac == FAKE_MAC || mac.isEmpty()
    }
}
