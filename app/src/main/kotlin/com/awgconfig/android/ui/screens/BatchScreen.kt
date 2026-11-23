package com.awgconfig.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.awgconfig.android.core.model.AwgProfileId
import com.awgconfig.android.core.model.AwgServerParams
import com.awgconfig.android.core.model.MasqueParams
import com.awgconfig.android.i18n.LocalStrings
import com.awgconfig.android.i18n.tr
import com.awgconfig.android.ui.components.Chip
import com.awgconfig.android.ui.components.NeonCard
import com.awgconfig.android.ui.components.SectionTitle
import com.awgconfig.android.ui.components.WarningText
import com.awgconfig.android.ui.theme.NeonPalette

/** Экран пакетной генерации. */
@Composable
fun BatchScreen(vm: AppViewModel, onResult: () -> Unit) {
    val s = LocalStrings.current

    var kind by rememberSaveable { mutableStateOf("awg") }
    var count by rememberSaveable { mutableStateOf(10) }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("51820") }
    var serverKey by rememberSaveable { mutableStateOf("") }
    var clientIp by rememberSaveable { mutableStateOf("10.8.1.2") }
    var masqueHost by rememberSaveable { mutableStateOf("masque.cloudflare.com") }
    var masqueSni by rememberSaveable { mutableStateOf("masque.cloudflare.com") }

    val genState by vm.genState.collectAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(s["batch_desc"].orEmpty(), fontSize = 11.sp, color = NeonPalette.textSecondary, lineHeight = 15.sp)
        VerticalSpacer(12)

        SectionTitle(s["batch_kind"].orEmpty())
        Row {
            Chip("AWG", accent = if (kind == "awg") NeonPalette.accent else NeonPalette.textSecondary, modifier = Modifier.padding(end = 6.dp)) { kind = "awg" }
            Chip("MASQUE", accent = if (kind == "masque") NeonPalette.accent4 else NeonPalette.textSecondary, modifier = Modifier.padding(end = 6.dp)) { kind = "masque" }
            Chip("X25519", accent = if (kind == "keypair") NeonPalette.accent2 else NeonPalette.textSecondary) { kind = "keypair" }
        }
        VerticalSpacer(8)
        WarningText(
            if (kind == "keypair") s["batch_awg_note"].orEmpty() else s["batch_awg_note"].orEmpty()
        )
        VerticalSpacer(12)

        SectionTitle(tr(s, "batch_count"))
        Row {
            listOf(5, 10, 25, 50, 100).forEach { c ->
                Chip(
                    c.toString(),
                    accent = if (count == c) NeonPalette.amber else NeonPalette.textSecondary,
                    modifier = Modifier.padding(end = 6.dp),
                ) { count = c }
            }
        }
        VerticalSpacer(12)

        when (kind) {
            "awg" -> {
                NeonField(s["awg_host"].orEmpty(), host) { host = it }
                VerticalSpacer(8)
                NeonField(s["awg_port"].orEmpty(), port, keyboardType = androidx.compose.ui.text.input.KeyboardType.Number) { port = it }
                VerticalSpacer(8)
                NeonField(s["awg_server_key"].orEmpty(), serverKey) { serverKey = it.trim() }
                VerticalSpacer(8)
                NeonField(s["awg_client_ip"].orEmpty(), clientIp) { clientIp = it }
            }
            "masque" -> {
                NeonField(s["masque_host"].orEmpty(), masqueHost) { masqueHost = it }
                VerticalSpacer(8)
                NeonField(s["masque_sni"].orEmpty(), masqueSni) { masqueSni = it }
            }
            else -> {}
        }

        VerticalSpacer(12)
        when (genState) {
            is GenState.Done -> LaunchedEffectKey("batch-done") { onResult(); vm.resetGen() }
            else -> Unit
        }

        val canGenerate = when (kind) {
            "awg" -> host.isNotBlank() && serverKey.isNotBlank()
            "masque" -> masqueHost.isNotBlank()
            else -> true
        }

        NeonCard(
            accent = if (canGenerate) NeonPalette.amber else NeonPalette.outline,
            onClick = {
                if (!canGenerate || genState is GenState.Working) return@NeonCard
                vm.resetGen()
                when (kind) {
                    "awg" -> vm.batchAwg(
                        AwgServerParams(
                            profile = AwgProfileId.AWG2,
                            host = host.trim(),
                            port = port.toIntOrNull() ?: 51820,
                            serverPublicKey = serverKey.trim(),
                            clientIpv4 = clientIp.trim(),
                        ),
                        count,
                    )
                    "masque" -> vm.batchMasque(
                        MasqueParams(host = masqueHost.trim(), sni = masqueSni.trim()),
                        count,
                    )
                    else -> vm.batchKeypair(count)
                }
            },
        ) {
            Text(
                s["action_generate"].orEmpty(),
                Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = if (canGenerate) NeonPalette.amber else NeonPalette.textSecondary,
            )
        }
        VerticalSpacer(28)
    }
}
