package com.awgconfig.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awgconfig.android.AppViewModel
import com.awgconfig.android.VerticalSpacer
import com.awgconfig.android.i18n.LocalStrings
import com.awgconfig.android.i18n.tr
import com.awgconfig.android.tunnel.TunnelCore
import com.awgconfig.android.ui.components.NeonCard
import com.awgconfig.android.ui.theme.NeonPalette

/** Главная: карточки протоколов + статус встроенного туннеля. */
@Composable
fun HomeScreen(vm: AppViewModel, onNavigate: (AppRoute) -> Unit) {
    val s = LocalStrings.current
    val tunnelState by vm.tunnel.state.collectAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // Хиро-заголовок
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp)
        ) {
            Text(
                s["home_title"].orEmpty(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = NeonPalette.textPrimary,
                lineHeight = 30.sp,
            )
        }
        Text(
            s["home_subtitle"].orEmpty(),
            fontSize = 12.sp,
            color = NeonPalette.textSecondary,
            lineHeight = 16.sp,
        )
        VerticalSpacer(16)

        // Статус туннеля
        NeonCard(accent = when (tunnelState.state) {
            TunnelCore.State.UP -> NeonPalette.accent2
            TunnelCore.State.ERROR -> NeonPalette.red
            else -> NeonPalette.accent3
        }, onClick = { onNavigate(AppRoute.Tunnel) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(10.dp)
                        .background(
                            when (tunnelState.state) {
                                TunnelCore.State.UP -> NeonPalette.accent2
                                TunnelCore.State.CONNECTING -> NeonPalette.amber
                                TunnelCore.State.ERROR -> NeonPalette.red
                                else -> NeonPalette.textSecondary
                            },
                            CircleShape,
                        )
                )
                Text(
                    s["home_tunnel_title"].orEmpty() + " · " +
                        (s["home_tunnel_" + when (tunnelState.state) {
                            TunnelCore.State.UP -> "up"
                            TunnelCore.State.CONNECTING -> "connecting"
                            TunnelCore.State.ERROR -> "error"
                            else -> "down"
                        }].orEmpty()),
                    Modifier.padding(start = 10.dp),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                )
            }
            tunnelState.title?.let {
                Text(it, Modifier.padding(top = 6.dp), fontSize = 12.sp, color = NeonPalette.textSecondary)
            }
            Text(
                s["home_tunnel_hint"].orEmpty(),
                Modifier.padding(top = 6.dp),
                fontSize = 11.sp,
                color = NeonPalette.textSecondary,
                lineHeight = 15.sp,
            )
        }
        VerticalSpacer(12)

        // Карточки протоколов
        NeonCard(accent = NeonPalette.accent3, onClick = { onNavigate(AppRoute.Warp) }) {
            Text(s["home_card_warp"].orEmpty(), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = NeonPalette.accent3)
            Text(s["home_card_warp_desc"].orEmpty(), Modifier.padding(top = 6.dp), fontSize = 12.sp, color = NeonPalette.textSecondary, lineHeight = 17.sp)
            Text("4 профиля · CPS · маршруты · vpn://", Modifier.padding(top = 8.dp), fontSize = 10.sp,
                color = NeonPalette.accent3.copy(alpha = 0.8f))
        }
        VerticalSpacer(10)

        NeonCard(accent = NeonPalette.accent, onClick = { onNavigate(AppRoute.AwgServer) }) {
            Text(s["home_card_awg"].orEmpty(), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = NeonPalette.accent)
            Text(s["home_card_awg_desc"].orEmpty(), Modifier.padding(top = 6.dp), fontSize = 12.sp, color = NeonPalette.textSecondary, lineHeight = 17.sp)
            Text("Jc · Jmin/Jmax · S1–S4 · H1–H4 · I1–I5", Modifier.padding(top = 8.dp), fontSize = 10.sp,
                color = NeonPalette.accent.copy(alpha = 0.8f))
        }
        VerticalSpacer(10)

        NeonCard(accent = NeonPalette.accent4, onClick = { onNavigate(AppRoute.Masque) }) {
            Text(s["home_card_masque"].orEmpty(), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = NeonPalette.accent4)
            Text(s["home_card_masque_desc"].orEmpty(), Modifier.padding(top = 6.dp), fontSize = 12.sp, color = NeonPalette.textSecondary, lineHeight = 17.sp)
            Text("masque:// · sing-box JSON", Modifier.padding(top = 8.dp), fontSize = 10.sp,
                color = NeonPalette.accent4.copy(alpha = 0.8f))
        }
        VerticalSpacer(10)

        NeonCard(accent = NeonPalette.accent2, onClick = { onNavigate(AppRoute.Keypair) }) {
            Text(s["home_card_keypair"].orEmpty(), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = NeonPalette.accent2)
            Text(s["home_card_keypair_desc"].orEmpty(), Modifier.padding(top = 6.dp), fontSize = 12.sp, color = NeonPalette.textSecondary, lineHeight = 17.sp)
        }
        VerticalSpacer(10)

        NeonCard(accent = NeonPalette.amber, onClick = { onNavigate(AppRoute.Batch) }) {
            Text(s["home_card_batch"].orEmpty(), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = NeonPalette.amber)
            Text(s["home_card_batch_desc"].orEmpty(), Modifier.padding(top = 6.dp), fontSize = 12.sp, color = NeonPalette.textSecondary, lineHeight = 17.sp)
        }
        VerticalSpacer(24)
    }
}
