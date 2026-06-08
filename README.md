# IP Searcher 🔍

一款 Android 局域网设备扫描工具，支持查看本机网络信息和扫描局域网内所有设备的 IP 地址。

## 功能特性

### 📱 本机信息
- 查看 IP 地址、MAC 地址、网关、子网掩码
- 查看 WiFi SSID、信号强度、网络类型
- Android 10+ MAC 地址不可用时显示隐私保护提示

### 🌐 局域网扫描
- **WiFi 路由器模式**：扫描当前连接路由器下的所有设备
- **手机热点模式**：扫描连接至本机热点的所有设备
- 自动检测网络模式，选择最优扫描策略

### 🔄 智能版本适配

| Android 版本 | 扫描模式 | IP 地址 | MAC 地址 |
|:---:|:---:|:---:|:---:|
| 8 - 9 | ARP 扫描 | ✅ | ✅ |
| 10 - 12 | ARP 扫描 | ✅ | ⚠️ 系统隐私保护 |
| 13+ | Ping 扫描 | ✅ | ❌ 不可用 |

- **Android 8-12**：UDP 广播探测 → 读取 ARP 表 → 获取 IP + MAC
- **Android 13+**：并发 ICMP Ping 探测 → 仅获取 IP（`/proc/net/arp` 已被 SELinux 封锁）
- 自动降级：ARP 扫描无结果时自动切换 Ping 扫描

## 技术栈

- **语言**：Kotlin 1.9.22
- **UI 框架**：Jetpack Compose + Material 3
- **架构**：MVVM（StateFlow 驱动）
- **构建**：AGP 8.2.2 + Gradle 8.5
- **最低版本**：Android 8.0（API 26）
- **目标版本**：Android 14（API 34）

## 项目结构

```
app/src/main/java/com/example/ipsearcher/
├── MainActivity.kt              # 入口 Activity + 权限请求
├── data/model/
│   └── DeviceInfo.kt           # 数据模型
├── network/
│   ├── NetworkUtils.kt         # 本机网络信息获取
│   ├── ArpScanner.kt           # 局域网扫描引擎（双模式）
│   └── HotspotDetector.kt     # 热点状态检测
├── viewmodel/
│   └── MainViewModel.kt        # ViewModel（状态协调）
└── ui/
    ├── theme/                   # Material 3 主题
    ├── navigation/              # 双 Tab 导航
    ├── screen/                  # 页面组件
    └── component/              # 通用 UI 组件
```

## 权限说明

| 权限 | 用途 | 必需 |
|:---|:---|:---:|
| `ACCESS_WIFI_STATE` | 获取 WiFi 连接信息 | ✅ |
| `CHANGE_WIFI_STATE` | 扫描网络设备 | ✅ |
| `ACCESS_NETWORK_STATE` | 检测网络连接状态 | ✅ |
| `ACCESS_FINE_LOCATION` | 获取 SSID/BSSID（Android 9+） | ⚠️ 运行时请求 |
| `INTERNET` | UDP 广播探测 | ✅ |

> `ACCESS_FINE_LOCATION` 为运行时权限，拒绝后 App 仍可运行，但部分信息（如 SSID）不可用。

## 构建与运行

1. 使用 Android Studio 打开本项目
2. 等待 Gradle 同步完成
3. 连接 Android 设备或启动模拟器
4. 点击 Run 运行

### 推荐测试环境

- **局域网扫描**：Android 8-9 模拟器/设备（ARP 扫描完整可用）
- **降级测试**：Android 13+ 设备（验证 Ping 扫描降级提示）
- **热点模式**：真机开启热点 + 另一设备连接后扫描

## 已知限制

- **Android 10+**：系统隐私保护，`WifiInfo.getMacAddress()` 返回假值 `02:00:00:00:00:00`，无法获取真实 MAC 地址
- **Android 13+**：`/proc/net/arp` 和 `ip neigh` 被 SELinux 策略封锁，ARP 扫描不可用，自动降级为 Ping 扫描（仅 IP，无 MAC）
- **热点检测**：部分定制 ROM 的反射 API 可能被封锁，App 使用 4 层检测策略（TetheredInterfaces → isApEnabled → isWifiApEnabled → IP 推断）确保兼容性
- **Ping 扫描**：部分设备可能屏蔽 ICMP echo，导致扫描不完整

## License

MIT License
