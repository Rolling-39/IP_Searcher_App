package com.example.ipsearcher.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ipsearcher.R
import com.example.ipsearcher.data.model.DeviceInfoUiState
import com.example.ipsearcher.data.model.NetworkType
import com.example.ipsearcher.ui.component.DeviceCardModifier
import com.example.ipsearcher.ui.component.InfoRow
import com.example.ipsearcher.ui.component.NetworkStatusIndicator
import com.example.ipsearcher.ui.component.ThinDivider

/**
 * 本机信息页面
 * 显示当前设备的 IP、MAC、网关、子网掩码、SSID、网络类型等
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceInfoScreen(
    uiState: DeviceInfoUiState,
    onRefresh: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.title_device_info)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "刷新"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (uiState.isLoading) {
                // 加载状态
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                // 本机信息卡片
                DeviceInfoCard(uiState = uiState)

                // 网络状态卡片
                NetworkStatusCard(uiState = uiState)
            }
        }
    }
}

/**
 * 本机信息主卡片
 */
@Composable
private fun DeviceInfoCard(uiState: DeviceInfoUiState) {
    Card(
        modifier = DeviceCardModifier
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // 卡片标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Smartphone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "本机信息",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            ThinDivider()

            // IP 地址
            InfoRow(
                label = stringResource(R.string.label_ip_address),
                value = uiState.ip.ifEmpty { stringResource(R.string.not_connected) },
                icon = Icons.Default.Language
            )

            ThinDivider()

            // MAC 地址
            InfoRow(
                label = stringResource(R.string.label_mac_address),
                value = when {
                    uiState.mac.isEmpty() -> stringResource(R.string.not_connected)
                    !uiState.isMacReal -> stringResource(R.string.mac_unavailable)
                    else -> uiState.mac
                },
                icon = Icons.Default.Key
            )

            ThinDivider()

            // 网关地址
            InfoRow(
                label = stringResource(R.string.label_gateway),
                value = uiState.gateway.ifEmpty { "--" },
                icon = Icons.Default.Router
            )

            ThinDivider()

            // 子网掩码
            InfoRow(
                label = stringResource(R.string.label_subnet_mask),
                value = uiState.subnetMask.ifEmpty { "--" },
                icon = Icons.Default.GridOn
            )
        }
    }
}

/**
 * 网络状态卡片
 */
@Composable
private fun NetworkStatusCard(uiState: DeviceInfoUiState) {
    Card(
        modifier = DeviceCardModifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // 卡片标题
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "网络状态",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            ThinDivider()

            // 网络类型 + 状态指示器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = stringResource(R.string.label_network_type),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                val networkLabel = when (uiState.networkType) {
                    NetworkType.NONE -> stringResource(R.string.not_connected)
                    NetworkType.WIFI -> stringResource(R.string.wifi_connected)
                    NetworkType.MOBILE -> stringResource(R.string.mobile_data)
                    NetworkType.HOTSPOT -> stringResource(R.string.hotspot_active)
                }
                val isConnected = uiState.networkType != NetworkType.NONE

                NetworkStatusIndicator(
                    isConnected = isConnected,
                    label = networkLabel
                )
            }

            ThinDivider()

            // SSID
            InfoRow(
                label = stringResource(R.string.label_ssid),
                value = uiState.ssid.ifEmpty { "--" },
                icon = Icons.Default.WifiTethering
            )

            ThinDivider()

            // 信号强度
            val signalLabel = when {
                uiState.signalStrength < 0 -> "--"
                uiState.signalStrength >= -50 -> stringResource(R.string.signal_excellent)
                uiState.signalStrength >= -60 -> stringResource(R.string.signal_good)
                uiState.signalStrength >= -70 -> stringResource(R.string.signal_medium)
                else -> stringResource(R.string.signal_weak)
            }
            InfoRow(
                label = stringResource(R.string.label_signal_strength),
                value = signalLabel,
                icon = Icons.Default.SignalCellularAlt
            )
        }
    }
}
