package com.example.ipsearcher.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ipsearcher.data.model.*
import com.example.ipsearcher.network.ArpScanner
import com.example.ipsearcher.network.HotspotDetector
import com.example.ipsearcher.network.NetworkUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * 主 ViewModel
 * 管理本机信息和局域网扫描的 UI 状态
 *
 * ⚠️ 本类负责协调 NetworkUtils 和 HotspotDetector，避免两者互相依赖
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    // 网络层实例
    private val context: Context = application.applicationContext
    private val arpScanner = ArpScanner(context)
    private val hotspotDetector = HotspotDetector(context)

    // 缓存热点状态，避免重复检测
    private var cachedIsHotspot: Boolean? = null

    // 扫描 Job（用于取消）
    private var scanJob: Job? = null

    // ========================
    // 本机信息 StateFlow
    // ========================
    private val _deviceInfoState = MutableStateFlow(DeviceInfoUiState())
    val deviceInfoState: StateFlow<DeviceInfoUiState> = _deviceInfoState.asStateFlow()

    // ========================
    // 局域网扫描 StateFlow
    // ========================
    private val _lanScanState = MutableStateFlow(LanScanUiState())
    val lanScanState: StateFlow<LanScanUiState> = _lanScanState.asStateFlow()

    init {
        // 启动时加载本机信息
        refreshDeviceInfo()
    }

    // ========================
    // 本机信息相关
    // ========================

    /**
     * 刷新本机设备信息
     */
    fun refreshDeviceInfo() {
        viewModelScope.launch {
            _deviceInfoState.update { it.copy(isLoading = true) }

            try {
                // 先检测热点状态（由 ViewModel 协调，不放在 NetworkUtils 里）
                val isHotspot = hotspotDetector.isHotspotEnabled()
                cachedIsHotspot = isHotspot

                // 传入热点状态给 NetworkUtils
                val info = NetworkUtils.getLocalDeviceInfo(context, isHotspotMode = isHotspot)

                _deviceInfoState.update {
                    it.copy(
                        ip = info.ipAddress,
                        mac = info.macAddress,
                        isMacReal = info.isMacReal,
                        gateway = info.gateway,
                        subnetMask = info.subnetMask,
                        ssid = info.ssid,
                        networkType = info.networkType,
                        signalStrength = info.signalStrength,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _deviceInfoState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "获取设备信息失败: ${e.message}"
                    )
                }
            }
        }
    }

    // ========================
    // 局域网扫描相关
    // ========================

    /**
     * 开始扫描
     */
    fun startScan() {
        // 如果正在扫描，先停止
        scanJob?.cancel()

        // 检测网络模式（由 ViewModel 协调）
        val networkMode = hotspotDetector.detect()
        val isHotspot = networkMode == NetworkMode.HOTSPOT
        cachedIsHotspot = isHotspot

        // 检查是否有网络
        val hasNetwork = isHotspot || NetworkUtils.getNetworkType(context) != NetworkType.NONE
        if (!hasNetwork) {
            _lanScanState.update {
                it.copy(
                    scanStatus = ScanStatus.ERROR,
                    errorMessage = "请先连接 WiFi 或开启热点"
                )
            }
            return
        }

        // 检查扫描可用性
        val availability = arpScanner.checkScanAvailability()

        // 获取网段前缀
        val subnetPrefix = when (networkMode) {
            NetworkMode.HOTSPOT -> hotspotDetector.getHotspotSubnetPrefix()
            else -> NetworkUtils.getSubnetPrefix(context)
        }

        // 获取本机 IP
        val localIp = when (networkMode) {
            NetworkMode.HOTSPOT -> hotspotDetector.getHotspotLocalIp()
                ?: NetworkUtils.getLocalIpAddress(context) ?: ""
            else -> NetworkUtils.getLocalIpAddress(context) ?: ""
        }

        // 获取网关 IP（热点模式下本机就是网关）
        val gatewayIp = when (networkMode) {
            NetworkMode.HOTSPOT -> localIp
            else -> NetworkUtils.getGatewayAddress(context, isHotspotMode = false)
        }

        // 更新扫描信息
        _lanScanState.update {
            it.copy(
                scanStatus = ScanStatus.SCANNING,
                networkMode = networkMode,
                subnetPrefix = subnetPrefix,
                totalHosts = 254,
                scannedCount = 0,
                devices = emptyList(),
                errorMessage = null,
                scanAvailability = availability
            )
        }

        // 启动扫描协程
        scanJob = viewModelScope.launch {
            try {
                val result = arpScanner.scan(
                    subnetPrefix = subnetPrefix,
                    localIp = localIp,
                    gatewayIp = gatewayIp,
                    isHotspotMode = isHotspot
                ) { scanned, total ->
                    _lanScanState.update { state ->
                        state.copy(scannedCount = scanned)
                    }
                }

                _lanScanState.update {
                    it.copy(
                        scanStatus = ScanStatus.COMPLETED,
                        devices = result.devices,
                        isScanPartial = result.isPartial,
                        errorMessage = if (result.isPartial) {
                            if (result.devices.any { d -> !d.isMacAvailable }) {
                                "当前系统版本限制，无法获取 MAC 地址，仅显示设备 IP"
                            } else {
                                "此 Android 版本限制局域网扫描，仅显示已缓存的设备信息"
                            }
                        } else {
                            null
                        }
                    )
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    _lanScanState.update { state ->
                        state.copy(
                            scanStatus = ScanStatus.COMPLETED,
                            errorMessage = null
                        )
                    }
                } else {
                    _lanScanState.update {
                        it.copy(
                            scanStatus = ScanStatus.ERROR,
                            errorMessage = "扫描失败: ${e.message}"
                        )
                    }
                }
            }
        }
    }

    /**
     * 停止扫描
     */
    fun stopScan() {
        scanJob?.cancel()
        scanJob = null

        _lanScanState.update {
            it.copy(scanStatus = ScanStatus.COMPLETED)
        }
    }

    /**
     * Tab 切换回调
     */
    fun onTabChanged(tabRoute: String) {
        if (tabRoute == "lan_scan") {
            val networkMode = hotspotDetector.detect()
            val subnetPrefix = when (networkMode) {
                NetworkMode.HOTSPOT -> hotspotDetector.getHotspotSubnetPrefix()
                else -> NetworkUtils.getSubnetPrefix(context)
            }

            _lanScanState.update {
                it.copy(
                    networkMode = networkMode,
                    subnetPrefix = subnetPrefix
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        scanJob?.cancel()
    }
}
