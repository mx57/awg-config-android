package com.awgconfig.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awgconfig.android.AppViewModel
import com.awgconfig.android.GenState
import com.awgconfig.android.VerticalSpacer
import com.awgconfig.android.i18n.LocalStrings
import com.awgconfig.android.ui.components.NeonCard
import com.awgconfig.android.ui.theme.NeonPalette

/** Экран быстрой генерации пары ключей X25519. */
@Composable
fun KeypairScreen(vm: AppViewModel, onResult: () -> Unit) {
    val s = LocalStrings.current
    val genState by vm.genState.collectAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(s["keypair_desc"].orEmpty(), fontSize = 11.sp, color = NeonPalette.textSecondary, lineHeight = 15.sp)
        VerticalSpacer(12)

        when (genState) {
            is GenState.Done -> LaunchedEffectKey("keypair-done") { onResult() }
            else -> Unit
        }

        NeonCard(accent = NeonPalette.accent2, onClick = {
            if (genState is GenState.Working) return@NeonCard
            vm.resetGen()
            vm.generateKeypair()
        }) {
            Text(
                s["action_generate"].orEmpty(),
                Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = NeonPalette.accent2,
            )
        }
        VerticalSpacer(28)
    }
}
