package com.awgconfig.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awgconfig.android.AppViewModel
import com.awgconfig.android.GenState
import com.awgconfig.android.VerticalSpacer
import com.awgconfig.android.core.model.AwgProfileId
import com.awgconfig.android.core.model.AwgServerParams
import com.awgconfig.android.core.model.DeviceProfile
import com.awgconfig.android.core.model.DnsPreset
import com.awgconfig.android.i18n.LocalStrings
import com.awgconfig.android.ui.components.Chip
import com.awgconfig.android.ui.components.NeonCard
import com.awgconfig.android.ui.components.SectionTitle
import com.awgconfig.android.ui.components.WarningText
import com.awgconfig.android.ui.theme.NeonPalette

/** Экран конфига для собственного AWG-сервера. */
@Composable
fun AwgServerScreen(vm: AppViewModel, onResult: () -> Unit) {
    val s = LocalStrings.current

    var profile by rememberSaveable { mutableStateOf(AwgProfileId.AWG2) }
    var host by rememberSaveable { mutableStateOf("") }
    var port by rememberSaveable { mutableStateOf("51820") }
    var serverKey by rememberSaveable { mutableStateOf("") }
    var clientIp by rememberSaveable { mutableStateOf("10.8.1.2") }
    var allowed by rememberSaveable { mutableStateOf("0.0.0.0/0") }
    var psk by rememberSaveable { mutableStateOf("") }
    var dns by rememberSaveable { mutableStateOf(DnsPreset.CLOUDFLARE) }
    var keepalive by rememberSaveable { mutableStateOf("25") }
    var warpSafe by rememberSaveable { mutableStateOf(false) }
    var randomHeaders by rememberSaveable { mutableStateOf(true) }
    var jc by rememberSaveable { mutableStateOf("") }
    var jmin by rememberSaveable { mutableStateOf("") }
    var jmax by rememberSaveable { mutableStateOf("") }
    var device by rememberSaveable { mutableStateOf(DeviceProfile.UNIVERSAL) }
    var keyError by rememberSaveable { mutableStateOf(false) }

    val genState by vm.genState.collectAsState()
    val validKey = serverKey.isBlank() || (serverKey.length == 44 && serverKey.all { it in "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=" })

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(s["awg_profile_note"].orEmpty(), fontSize = 11.sp, color = NeonPalette.textSecondary)
        VerticalSpacer(8)

        SectionTitle(s["warp_choose_profile"].orEmpty())
        Row {
            for (p in listOf(AwgProfileId.LEGACY, AwgProfileId.AWG2, AwgProfileId.AWG3, AwgProfileId.AWG31)) {
                Chip(
                    p.displayVersion,
                    accent = if (profile == p) NeonPalette.accent else NeonPalette.textSecondary,
                    modifier = Modifier.padding(end = 6.dp),
                ) { profile = p }
            }
        }
        VerticalSpacer(12)

        NeonField(s["awg_host"].orEmpty(), host) { host = it }
        VerticalSpacer(8)
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonField(s["awg_port"].orEmpty(), port, Modifier.weight(1f), KeyboardType.Number) { port = it }
            Spacer(Modifier.padding(horizontal = 8.dp))
            NeonField(s["awg_keepalive"].orEmpty(), keepalive, Modifier.weight(1f), KeyboardType.Number) { keepalive = it }
        }
        VerticalSpacer(8)
        NeonField(s["awg_server_key"].orEmpty(), serverKey, error = !validKey) { serverKey = it.trim() }
        if (!validKey) {
            WarningText(s["awg_server_key_invalid"].orEmpty(), Modifier.padding(top = 4.dp))
        }
        VerticalSpacer(8)
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonField(s["awg_client_ip"].orEmpty(), clientIp, Modifier.weight(1f)) { clientIp = it }
            Spacer(Modifier.padding(horizontal = 8.dp))
            NeonField(s["awg_allowed"].orEmpty(), allowed, Modifier.weight(1f)) { allowed = it }
        }
        VerticalSpacer(8)
        NeonField(s["awg_psk"].orEmpty(), psk) { psk = it.trim() }
        VerticalSpacer(12)

        SectionTitle(s["warp_dns"].orEmpty())
        FlowChips(
            items = DnsPreset.entries.map { it.id to it.name.replace("_", " ") },
            selectedKeys = setOf(dns.id),
            onToggle = { key -> DnsPreset.fromId(key)?.let { dns = it } },
        )
        VerticalSpacer(12)

        ToggleRow(s["awg_warp_safe"].orEmpty(), s["awg_warp_safe_desc"].orEmpty(), warpSafe) { warpSafe = it }
        if (!warpSafe) {
            ToggleRow(s["awg_random_headers"].orEmpty(), s["awg_random_headers_desc"].orEmpty(), randomHeaders) { randomHeaders = it }
        }
        VerticalSpacer(8)
        Row(verticalAlignment = Alignment.CenterVertically) {
            NeonField("Jc", jc, Modifier.weight(1f), KeyboardType.Number) { jc = it }
            Spacer(Modifier.padding(horizontal = 6.dp))
            NeonField("Jmin", jmin, Modifier.weight(1f), KeyboardType.Number) { jmin = it }
            Spacer(Modifier.padding(horizontal = 6.dp))
            NeonField("Jmax", jmax, Modifier.weight(1f), KeyboardType.Number) { jmax = it }
        }
        Text(s["awg_custom_junk"].orEmpty(), Modifier.padding(top = 4.dp), fontSize = 10.sp, color = NeonPalette.textSecondary)
        VerticalSpacer(8)

        SectionTitle(s["warp_device"].orEmpty())
        ChipsRow(
            options = listOf(
                s["warp_device_universal"].orEmpty() to "",
                s["warp_device_mobile"].orEmpty() to "",
                s["warp_device_router"].orEmpty() to "",
            ),
            selected = device.ordinal,
            onSelected = { device = DeviceProfile.entries[it] },
        )
        VerticalSpacer(12)

        when (val st = genState) {
            is GenState.Failed -> {
                NeonCard(accent = NeonPalette.red) {
                    Text(s["gen_failed"].orEmpty(), color = NeonPalette.red, fontWeight = FontWeight.Bold)
                    Text(st.message, Modifier.padding(top = 6.dp), fontSize = 12.sp, color = NeonPalette.textSecondary)
                }
                VerticalSpacer(10)
            }
            is GenState.Done -> LaunchedEffectKey("awg-done") { onResult(); vm.resetGen() }
            else -> Unit
        }

        val canGenerate = host.isNotBlank() && serverKey.isNotBlank() && validKey
        NeonCard(
            accent = if (canGenerate) NeonPalette.accent else NeonPalette.outline,
            onClick = {
                if (!canGenerate || genState is GenState.Working) return@NeonCard
                keyError = false
                vm.resetGen()
                vm.generateAwgServer(
                    AwgServerParams(
                        profile = profile,
                        host = host.trim(),
                        port = port.toIntOrNull() ?: 51820,
                        serverPublicKey = serverKey.trim(),
                        clientIpv4 = clientIp.trim(),
                        dns = dns,
                        allowedIps = allowed.trim(),
                        psk = psk.takeIf { it.isNotBlank() },
                        keepalive = keepalive.toIntOrNull() ?: 25,
                        warpSafe = warpSafe,
                        jc = jc.toIntOrNull(), jmin = jmin.toIntOrNull(), jmax = jmax.toIntOrNull(),
                        useRandomHeaders = !warpSafe && randomHeaders,
                        device = device,
                    )
                )
            },
        ) {
            Text(
                s["action_generate"].orEmpty(),
                Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = if (canGenerate) NeonPalette.accent else NeonPalette.textSecondary,
            )
        }
        VerticalSpacer(28)
    }
}

/** Неоновое текстовое поле. */
@Composable
fun NeonField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    error: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, fontSize = 12.sp) },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            fontSize = 13.sp,
            color = NeonPalette.textPrimary,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = if (error) NeonPalette.red else NeonPalette.accent,
            unfocusedBorderColor = if (error) NeonPalette.red else NeonPalette.outline,
            focusedLabelColor = if (error) NeonPalette.red else NeonPalette.accent2,
            cursorColor = NeonPalette.accent2,
        ),
    )
}
