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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awgconfig.android.AppViewModel
import com.awgconfig.android.GenState
import com.awgconfig.android.VerticalSpacer
import com.awgconfig.android.core.model.MasqueParams
import com.awgconfig.android.i18n.LocalStrings
import com.awgconfig.android.ui.components.NeonCard
import com.awgconfig.android.ui.components.SectionTitle
import com.awgconfig.android.ui.components.WarningText
import com.awgconfig.android.ui.theme.NeonPalette

/** Экран MASQUE-генератора. */
@Composable
fun MasqueScreen(vm: AppViewModel, onResult: () -> Unit) {
    val s = LocalStrings.current
    var host by rememberSaveable { mutableStateOf("masque.cloudflare.com") }
    var port by rememberSaveable { mutableStateOf("443") }
    var sni by rememberSaveable { mutableStateOf("masque.cloudflare.com") }
    var token by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("Cloudflare MASQUE") }
    var singBox by rememberSaveable { mutableStateOf(true) }
    val genState by vm.genState.collectAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(s["masque_desc"].orEmpty(), fontSize = 11.sp, color = NeonPalette.textSecondary, lineHeight = 15.sp)
        VerticalSpacer(12)

        NeonField(s["masque_host"].orEmpty(), host) { host = it }
        VerticalSpacer(8)
        NeonField(s["masque_port"].orEmpty(), port, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number) { port = it }
        VerticalSpacer(8)
        NeonField(s["masque_sni"].orEmpty(), sni) { sni = it }
        VerticalSpacer(8)
        NeonField(s["masque_token"].orEmpty(), token) { token = it }
        VerticalSpacer(8)
        NeonField(s["masque_label"].orEmpty(), label) { label = it }
        VerticalSpacer(12)
        ToggleRow(s["masque_singbox"].orEmpty(), "", singBox) { singBox = it }
        VerticalSpacer(8)
        WarningText(s["masque_note"].orEmpty())
        VerticalSpacer(12)

        when (val st = genState) {
            is GenState.Done -> LaunchedEffectKey("masque-done") { onResult(); vm.resetGen() }
            else -> Unit
        }

        NeonCard(accent = NeonPalette.accent4, onClick = {
            if (genState is GenState.Working) return@NeonCard
            vm.resetGen()
            vm.generateMasque(
                MasqueParams(
                    host = host.trim(),
                    port = port.toIntOrNull() ?: 443,
                    sni = sni.trim(),
                    token = token.trim(),
                    label = label.ifBlank { "MASQUE" },
                    includeSingBox = singBox,
                )
            )
        }) {
            Text(
                s["action_generate"].orEmpty(),
                Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = NeonPalette.accent4,
            )
        }
        VerticalSpacer(28)
    }
}
