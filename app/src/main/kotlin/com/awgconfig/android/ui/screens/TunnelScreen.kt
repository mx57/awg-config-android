package com.awgconfig.android.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awgconfig.android.AppViewModel
import com.awgconfig.android.VerticalSpacer
import com.awgconfig.android.i18n.LocalStrings
import com.awgconfig.android.ui.components.Chip
import com.awgconfig.android.ui.components.NeonCard
import com.awgconfig.android.ui.components.SectionTitle
import com.awgconfig.android.ui.theme.NeonPalette
import kotlinx.coroutines.delay

/** Экран встроенного туннеля: состояние, статистика, диагностика
 * (разрешение VPN / движок / последняя ошибка), включение/выключение. */
@Composable
fun TunnelScreen(vm: AppViewModel) {
    val s = LocalStrings.current
    val state by vm.tunnel.state.collectAsState()
    val vpnPermission by vm.vpnPermission.collectAsState()

    // Обновляем статус разрешения при входе на экран: диалог мог быть выдан
    // (или отозван через настройки системы) в другом месте.
    LaunchedEffect(Unit) { vm.refreshVpnPermission() }

    LaunchedEffect(state.state) {
        while (state.state == com.awgconfig.android.tunnel.TunnelCore.State.UP) {
            vm.tunnel.refreshStats()
            delay(2000)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        val color = when (state.state) {
            com.awgconfig.android.tunnel.TunnelCore.State.UP -> NeonPalette.accent2
            com.awgconfig.android.tunnel.TunnelCore.State.CONNECTING -> NeonPalette.amber
            com.awgconfig.android.tunnel.TunnelCore.State.ERROR -> NeonPalette.red
            else -> NeonPalette.textSecondary
        }

        NeonCard(accent = color) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(color, CircleShape))
                Text(
                    "  " + s["home_tunnel_" + when (state.state) {
                        com.awgconfig.android.tunnel.TunnelCore.State.UP -> "up"
                        com.awgconfig.android.tunnel.TunnelCore.State.CONNECTING -> "connecting"
                        com.awgconfig.android.tunnel.TunnelCore.State.ERROR -> "error"
                        else -> "down"
                    }].orEmpty(),
                    fontSize = 16.sp, fontWeight = FontWeight.Bold,
                )
            }
            state.error?.let {
                Text(it, Modifier.padding(top = 8.dp), fontSize = 12.sp, color = NeonPalette.red)
            }
            VerticalSpacer(8)
            Text(s["tunnel_last_config"].orEmpty() + ": " + (state.title ?: "—"), fontSize = 12.sp, color = NeonPalette.textSecondary)
        }

        VerticalSpacer(12)

        // Диагностика: разрешение VPN (его можно выдать явно кнопкой —
        // ответ на «диалог не появился»: если разрешение уже выдано,
        // система его не запрашивает повторно), движок и последняя ошибка.
        SectionTitle(s["tunnel_diag_title"].orEmpty())
        NeonCard(accent = if (vpnPermission) NeonPalette.accent2 else NeonPalette.amber) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(10.dp).background(
                        if (vpnPermission) NeonPalette.accent2 else NeonPalette.amber,
                        CircleShape,
                    )
                )
                Text(
                    "  " + s["tunnel_permission"].orEmpty() + ": " +
                        s[if (vpnPermission) "vpn_permission_granted" else "vpn_permission_missing"].orEmpty(),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold,
                )
            }
            if (!vpnPermission) {
                Row(Modifier.padding(top = 8.dp)) {
                    Chip(s["vpn_permission_request"].orEmpty(), accent = NeonPalette.amber) {
                        vm.requestVpnPermission()
                    }
                }
            }
            Text(
                s["tunnel_permission_hint"].orEmpty(),
                Modifier.padding(top = 6.dp),
                fontSize = 10.sp, color = NeonPalette.textSecondary, lineHeight = 14.sp,
            )
            Text(
                s["tunnel_engine"].orEmpty() + ": " +
                    (state.version ?: s["tunnel_engine_missing"].orEmpty()) +
                    " (awg-go)",
                Modifier.padding(top = 8.dp),
                fontSize = 11.sp, color = NeonPalette.textSecondary, fontFamily = FontFamily.Monospace,
            )
            state.error?.let {
                Text(
                    s["tunnel_diag_last_error"].orEmpty() + ": " + it,
                    Modifier.padding(top = 4.dp),
                    fontSize = 11.sp, color = NeonPalette.red, lineHeight = 15.sp,
                )
            }
        }

        VerticalSpacer(12)
        SectionTitle(s["tunnel_rx"] + " / " + s["tunnel_tx"])
        NeonCard(accent = NeonPalette.outline) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(horizontalAlignment = Alignment.Start) {
                    Text(s["tunnel_rx"].orEmpty(), fontSize = 11.sp, color = NeonPalette.textSecondary)
                    Text(formatBytes(state.rxBytes), fontSize = 16.sp, fontFamily = FontFamily.Monospace, color = NeonPalette.accent3)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(s["tunnel_tx"].orEmpty(), fontSize = 11.sp, color = NeonPalette.textSecondary)
                    Text(formatBytes(state.txBytes), fontSize = 16.sp, fontFamily = FontFamily.Monospace, color = NeonPalette.accent)
                }
            }
        }

        VerticalSpacer(12)
        if (state.state == com.awgconfig.android.tunnel.TunnelCore.State.UP) {
            NeonCard(accent = NeonPalette.red, onClick = { vm.disconnectTunnel() }) {
                Text(
                    s["action_disconnect"].orEmpty(),
                    Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NeonPalette.red,
                )
            }
        } else {
            // Туннель выключен: кнопка «Подключить последний конфиг» — тот же
            // prepare-флоу с системным диалогом VPN, что и на экране результата.
            val lastTitle = vm.settings.lastTunnelTitle
            val lastText = vm.settings.lastTunnelConfig
            if (!lastText.isNullOrEmpty()) {
                NeonCard(accent = NeonPalette.accent2, onClick = { vm.requestConnectLast() }) {
                    Text(
                        s["tunnel_connect_last"].orEmpty(),
                        Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                        fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NeonPalette.accent2,
                    )
                    Text(
                        lastTitle ?: "—",
                        Modifier.padding(top = 6.dp), textAlign = TextAlign.Center,
                        fontSize = 10.sp, color = NeonPalette.textSecondary,
                    )
                }
                VerticalSpacer(8)
            }
            NeonCard(accent = NeonPalette.outline) {
                Text(s["tunnel_no_config"].orEmpty(), fontSize = 11.sp, color = NeonPalette.textSecondary, lineHeight = 15.sp)
            }
        }

        VerticalSpacer(12)
        Text(s["tunnel_note"].orEmpty(), fontSize = 11.sp, color = NeonPalette.textSecondary, lineHeight = 16.sp)
        VerticalSpacer(24)
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1 shl 30 -> "%.2f GiB".format(bytes.toDouble() / (1 shl 30))
    bytes >= 1 shl 20 -> "%.2f MiB".format(bytes.toDouble() / (1 shl 20))
    bytes >= 1 shl 10 -> "%.1f KiB".format(bytes.toDouble() / (1 shl 10))
    else -> "$bytes B"
}
