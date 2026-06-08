package com.example.ipsearcher.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ipsearcher.data.model.DeviceInfoUiState
import com.example.ipsearcher.data.model.LanScanUiState
import com.example.ipsearcher.ui.screen.DeviceInfoScreen
import com.example.ipsearcher.ui.screen.LanScanScreen

/**
 * Tab 路由定义
 */
enum class ScreenRoute(val route: String) {
    DEVICE_INFO("device_info"),
    LAN_SCAN("lan_scan")
}

/**
 * Tab 配置
 */
data class TabItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

/**
 * App 导航组件
 * 包含底部 Tab 栏和页面切换逻辑
 */
@Composable
fun AppNavigation(
    deviceInfoUiState: DeviceInfoUiState,
    lanScanUiState: LanScanUiState,
    onRefreshDeviceInfo: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onTabChanged: (String) -> Unit
) {
    val navController = rememberNavController()

    val tabs = listOf(
        TabItem(
            route = ScreenRoute.DEVICE_INFO.route,
            label = "本机信息",
            icon = Icons.Default.DevicesOther
        ),
        TabItem(
            route = ScreenRoute.LAN_SCAN.route,
            label = "局域网扫描",
            icon = Icons.Default.Lan
        )
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                tabs.forEach { tab ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label) },
                        selected = currentDestination?.hierarchy?.any {
                            it.route == tab.route
                        } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                // 避免重复导航到同一个目的地
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                            onTabChanged(tab.route)
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ScreenRoute.DEVICE_INFO.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ScreenRoute.DEVICE_INFO.route) {
                DeviceInfoScreen(
                    uiState = deviceInfoUiState,
                    onRefresh = onRefreshDeviceInfo
                )
            }

            composable(ScreenRoute.LAN_SCAN.route) {
                LanScanScreen(
                    uiState = lanScanUiState,
                    onStartScan = onStartScan,
                    onStopScan = onStopScan
                )
            }
        }
    }
}
