package com.awgconfig.android

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.awgconfig.android.core.warp.WarpGenerator
import com.awgconfig.android.core.warp.WarpRegistrationApi
import com.awgconfig.android.data.HistoryStore
import com.awgconfig.android.data.SettingsStore
import com.awgconfig.android.tunnel.VpnTunnelController
import java.net.URL
import java.net.URLConnection

/** Точка входа приложения: держит синглтоны генератора/хранилищ/туннеля. */
class AwgApp : Application() {
    lateinit var settings: SettingsStore
        private set
    lateinit var history: HistoryStore
        private set
    lateinit var tunnel: VpnTunnelController
        private set
    lateinit var warpGenerator: WarpGenerator
        private set

    override fun onCreate() {
        super.onCreate()
        settings = SettingsStore(this)
        history = HistoryStore(this)
        tunnel = VpnTunnelController(this)
        warpGenerator = WarpGenerator { assets.open("routes.json") }
        WarpRegistrationApi.connectionOpener = ::openRegistrationConnection
    }

    /**
     * Регистрация WARP не должна зависеть от состояния VPN-туннеля (нашего или
     * чужого): при активном VPN-транспорте привязываем HTTP-сокеты регистрации к
     * базовой сети (Wi-Fi/мобильной), минуя VPN. Привязка на уровне отдельного
     * соединения (Network.openConnection), туннелю не мешает.
     */
    private fun openRegistrationConnection(url: URL): URLConnection {
        val plain = url.openConnection()
        val cm = getSystemService(ConnectivityManager::class.java) ?: return plain
        return try {
            val active = cm.activeNetwork
            val activeCaps = active?.let { cm.getNetworkCapabilities(it) }
            val vpnActive = activeCaps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
            if (!vpnActive) return plain
            val underlying = cm.allNetworks.firstOrNull { n ->
                val c = cm.getNetworkCapabilities(n) ?: return@firstOrNull false
                !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                    c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
            underlying?.openConnection(url) ?: plain
        } catch (_: Exception) {
            plain
        }
    }
}
