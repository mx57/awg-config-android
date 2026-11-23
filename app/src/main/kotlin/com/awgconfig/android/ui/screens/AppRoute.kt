package com.awgconfig.android.ui.screens

/** Маршруты внутреннего навигатора. */
sealed interface AppRoute {
    data object Home : AppRoute
    data object Warp : AppRoute
    data object AwgServer : AppRoute
    data object Masque : AppRoute
    data object Keypair : AppRoute
    data object Batch : AppRoute
    data object Configs : AppRoute
    data object Tunnel : AppRoute
    data object Settings : AppRoute
    data class Result(val back: AppRoute?) : AppRoute
}
