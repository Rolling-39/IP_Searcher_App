package com.example.ipsearcher.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ipsearcher.R
import com.example.ipsearcher.data.model.DeviceType
import com.example.ipsearcher.data.model.ScannedDevice

/**
 * 扫描结果列表项组件
 * 显示设备 IP、MAC 地址和设备类型标签
 */
@Composable
fun ScanResultItem(
    device: ScannedDevice,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 1.dp
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 第一行：IP 地址 + 设备类型标签
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = device.ipAddress,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // 设备类型标签
                DeviceTypeTag(
                    text = getDeviceTypeLabel(device.deviceType),
                    color = getDeviceTypeColor(device.deviceType)
                )
            }

            // 第二行：MAC 地址
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_mac_address),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = if (device.isMacAvailable && device.macAddress != null) {
                        device.macAddress
                    } else {
                        stringResource(R.string.mac_unavailable)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = if (device.isMacAvailable) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }

            // 第三行：主机名（如果有）
            device.hostname?.let { hostname ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "主机名",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = hostname,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * 获取设备类型显示标签
 */
@Composable
private fun getDeviceTypeLabel(type: DeviceType): String {
    return when (type) {
        DeviceType.LOCAL -> stringResource(R.string.device_type_local)
        DeviceType.GATEWAY -> stringResource(R.string.device_type_gateway)
        DeviceType.NORMAL -> ""
    }
}

/**
 * 获取设备类型对应颜色
 */
@Composable
private fun getDeviceTypeColor(type: DeviceType): androidx.compose.ui.graphics.Color {
    return when (type) {
        DeviceType.LOCAL -> MaterialTheme.colorScheme.primary
        DeviceType.GATEWAY -> MaterialTheme.colorScheme.tertiary
        DeviceType.NORMAL -> MaterialTheme.colorScheme.outline
    }
}
