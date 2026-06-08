package com.example.ipsearcher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ipsearcher.ui.navigation.AppNavigation
import com.example.ipsearcher.ui.theme.IPSearcherTheme
import com.example.ipsearcher.viewmodel.MainViewModel

/**
 * 主 Activity
 * 处理权限请求和应用入口
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            IPSearcherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PermissionGate(
                        onPermissionResult = { granted ->
                            // 权限结果回调：无论是否授予，都允许继续使用 App
                        }
                    ) {
                        AppContent()
                    }
                }
            }
        }
    }
}

/**
 * App 主内容
 */
@Composable
private fun AppContent() {
    val viewModel: MainViewModel = viewModel()

    AppNavigation(
        deviceInfoUiState = viewModel.deviceInfoState.collectAsState().value,
        lanScanUiState = viewModel.lanScanState.collectAsState().value,
        onRefreshDeviceInfo = viewModel::refreshDeviceInfo,
        onStartScan = viewModel::startScan,
        onStopScan = viewModel::stopScan,
        onTabChanged = viewModel::onTabChanged
    )
}

/**
 * 权限门控组件
 * 在 Android 6+ 上请求 ACCESS_FINE_LOCATION 权限（获取 WiFi SSID/BSSID 需要）
 * 权限被拒绝时仍然允许使用 App，仅功能受限
 */
@Composable
private fun PermissionGate(
    onPermissionResult: (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    var hasRequestedPermission by remember { mutableStateOf(false) }
    var showRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRequestedPermission = true
        showRationale = false
        onPermissionResult(granted)
    }

    // 检查权限是否已授予
    val isPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        ContextCompat.checkSelfPermission(
            androidx.compose.ui.platform.LocalContext.current,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true // Android 5.x 不需要运行时权限
    }

    LaunchedEffect(isPermissionGranted) {
        if (!hasRequestedPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isPermissionGranted) {
                // 显示说明后请求权限
                showRationale = true
            } else {
                hasRequestedPermission = true
                onPermissionResult(true)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        content()

        // 权限说明弹窗
        if (showRationale) {
            AlertDialog(
                onDismissRequest = {
                    showRationale = false
                    hasRequestedPermission = true
                    // 用户点击外部关闭，视为跳过
                },
                title = {
                    Text("需要位置权限")
                },
                text = {
                    Text(
                        text = stringResource(R.string.permission_location_rationale),
                        textAlign = TextAlign.Start
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showRationale = false
                            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    ) {
                        Text("授予")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showRationale = false
                            hasRequestedPermission = true
                            onPermissionResult(false)
                        }
                    ) {
                        Text("跳过")
                    }
                }
            )
        }
    }
}
