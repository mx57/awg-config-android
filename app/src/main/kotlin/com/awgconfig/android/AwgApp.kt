package com.awgconfig.android

import android.app.Application
import com.awgconfig.android.core.warp.WarpGenerator
import com.awgconfig.android.data.HistoryStore
import com.awgconfig.android.data.SettingsStore
import com.awgconfig.android.tunnel.TunnelCore

/** Точка входа приложения: держит синглтоны генератора/хранилищ/туннеля. */
class AwgApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var history: HistoryStore
        private set
    lateinit var tunnel: TunnelCore
        private set
    lateinit var warpGenerator: WarpGenerator
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        history = HistoryStore(this)
        tunnel = TunnelCore(this)
        warpGenerator = WarpGenerator { assets.open("routes.json") }
    }
}
