package com.example.ipsearcher.data.model

/**
 * 网络类型枚举
 */
enum class NetworkType {
    NONE,       // 无网络连接
    WIFI,       // WiFi 已连接（连接路由器）
    MOBILE,     // 移动数据
    HOTSPOT     // 热点已开启
}

/**
 * 本机设备信息
 */
data class LocalDeviceInfo(
    val ipAddress: String = "",
    val macAddress: String = "",
    val isMacReal: Boolean = false,
    val gateway: String = "",
    val subnetMask: String = "",
    val ssid: String = "",
    val networkType: NetworkType = NetworkType.NONE,
    val signalStrength: Int = -1
)

/**
 * 扫描到的局域网设备
 */
data class ScannedDevice(
    val ipAddress: String,
    val macAddress: String? = null,
    val isMacAvailable: Boolean = true,
    val deviceType: DeviceType = DeviceType.NORMAL,
    val hostname: String? = null
)

/**
 * 设备类型标识
 */
enum class DeviceType {
    LOCAL,      // 本机
    GATEWAY,    // 网关（路由器）
    NORMAL      // 其他设备
}

/**
 * 网络模式（用于扫描）
 */
enum class NetworkMode {
    WIFI_ROUTER,    // WiFi 连接路由器
    HOTSPOT,        // 手机热点模式
    UNKNOWN         // 未知
}

/**
 * 扫描状态
 */
enum class ScanStatus {
    IDLE,       // 空闲
    SCANNING,   // 扫描中
    COMPLETED,  // 扫描完成
    ERROR       // 错误
}

/**
 * 扫描可用性
 */
enum class ScanAvailability {
    FULL,           // 完全可用
    PARTIAL,        // 部分可用（Android 13+ 限制）
    UNAVAILABLE     // 不可用
}

/**
 * MAC 地址获取结果
 */
data class MacResult(
    val mac: String,
    val isReal: Boolean
)

/**
 * 扫描结果
 */
data class ScanResult(
    val devices: List<ScannedDevice>,
    val isPartial: Boolean = false,
    val availability: ScanAvailability = ScanAvailability.FULL
)

/**
 * ViewModel UI 状态 - 本机信息
 */
data class DeviceInfoUiState(
    val ip: String = "",
    val mac: String = "",
    val isMacReal: Boolean = false,
    val gateway: String = "",
    val subnetMask: String = "",
    val ssid: String = "",
    val networkType: NetworkType = NetworkType.NONE,
    val signalStrength: Int = -1,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * ViewModel UI 状态 - 局域网扫描
 */
data class LanScanUiState(
    val scanStatus: ScanStatus = ScanStatus.IDLE,
    val networkMode: NetworkMode = NetworkMode.UNKNOWN,
    val subnetPrefix: String = "",
    val totalHosts: Int = 0,
    val scannedCount: Int = 0,
    val devices: List<ScannedDevice> = emptyList(),
    val errorMessage: String? = null,
    val isScanPartial: Boolean = false,
    val scanAvailability: ScanAvailability = ScanAvailability.FULL
)
