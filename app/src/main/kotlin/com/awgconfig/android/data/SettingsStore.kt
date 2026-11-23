package com.awgconfig.android.data

import android.content.Context

/** Настройки приложения: язык, DNS по умолчанию, скрытие приватного ключа. */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences("awg_settings", Context.MODE_PRIVATE)

    /** "ru" | "en". */
    var language: String
        get() = prefs.getString(KEY_LANGUAGE, "ru") ?: "ru"
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value).apply()

    var defaultDns: String
        get() = prefs.getString(KEY_DNS, "cloudflare") ?: "cloudflare"
        set(value) = prefs.edit().putString(KEY_DNS, value).apply()

    var hidePrivateKey: Boolean
        get() = prefs.getBoolean(KEY_HIDE_PRIV, true)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_PRIV, value).apply()

    /** Последний конфиг, использованный встроенным туннелем (текст .conf). */
    var lastTunnelConfig: String?
        get() = prefs.getString(KEY_TUNNEL_CONF, null)
        set(value) = prefs.edit().putString(KEY_TUNNEL_CONF, value).apply()

    var lastTunnelTitle: String?
        get() = prefs.getString(KEY_TUNNEL_TITLE, null)
        set(value) = prefs.edit().putString(KEY_TUNNEL_TITLE, value).apply()

    /** Протокол последнего конфига туннеля (ключ ProtocolKind.key). */
    var lastTunnelKind: String?
        get() = prefs.getString(KEY_TUNNEL_KIND, null)
        set(value) = prefs.edit().putString(KEY_TUNNEL_KIND, value).apply()

    companion object {
        private const val KEY_LANGUAGE = "language"
        private const val KEY_DNS = "dns"
        private const val KEY_HIDE_PRIV = "hide_priv"
        private const val KEY_TUNNEL_CONF = "tunnel_conf"
        private const val KEY_TUNNEL_TITLE = "tunnel_title"
        private const val KEY_TUNNEL_KIND = "tunnel_kind"
    }
}
