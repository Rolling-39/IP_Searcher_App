package com.example.ipsearcher.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import com.example.ipsearcher.data.model.NetworkMode
import java.lang.reflect.Method
import java.net.NetworkInterface

/**
 * 热点检测器
 *
 * 判断当前手机是否开启了 WiFi 热点，以及确定网络模式。
 *
 * ⚠️ 本类不依赖 NetworkUtils，避免循环依赖导致 StackOverflow
 *
 * 兼容策略（按优先级）：
 * 1. 反射调用 ConnectivityManager.getTetheredInterfaces() — 最可靠
 * 2. 反射调用 WifiManager.isWifiApEnabled() / isApEnabled() — 旧版兼容
 * 3. 推断法：检查网络接口 IP 是否在热点网段 — 兜底方案
 */
class HotspotDetector(private val context: Context) {

    companion object {
        /** 常见热点网段前缀 */
        val HOTSPOT_PREFIXES = listOf(
            "192.168.43",   // AOSP 默认
            "192.168.44",   // 部分三星设备
            "192.168.45",   // 部分设备
            "192.168.46",   // 部分设备
            "192.168.47",   // 部分设备
            "192.168.48",   // 部分设备
            "192.168.144",  // 部分国产设备
            "192.168.1",    // 部分定制 ROM
            "192.168.0"     // 部分定制 ROM
        )
        private const val TAG = "HotspotDetector"
    }

    private val wifiManager: WifiManager? by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }

    /**
     * 检测当前网络模式
     *
     * @return NetworkMode.WIFI_ROUTER / NetworkMode.HOTSPOT / NetworkMode.UNKNOWN
     */
    fun detect(): NetworkMode {
        if (isHotspotEnabled()) {
            return NetworkMode.HOTSPOT
        }

        // 直接检查 ConnectivityManager，不调用 NetworkUtils 避免循环依赖
        val isWifiConnected = checkWifiConnected()
        return if (isWifiConnected) NetworkMode.WIFI_ROUTER else NetworkMode.UNKNOWN
    }

    /**
     * 判断当前是否开启了 WiFi 热点
     *
     * 多策略尝试（按可靠性排序）：
     * 1. ConnectivityManager.getTetheredInterfaces() — 官方隐藏 API
     * 2. WifiManager.isApEnabled() — 较新的隐藏 API
     * 3. WifiManager.isWifiApEnabled() — 旧版隐藏 API
     * 4. 推断法：检查本机 IP 是否在热点网段 — 兜底
     */
    fun isHotspotEnabled(): Boolean {
        // 策略 1: 通过 ConnectivityManager 检查 tethered 接口（最可靠）
        if (checkTetheredInterfaces()) return true

        // 策略 2: 反射调用 isApEnabled (较新的 API 名称)
        if (checkIsApEnabled()) return true

        // 策略 3: 反射调用 isWifiApEnabled (旧版 API 名称)
        if (checkIsWifiApEnabled()) return true

        // 策略 4: 推断法 — 如果 WiFi 没有连接但本机有热点网段 IP
        if (inferHotspotFromNetworkInterface()) return true

        return false
    }

    /**
     * 策略 1: 通过 ConnectivityManager 检查是否有 tethered 接口
     */
    private fun checkTetheredInterfaces(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) ?: return false

            // 方法 A: getTetheredInterfaces() — 返回正在共享的接口列表
            try {
                val method = cm.javaClass.getMethod("getTetheredInterfaces")
                val interfaces = method.invoke(cm) as? Array<*>?
                if (interfaces != null && interfaces.isNotEmpty()) {
                    return true
                }
            } catch (_: Exception) {}

            false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 策略 2: 反射调用 isApEnabled
     */
    private fun checkIsApEnabled(): Boolean {
        val wm = wifiManager ?: return false
        return try {
            val method: Method = wm.javaClass.getMethod("isApEnabled")
            val result = method.invoke(wm) as? Boolean
            result == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 策略 3: 反射调用 isWifiApEnabled
     */
    private fun checkIsWifiApEnabled(): Boolean {
        val wm = wifiManager ?: return false
        return try {
            val method: Method = wm.javaClass.getMethod("isWifiApEnabled")
            val result = method.invoke(wm) as? Boolean
            result == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 策略 4: 推断法
     * 如果 WiFi 没有连接到路由器，但本机有热点网段的 IP，则推断热点已开启
     *
     * ⚠️ 直接检查 ConnectivityManager，不调用 NetworkUtils 避免循环依赖
     */
    private fun inferHotspotFromNetworkInterface(): Boolean {
        return try {
            // 直接检查 WiFi 是否连接，不调用 NetworkUtils
            val wifiConnected = checkWifiConnected()
            if (wifiConnected) return false  // WiFi 已连接路由器，不是热点模式

            // 检查本机是否有热点网段的 IP
            val hotspotIp = getHotspotIpFromNetworkInterface()
            hotspotIp != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 直接检查 WiFi 是否连接（不依赖 NetworkUtils，避免循环依赖）
     */
    private fun checkWifiConnected(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as? ConnectivityManager ?: return false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = cm.activeNetwork ?: return false
                val capabilities = cm.getNetworkCapabilities(network) ?: return false
                capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
            } else {
                @Suppress("DEPRECATION")
                val info = cm.activeNetworkInfo
                info?.type == ConnectivityManager.TYPE_WIFI && info.isConnected
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 获取热点网段前缀
     *
     * 策略：
     * 1. 从 NetworkInterface 获取热点接口 IP（最可靠）
     * 2. 从 WifiManager.dhcpInfo 获取（WiFi 客户端模式回退）
     * 3. 使用默认值 192.168.43
     */
    fun getHotspotSubnetPrefix(): String {
        // 优先策略：从网络接口获取热点 IP
        val hotspotIp = getHotspotIpFromNetworkInterface()
        if (hotspotIp != null) {
            val lastDot = hotspotIp.lastIndexOf('.')
            if (lastDot > 0) {
                return hotspotIp.substring(0, lastDot)
            }
        }

        // 次选：从 WifiManager 获取
        val wm = wifiManager
        if (wm != null) {
            try {
                val dhcpInfo = wm.dhcpInfo
                val ip = dhcpInfo.ipAddress
                if (ip != 0) {
                    val ipStr = intToIpAddress(ip)
                    // 检查是否在热点网段
                    if (HOTSPOT_PREFIXES.any { prefix -> ipStr.startsWith(prefix) }) {
                        val lastDot = ipStr.lastIndexOf('.')
                        if (lastDot > 0) return ipStr.substring(0, lastDot)
                    }
                }
            } catch (_: Exception) {}
        }

        // 兜底：使用默认热点网段
        return HOTSPOT_PREFIXES[0]
    }

    /**
     * 从 NetworkInterface 中获取热点模式的 IP 地址
     */
    fun getHotspotIpFromNetworkInterface(): String? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (ni.isLoopback || !ni.isUp) continue
                if (ni.name.startsWith("rmnet", ignoreCase = true)) continue
                if (ni.name.startsWith("ccmni", ignoreCase = true)) continue
                if (ni.name.startsWith("usb", ignoreCase = true)) continue

                val addresses = ni.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr.isLoopbackAddress) continue
                    val hostAddress = addr.hostAddress ?: continue
                    if (hostAddress.contains(":")) continue

                    if (HOTSPOT_PREFIXES.any { prefix -> hostAddress.startsWith(prefix) }) {
                        return hostAddress
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 获取热点模式下的本机 IP
     */
    fun getHotspotLocalIp(): String? {
        return getHotspotIpFromNetworkInterface()
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
}
