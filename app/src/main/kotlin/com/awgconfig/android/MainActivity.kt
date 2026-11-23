package com.awgconfig.android

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awgconfig.android.i18n.LocalLang
import com.awgconfig.android.i18n.LocalStrings
import com.awgconfig.android.i18n.Strings
import com.awgconfig.android.i18n.tr
import com.awgconfig.android.tunnel.TunnelCore
import com.awgconfig.android.ui.screens.AppRoute
import com.awgconfig.android.ui.screens.AwgServerScreen
import com.awgconfig.android.ui.screens.BatchScreen
import com.awgconfig.android.ui.screens.ConfigsScreen
import com.awgconfig.android.ui.screens.HomeScreen
import com.awgconfig.android.ui.screens.KeypairScreen
import com.awgconfig.android.ui.screens.MasqueScreen
import com.awgconfig.android.ui.screens.ResultScreen
import com.awgconfig.android.ui.screens.SettingsScreen
import com.awgconfig.android.ui.screens.TunnelScreen
import com.awgconfig.android.ui.screens.WarpScreen
import com.awgconfig.android.ui.theme.AwgConfigTheme
import com.awgconfig.android.ui.theme.NeonPalette

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val vm: AppViewModel by viewModels()
        setContent {
            AwgConfigTheme {
                val lang by vm.lang.collectAsState()
                val strings = remember(lang) { if (lang == "en") Strings.en else Strings.ru }
                CompositionLocalProvider(
                    LocalStrings provides strings,
                    LocalLang provides lang,
                ) {
                    AppRoot(vm)
                }
            }
        }
    }
}

/** Внутренняя навигация (без navigation-compose, чтобы не тащить зависимость). */
@Composable
fun AppRoot(vm: AppViewModel) {
    val context = LocalContext.current
    val route = remember { mutableStateOf<AppRoute>(AppRoute.Home) }
    val pending by vm.pendingConnect.collectAsState()

    // Системный диалог VPN-разрешения → затем подключение
    val prepareLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val config = vm.consumePendingConnect() ?: return@rememberLauncherForActivityResult
        if (result.resultCode == Activity.RESULT_OK) {
            vm.connectTunnelApproved(config)
        }
    }

    LaunchedEffect(pending) {
        val config = pending ?: return@LaunchedEffect
        if (config.tunnelCapable.not()) { vm.consumePendingConnect(); return@LaunchedEffect }
        val intent = VpnService.prepare(context)
        if (intent != null) {
            prepareLauncher.launch(intent)
        } else {
            vm.consumePendingConnect()
            vm.connectTunnelApproved(config)
        }
    }

    BackHandler(enabled = route.value != AppRoute.Home) {
        route.value = when (val current = route.value) {
            is AppRoute.Result -> current.back ?: AppRoute.Home
            else -> AppRoute.Home
        }
    }

    Column(Modifier.fillMaxSize().background(NeonPalette.background)) {
        AppTopBar(
            title = tr(LocalStrings.current, route.value.titleKey()),
            showBack = route.value != AppRoute.Home,
            onBack = { route.value = if (route.value is AppRoute.Result) (route.value as AppRoute.Result).back ?: AppRoute.Home else AppRoute.Home },
            onHome = { route.value = AppRoute.Home },
            onConfigs = { route.value = AppRoute.Configs },
            onSettings = { route.value = AppRoute.Settings },
        )
        Surface(
            Modifier.fillMaxSize(),
            color = Color.Transparent,
        ) {
            when (val current = route.value) {
                AppRoute.Home -> HomeScreen(vm, onNavigate = { route.value = it })
                AppRoute.Warp -> WarpScreen(vm, onResult = { route.value = AppRoute.Result(AppRoute.Warp) })
                AppRoute.AwgServer -> AwgServerScreen(vm, onResult = { route.value = AppRoute.Result(AppRoute.AwgServer) })
                AppRoute.Masque -> MasqueScreen(vm, onResult = { route.value = AppRoute.Result(AppRoute.Masque) })
                AppRoute.Keypair -> KeypairScreen(vm, onResult = { route.value = AppRoute.Result(AppRoute.Keypair) })
                AppRoute.Batch -> BatchScreen(vm, onResult = { route.value = AppRoute.Result(AppRoute.Batch) })
                is AppRoute.Result -> ResultScreen(vm, back = current.back)
                AppRoute.Configs -> ConfigsScreen(vm, onOpen = { route.value = AppRoute.Result(AppRoute.Configs) })
                AppRoute.Tunnel -> TunnelScreen(vm)
                AppRoute.Settings -> SettingsScreen(vm)
            }
        }
    }
}

private fun AppRoute.titleKey(): String = when (this) {
    AppRoute.Home -> "nav_home"
    AppRoute.Warp -> "home_card_warp"
    AppRoute.AwgServer -> "awg_title"
    AppRoute.Masque -> "masque_title"
    AppRoute.Keypair -> "keypair_title"
    AppRoute.Batch -> "batch_title"
    is AppRoute.Result -> "result_title"
    AppRoute.Configs -> "nav_configs"
    AppRoute.Tunnel -> "nav_tunnel"
    AppRoute.Settings -> "nav_settings"
}

@Composable
private fun AppTopBar(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onConfigs: () -> Unit,
    onSettings: () -> Unit,
) {
    val s = LocalStrings.current
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(NeonPalette.background.copy(alpha = 0.0f), NeonPalette.background)
                )
            )
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "back",
                        tint = NeonPalette.accent2,
                    )
                }
            } else {
                Box(
                    Modifier
                        .padding(start = 12.dp)
                        .size(10.dp)
                        .background(
                            Brush.linearGradient(listOf(NeonPalette.accent, NeonPalette.accent2)),
                            CircleShape,
                        )
                )
            }
            Text(
                title,
                Modifier.weight(1f).padding(start = 8.dp),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = NeonPalette.textPrimary,
            )
            IconButton(onClick = onConfigs) {
                Icon(Icons.Filled.History, contentDescription = s["nav_configs"], tint = NeonPalette.textSecondary)
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = s["nav_settings"], tint = NeonPalette.textSecondary)
            }
        }
    }
}

/** Индикатор состояния туннеля (переиспользуется на главной и в туннеле). */
@Composable
fun TunnelStatusDot(state: TunnelCore.State): Color = when (state) {
    TunnelCore.State.UP -> NeonPalette.accent2
    TunnelCore.State.CONNECTING -> NeonPalette.amber
    TunnelCore.State.ERROR -> NeonPalette.red
    else -> NeonPalette.textSecondary
}

@Composable
fun VerticalSpacer(h: Int = 12) = Spacer(Modifier.height(h.dp))
