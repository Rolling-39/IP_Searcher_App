package com.example.ipsearcher.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ipsearcher.R
import com.example.ipsearcher.data.model.LanScanUiState
import com.example.ipsearcher.data.model.ScanStatus
import com.example.ipsearcher.ui.component.ScanHeaderCard
import com.example.ipsearcher.ui.component.ScanResultItem

/**
 * 局域网扫描页面
 * 提供扫描按钮，显示扫描进度和发现的设备列表
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanScanScreen(
    uiState: LanScanUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_lan_scan)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 扫描信息栏（始终显示）
            ScanHeaderCard(
                networkMode = uiState.networkMode,
                subnetPrefix = uiState.subnetPrefix,
                deviceCount = uiState.devices.size
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 错误提示
            uiState.errorMessage?.let { message ->
                ErrorBanner(message = message)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 扫描按钮
            ScanButton(
                scanStatus = uiState.scanStatus,
                onStartScan = onStartScan,
                onStopScan = onStopScan
            )

            // 扫描进度条
            if (uiState.scanStatus == ScanStatus.SCANNING) {
                ScanProgressBar(
                    scannedCount = uiState.scannedCount,
                    totalHosts = uiState.totalHosts,
                    subnetPrefix = uiState.subnetPrefix
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 设备列表 / 空状态
            when {
                uiState.devices.isEmpty() && uiState.scanStatus == ScanStatus.IDLE -> {
                    // 空状态
                    EmptyState()
                }
                uiState.devices.isEmpty() && uiState.scanStatus == ScanStatus.COMPLETED -> {
                    // 扫描完成但未发现设备
                    NoDevicesFound()
                }
                else -> {
                    // 设备列表
                    DeviceList(devices = uiState.devices)
                }
            }
        }
    }
}

/**
 * 扫描按钮
 */
@Composable
private fun ScanButton(
    scanStatus: ScanStatus,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit
) {
    val isScanning = scanStatus == ScanStatus.SCANNING

    Button(
        onClick = if (isScanning) onStopScan else onStartScan,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isScanning) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
        ),
        shape = MaterialTheme.shapes.large
    ) {
        Icon(
            imageVector = if (isScanning) Icons.Default.Stop else Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isScanning) {
                stringResource(R.string.button_stop_scan)
            } else {
                stringResource(R.string.button_start_scan)
            },
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/**
 * 扫描进度条
 */
@Composable
private fun ScanProgressBar(
    scannedCount: Int,
    totalHosts: Int,
    subnetPrefix: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(
                    R.string.scanning_progress,
                    if (subnetPrefix.isNotEmpty()) "$subnetPrefix.x" else "--",
                    scannedCount,
                    totalHosts
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.primaryContainer,
            progress = {
                if (totalHosts > 0) scannedCount.toFloat() / totalHosts
                else 0f
            }
        )
    }
}

/**
 * 错误提示横幅
 */
@Composable
private fun ErrorBanner(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/**
 * 空状态
 */
@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lan,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.outlineVariant
            )
            Text(
                text = stringResource(R.string.empty_state_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.empty_state_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * 未发现设备
 */
@Composable
private fun NoDevicesFound() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.outlineVariant
            )
            Text(
                text = stringResource(R.string.no_devices_found),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

/**
 * 设备列表
 */
@Composable
private fun DeviceList(devices: List<com.example.ipsearcher.data.model.ScannedDevice>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(
            items = devices,
            key = { it.ipAddress }
        ) { device ->
            ScanResultItem(device = device)
        }
    }
}
