package com.example.ipsearcher.network

import android.content.Context
import android.os.Build
import com.example.ipsearcher.data.model.ScanAvailability
import com.example.ipsearcher.data.model.ScanResult
import com.example.ipsearcher.data.model.ScannedDevice
import com.example.ipsearcher.data.model.DeviceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * 网络扫描引擎
 *
 * 扫描策略（按优先级自动选择）：
 *
 * 【模式 A】ARP 扫描（Android 8-12，最优）
 *   Phase 1: 发送 UDP 广播包触发 ARP 表更新
 *   Phase 2: 读取 ARP 表获取 IP-MAC 映射
 *
 * 【模式 B】Ping 扫描（Android 13+ 降级方案 / 热点模式）
 *   Phase 1: 使用系统 ping 命令逐 IP 探测
 *   Phase 2: 收集有响应的 IP 地址（无 MAC）
 *
 * 版本适配：
 * - Android < 10: /proc/net/arp 完全可用 → 模式 A
 * - Android 10-12: /proc/net/arp + ip neigh → 模式 A
 * - Android 13+: ARP 不可用 → 自动降级为模式 B（仅 IP，无 MAC）
 * - 热点模式: 优先使用模式 B（系统 ping，穿透性更强）
 */
class ArpScanner(private val context: Context) {

    companion object {
        private const val UDP_PORT = 9        // Discard 协议端口
        private const val UDP_DATA = "IPScannerProbe"
        private const val ARP_WAIT_MS = 800L   // 等待 ARP 表更新的时间
        private const val BATCH_SIZE = 50      // UDP 发送批次大小
        private const val BATCH_DELAY_MS = 30L // 批次间延迟
        private val FAKE_MAC = "02:00:00:00:00:00"

        // Ping 扫描参数
        private const val PING_TIMEOUT_MS = 500L  // 单个 Ping 超时（毫秒）
        private const val PING_PARALLEL = 32       // 并发 Ping 数量
        private const val PING_TOTAL_TIMEOUT_S = 30L  // 总扫描时限（秒）
    }

    /**
     * 执行局域网扫描
     * 自动根据 Android 版本和网络模式选择扫描策略
     *
     * @param subnetPrefix 网段前缀，如 "192.168.1"
     * @param localIp 本机 IP（用于标记）
     * @param gatewayIp 网关 IP（用于标记）
     * @param isHotspotMode 是否为热点模式
     * @param onProgress 进度回调 (已扫描数, 总数)
     * @return 扫描结果
     */
    suspend fun scan(
        subnetPrefix: String,
        localIp: String = "",
        gatewayIp: String = "",
        isHotspotMode: Boolean = false,
        onProgress: (Int, Int) -> Unit
    ): ScanResult = withContext(Dispatchers.IO) {

        val totalHosts = 254
        val availability = checkScanAvailability()

        // 热点模式：直接使用 Ping 扫描（ARP 在热点侧不可靠）
        if (isHotspotMode) {
            return@withContext pingScan(subnetPrefix, localIp, gatewayIp, totalHosts, onProgress, availability)
        }

        if (availability == ScanAvailability.FULL || availability == ScanAvailability.PARTIAL) {
            // 模式 A：ARP 扫描（优先）
            val result = arpScan(subnetPrefix, localIp, gatewayIp, totalHosts, onProgress, availability)

            // 如果 ARP 扫描发现了设备，直接返回
            if (result.devices.isNotEmpty()) {
                return@withContext result
            }
        }

        // 模式 B：Ping 扫描（降级方案，或 ARP 扫描无结果时使用）
        pingScan(subnetPrefix, localIp, gatewayIp, totalHosts, onProgress, availability)
    }

    // ========================
    // 模式 A：ARP 扫描
    // ========================

    /**
     * ARP 扫描：UDP 广播 + ARP 表读取
     */
    private suspend fun arpScan(
        subnetPrefix: String,
        localIp: String,
        gatewayIp: String,
        totalHosts: Int,
        onProgress: (Int, Int) -> Unit,
        availability: ScanAvailability
    ): ScanResult {
        // Phase 1: UDP 广播探测
        sendUdpProbe(subnetPrefix, totalHosts, onProgress)

        // Phase 2: 等待 ARP 表更新
        if (currentCoroutineContext().isActive) {
            Thread.sleep(ARP_WAIT_MS)
        }

        // Phase 3: 读取 ARP 表
        val arpTable = if (currentCoroutineContext().isActive) readArpTable() else emptyMap()

        // Phase 4: 构建设备列表
        val devices = buildDeviceList(arpTable, subnetPrefix, localIp, gatewayIp)

        return ScanResult(
            devices = devices,
            isPartial = availability != ScanAvailability.FULL,
            availability = availability
        )
    }

    /**
     * Phase 1: 发送 UDP 广播探测
     * 遍历网段所有 IP，向每个 IP 发送 UDP 包以触发 ARP 请求
     */
    private suspend fun sendUdpProbe(
        subnetPrefix: String,
        totalHosts: Int,
        onProgress: (Int, Int) -> Unit
    ) = withContext(Dispatchers.IO) {
        val socket = DatagramSocket()
        socket.soTimeout = 100

        try {
            // 首先发送广播包
            sendBroadcastProbe(socket)

            // 然后逐个 IP 发送探测包（分批发送，避免网络拥塞）
            var scanned = 0
            for (i in 1..totalHosts) {
                if (!isActive) break

                val targetIp = "$subnetPrefix.$i"
                sendUdpPacket(socket, targetIp)
                scanned++

                // 每批次报告一次进度
                if (i % BATCH_SIZE == 0) {
                    onProgress(scanned, totalHosts)
                    Thread.sleep(BATCH_DELAY_MS)
                }
            }
            onProgress(scanned, totalHosts)
        } catch (_: Exception) {
            // UDP 发送失败不影响后续扫描
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    /**
     * 发送广播探测包
     */
    private fun sendBroadcastProbe(socket: DatagramSocket) {
        try {
            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            val data = UDP_DATA.toByteArray()
            val packet = DatagramPacket(data, data.size, broadcastAddr, UDP_PORT)
            socket.send(packet)
        } catch (_: Exception) {
            // 忽略
        }
    }

    /**
     * 发送单个 UDP 探测包
     */
    private fun sendUdpPacket(socket: DatagramSocket, targetIp: String) {
        try {
            val addr = InetAddress.getByName(targetIp)
            val data = UDP_DATA.toByteArray()
            val packet = DatagramPacket(data, data.size, addr, UDP_PORT)
            socket.send(packet)
        } catch (_: Exception) {
            // 忽略单个 IP 的发送失败
        }
    }

    /**
     * 读取 ARP 表
     * 根据系统版本选择不同的读取方式
     */
    private fun readArpTable(): Map<String, String> {
        val result = mutableMapOf<String, String>()

        // 方法 1: 尝试 /proc/net/arp
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val arpEntries = parseProcNetArp()
            if (arpEntries.isNotEmpty()) {
                result.putAll(arpEntries)
                return result
            }
        }

        // 方法 2: 尝试 ip neigh 命令
        val neighEntries = parseIpNeigh()
        if (neighEntries.isNotEmpty()) {
            result.putAll(neighEntries)
        }

        // 如果 ip neigh 也没数据，再尝试 /proc/net/arp（作为回退）
        if (result.isEmpty()) {
            val arpEntries = parseProcNetArp()
            result.putAll(arpEntries)
        }

        return result
    }

    /**
     * 解析 /proc/net/arp 文件
     */
    private fun parseProcNetArp(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val reader = BufferedReader(
                InputStreamReader(java.io.FileInputStream("/proc/net/arp"))
            )
            reader.useLines { lines ->
                for (line in lines) {
                    if (line.contains("IP address")) continue
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 4) {
                        val ip = parts[0]
                        val mac = parts[3]
                        if (mac != "00:00:00:00:00:00" && mac.length == 17) {
                            result[ip] = mac
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // 无权限或文件不存在
        }
        return result
    }

    /**
     * 解析 "ip neigh" 命令输出
     */
    private fun parseIpNeigh(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        try {
            val process = Runtime.getRuntime().exec("ip neigh")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            reader.useLines { lines ->
                for (line in lines) {
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) continue
                    val ipPart = trimmed.split("\\s+".toRegex()).firstOrNull() ?: continue
                    val mac = extractMacFromIpNeigh(trimmed) ?: continue
                    if (mac != "00:00:00:00:00:00" && mac.length == 17) {
                        result[ipPart] = mac
                    }
                }
            }
            try { process.destroy() } catch (_: Exception) {}
        } catch (_: Exception) {
            // 命令执行失败
        }
        return result
    }

    /**
     * 从 ip neigh 输出行中提取 MAC 地址
     */
    private fun extractMacFromIpNeigh(line: String): String? {
        val lladdrIndex = line.indexOf("lladdr")
        if (lladdrIndex < 0) return null
        val afterLladdr = line.substring(lladdrIndex + 6).trim()
        return afterLladdr.split("\\s+".toRegex()).firstOrNull()
    }

    // ========================
    // 模式 B：Ping 扫描（Android 13+ 降级 + 热点模式）
    // ========================

    /**
     * Ping 扫描：并发向网段内所有 IP 发送 ICMP echo，收集有响应的 IP
     *
     * 改进：使用系统 ping 命令替代 InetAddress.isReachable()
     * 原因：
     * - InetAddress.isReachable() 在 Android 上默认走 TCP echo (端口7) 而非 ICMP
     * - 热点模式下 TCP echo 被防火墙拦截
     * - 系统 ping 命令发送真正的 ICMP echo，穿透性更强
     * - 对于热点模式特别有效，因为连接设备和手机在同一网段
     */
    private suspend fun pingScan(
        subnetPrefix: String,
        localIp: String,
        gatewayIp: String,
        totalHosts: Int,
        onProgress: (Int, Int) -> Unit,
        availability: ScanAvailability
    ): ScanResult = withContext(Dispatchers.IO) {

        val aliveIps = ConcurrentHashMap<String, Boolean>()
        val executor: ExecutorService = Executors.newFixedThreadPool(PING_PARALLEL)
        val scannedCount = AtomicInteger(0)

        // 并发 Ping 所有 254 个 IP
        for (i in 1..totalHosts) {
            if (!isActive) break

            val targetIp = "$subnetPrefix.$i"
            executor.execute {
                try {
                    val reachable = systemPing(targetIp)
                    if (reachable) {
                        aliveIps[targetIp] = true
                    }
                } catch (_: Exception) {
                    // 忽略单个 Ping 失败
                } finally {
                    val count = scannedCount.incrementAndGet()
                    if (count % BATCH_SIZE == 0 || count == totalHosts) {
                        onProgress(count, totalHosts)
                    }
                }
            }
        }

        // 等待所有 Ping 完成
        executor.shutdown()
        executor.awaitTermination(PING_TOTAL_TIMEOUT_S, TimeUnit.SECONDS)

        // 同时加入本机 IP 和网关（即使 Ping 不通也应显示）
        if (localIp.isNotEmpty()) aliveIps[localIp] = true
        if (gatewayIp.isNotEmpty()) aliveIps[gatewayIp] = true

        // 构建设备列表（无 MAC 地址）
        val devices = aliveIps.keys.toList()
            .filter { it.startsWith(subnetPrefix) }
            .map { ip ->
                ScannedDevice(
                    ipAddress = ip,
                    macAddress = null,
                    isMacAvailable = false,
                    deviceType = when {
                        ip == localIp -> DeviceType.LOCAL
                        ip == gatewayIp -> DeviceType.GATEWAY
                        else -> DeviceType.NORMAL
                    }
                )
            }
            .sortedWith(
                compareBy<ScannedDevice> {
                    when (it.deviceType) {
                        DeviceType.LOCAL -> 0
                        DeviceType.GATEWAY -> 1
                        DeviceType.NORMAL -> 2
                    }
                }.thenBy {
                    it.ipAddress.split(".").lastOrNull()?.toIntOrNull() ?: 0
                }
            )

        val isPartial = availability != ScanAvailability.FULL

        ScanResult(
            devices = devices,
            isPartial = isPartial,
            availability = availability
        )
    }

    /**
     * 使用系统 ping 命令探测主机是否存活
     *
     * 相比 InetAddress.isReachable() 的优势：
     * 1. 发送真正的 ICMP echo request（不是 TCP）
     * 2. 在热点网络下穿透性更强
     * 3. 不受 Java SecurityManager 限制
     *
     * @param targetIp 目标 IP 地址
     * @return true 表示主机存活
     */
    private fun systemPing(targetIp: String): Boolean {
        return try {
            // 使用系统 ping 命令：-c 1 发1个包，-W 超时秒数
            // 注意：Android 的 ping 支持 -W 参数（小数秒）
            val timeoutSec = PING_TIMEOUT_MS / 1000.0
            val command = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Android 8+: ping 支持 -W 小数秒
                arrayOf("ping", "-c", "1", "-W", String.format("%.1f", timeoutSec), targetIp)
            } else {
                // 旧版本：使用整数秒超时
                arrayOf("ping", "-c", "1", "-W", "1", targetIp)
            }

            val process = Runtime.getRuntime().exec(command)
            val exitCode = process.waitFor()

            // 清理进程
            try {
                process.inputStream.close()
                process.errorStream.close()
                process.outputStream.close()
                process.destroy()
            } catch (_: Exception) {}

            // exitCode == 0 表示目标主机响应了 ICMP echo
            exitCode == 0
        } catch (_: Exception) {
            false
        }
    }

    // ========================
    // 公共方法
    // ========================

    /**
     * 构建设备列表（ARP 模式使用）
     */
    private fun buildDeviceList(
        arpTable: Map<String, String>,
        subnetPrefix: String,
        localIp: String,
        gatewayIp: String
    ): List<ScannedDevice> {
        val devices = mutableListOf<ScannedDevice>()

        for ((ip, mac) in arpTable) {
            if (!ip.startsWith(subnetPrefix)) continue
            val isMacReal = mac != FAKE_MAC && mac.isNotEmpty()
            val deviceType = when {
                ip == localIp -> DeviceType.LOCAL
                ip == gatewayIp -> DeviceType.GATEWAY
                else -> DeviceType.NORMAL
            }
            devices.add(
                ScannedDevice(
                    ipAddress = ip,
                    macAddress = if (isMacReal) mac else null,
                    isMacAvailable = isMacReal,
                    deviceType = deviceType
                )
            )
        }

        return devices.sortedWith(
            compareBy<ScannedDevice> {
                when (it.deviceType) {
                    DeviceType.LOCAL -> 0
                    DeviceType.GATEWAY -> 1
                    DeviceType.NORMAL -> 2
                }
            }.thenBy {
                it.ipAddress.split(".").lastOrNull()?.toIntOrNull() ?: 0
            }
        )
    }

    /**
     * 检查扫描可用性
     * Android 13+ 返回 PARTIAL（Ping 扫描可用，但无 MAC）
     */
    fun checkScanAvailability(): ScanAvailability {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> ScanAvailability.PARTIAL
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> ScanAvailability.PARTIAL
            else -> ScanAvailability.FULL
        }
    }
}
