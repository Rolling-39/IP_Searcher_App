package com.example.ipsearcher

import android.app.Application

/**
 * Application 类
 * 用于初始化全局组件
 */
class IpSearcherApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // 可在此处初始化 DI 框架、日志系统等
    }
}
