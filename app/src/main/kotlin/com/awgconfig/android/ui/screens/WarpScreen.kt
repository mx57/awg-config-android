package com.awgconfig.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.awgconfig.android.core.model.CpsProtocol
import com.awgconfig.android.core.model.DeviceProfile
import com.awgconfig.android.core.model.DnsPreset
import com.awgconfig.android.core.model.EndpointMode
import com.awgconfig.android.core.model.RouteMode
import com.awgconfig.android.core.model.WarpGenParams
import com.awgconfig.android.core.routes.RouteCatalog
import com.awgconfig.android.core.warp.EndpointSelector
import com.awgconfig.android.i18n.LocalStrings
import com.awgconfig.android.i18n.tr
import com.awgconfig.android.ui.components.Chip
import com.awgconfig.android.ui.components.NeonCard
import com.awgconfig.android.ui.components.SectionTitle
import com.awgconfig.android.ui.components.WarningText
import com.awgconfig.android.ui.theme.NeonPalette

/** Экран генерации WARP-конфигов AmneziaWG. */
@Composable
fun WarpScreen(vm: AppViewModel, onResult: () -> Unit) {
    val s = LocalStrings.current

    var profile by rememberSaveable { mutableStateOf(AwgProfileId.AWG2) }
    var routeMode by rememberSaveable { mutableStateOf(RouteMode.FULL) }
    var presets by rememberSaveable { mutableStateOf(setOf<String>()) }
    var dns by rememberSaveable { mutableStateOf(DnsPreset.CLOUDFLARE) }
    var endpointMode by rememberSaveable { mutableStateOf(EndpointMode.HOSTNAME) }
    var manualIp by rememberSaveable { mutableStateOf(EndpointSelector.FALLBACK_ENDPOINTS.first().ip) }
    var port by rememberSaveable { mutableStateOf("4500") }
    var includeIpv6 by rememberSaveable { mutableStateOf(false) }
    var noLimit by rememberSaveable { mutableStateOf(false) }
    var device by rememberSaveable { mutableStateOf(DeviceProfile.UNIVERSAL) }
    var cps by rememberSaveable { mutableStateOf(CpsProtocol.AUTO) }
    var cps5 by rememberSaveable { mutableStateOf(false) }
    var count by rememberSaveable { mutableStateOf(1) }
    var wantVpnLink by rememberSaveable { mutableStateOf(true) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val genState by vm.genState.collectAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        SectionTitle(s["warp_choose_profile"].orEmpty())
        Text(s["warp_profile_desc"].orEmpty(), fontSize = 11.sp, color = NeonPalette.textSecondary, lineHeight = 15.sp)

        ProfileCard(
            title = s["warp_profile_legacy"].orEmpty(), desc = s["warp_profile_legacy_desc"].orEmpty(),
            badge = "1.5", selected = profile == AwgProfileId.LEGACY, accent = NeonPalette.textSecondary,
        ) { profile = AwgProfileId.LEGACY }
        ProfileCard(
            title = s["warp_profile_awg2"].orEmpty(), desc = s["warp_profile_awg2_desc"].orEmpty(),
            badge = "2.0", selected = profile == AwgProfileId.AWG2, accent = NeonPalette.accent3, recommended = true,
        ) { profile = AwgProfileId.AWG2 }
        ProfileCard(
            title = s["warp_profile_awg3"].orEmpty(), desc = s["warp_profile_awg3_desc"].orEmpty(),
            badge = "3.0", selected = profile == AwgProfileId.AWG3, accent = NeonPalette.accent,
        ) { profile = AwgProfileId.AWG3 }
        ProfileCard(
            title = s["warp_profile_awg31"].orEmpty(), desc = s["warp_profile_awg31_desc"].orEmpty(),
            badge = "3.1", selected = profile == AwgProfileId.AWG31, accent = NeonPalette.accent4,
        ) { profile = AwgProfileId.AWG31 }

        VerticalSpacer(12)
        SectionTitle(s["warp_settings"].orEmpty())
        TextButton(onClick = { showSettings = !showSettings }) {
            Text(
                if (showSettings) s["action_close"].orEmpty() else s["action_more"].orEmpty(),
                color = NeonPalette.accent2, fontSize = 13.sp,
            )
        }

        if (showSettings) {
            VerticalSpacer(4)

            // Маршрутизация
            SectionTitle(s["warp_routing"].orEmpty())
            ChipsRow(
                options = listOf(
                    s["warp_routing_full"].orEmpty() to s["warp_routing_full_desc"].orEmpty(),
                    s["warp_routing_split"].orEmpty() to s["warp_routing_split_desc"].orEmpty(),
                ),
                selected = routeMode.ordinal,
                onSelected = { routeMode = RouteMode.entries[it] },
            )

            if (routeMode == RouteMode.SPLIT) {
                VerticalSpacer(8)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { presets = RouteCatalog.QUICK_PICK.toSet() }) {
                        Text(s["warp_presets_quick"].orEmpty(), color = NeonPalette.accent2, fontSize = 12.sp)
                    }
                    TextButton(onClick = { presets = emptySet() }) {
                        Text(s["warp_presets_clear"].orEmpty(), color = NeonPalette.textSecondary, fontSize = 12.sp)
                    }
                }
                val selectedCount = presets.size
                Text(
                    tr(s, "warp_presets_empty") + " ($selectedCount)",
                    fontSize = 11.sp, color = NeonPalette.textSecondary,
                )
                VerticalSpacer(6)
                for (category in RouteCatalog.CATEGORY_ORDER) {
                    val inCategory = RouteCatalog.PRESETS.filter { it.category == category }
                    if (inCategory.isEmpty()) continue
                    SectionTitle(
                        if (LocalLangSafe() == "en") RouteCatalog.categoryEn(category) else RouteCatalog.categoryRu(category)
                    )
                    FlowChips(
                        items = inCategory.map { it.key to (if (LocalLangSafe() == "en") it.labelEn else it.labelRu) },
                        selectedKeys = presets,
                        onToggle = { key ->
                            presets = if (key in presets) presets - key else presets + key
                        },
                    )
                }
            }

            VerticalSpacer(12)
            // DNS
            SectionTitle(s["warp_dns"].orEmpty())
            FlowChips(
                items = DnsPreset.entries.map { it.id to it.name.replace("_", " ") },
                selectedKeys = setOf(dns.id),
                onToggle = { key -> DnsPreset.fromId(key)?.let { dns = it } },
            )

            VerticalSpacer(12)
            // Endpoint
            SectionTitle(s["warp_endpoint"].orEmpty())
            ChipsRow(
                options = listOf(
                    s["warp_endpoint_hostname"].orEmpty() to s["warp_endpoint_hostname_desc"].orEmpty(),
                    s["warp_endpoint_smart"].orEmpty() to s["warp_endpoint_smart_desc"].orEmpty(),
                    s["warp_endpoint_manual"].orEmpty() to s["warp_endpoint_manual_desc"].orEmpty(),
                ),
                selected = endpointMode.ordinal,
                onSelected = { endpointMode = EndpointMode.entries[it] },
            )
            if (endpointMode == EndpointMode.MANUAL) {
                FlowChips(
                    items = EndpointSelector.FALLBACK_ENDPOINTS.map { it.ip to it.ip },
                    selectedKeys = setOf(manualIp),
                    onToggle = { manualIp = it },
                )
            }

            // Порт
            SectionTitle(s["warp_port"].orEmpty())
            FlowChips(
                items = EndpointSelector.PORT_ALLOWLIST.map { it.toString() to it.toString() },
                selectedKeys = setOf(port),
                onToggle = { port = it },
            )

            VerticalSpacer(12)
            SectionTitle(s["warp_device"].orEmpty())
            ChipsRow(
                options = listOf(
                    s["warp_device_universal"].orEmpty() to s["warp_device_universal_desc"].orEmpty(),
                    s["warp_device_mobile"].orEmpty() to s["warp_device_mobile_desc"].orEmpty(),
                    s["warp_device_router"].orEmpty() to s["warp_device_router_desc"].orEmpty(),
                ),
                selected = device.ordinal,
                onSelected = { device = DeviceProfile.entries[it] },
            )

            VerticalSpacer(12)
            SectionTitle(s["warp_cps"].orEmpty())
            Text(s["warp_cps_desc"].orEmpty(), fontSize = 11.sp, color = NeonPalette.textSecondary)
            FlowChips(
                items = listOf(
                    CpsProtocol.AUTO to s["warp_cps_auto"].orEmpty(),
                    CpsProtocol.STATIC to s["warp_cps_static"].orEmpty(),
                    CpsProtocol.SIP to s["warp_cps_sip"].orEmpty(),
                    CpsProtocol.STUN to s["warp_cps_stun"].orEmpty(),
                    CpsProtocol.QUIC to s["warp_cps_quic"].orEmpty(),
                    CpsProtocol.DNS to s["warp_cps_dns"].orEmpty(),
                    CpsProtocol.DTLS to s["warp_cps_dtls"].orEmpty(),
                ).map { it.first.key to it.second },
                selectedKeys = setOf(cps.key),
                onToggle = { key -> CpsProtocol.fromKey(key)?.let { cps = it } },
            )
            ToggleRow(s["warp_cps5"].orEmpty(), s["warp_cps5_desc"].orEmpty(), cps5) { cps5 = it }

            VerticalSpacer(12)
            ToggleRow(s["warp_ipv6"].orEmpty(), s["warp_ipv6_desc"].orEmpty(), includeIpv6) { includeIpv6 = it }
            ToggleRow(s["warp_no_limit"].orEmpty(), s["warp_no_limit_desc"].orEmpty(), noLimit) { noLimit = it }
            ToggleRow(s["warp_vpnlink"].orEmpty(), "", wantVpnLink) { wantVpnLink = it }

            VerticalSpacer(12)
            SectionTitle(s["warp_count"].orEmpty())
            Text(s["warp_count_desc"].orEmpty(), fontSize = 11.sp, color = NeonPalette.textSecondary)
            FlowChips(
                items = listOf("1" to "1", "2" to "2", "3" to "3"),
                selectedKeys = setOf(count.toString()),
                onToggle = { count = it.toIntOrNull() ?: 1 },
            )
            VerticalSpacer(12)
        }

        VerticalSpacer(8)

        // Состояние генерации
        when (val st = genState) {
            is GenState.Working -> {
                NeonCard(accent = NeonPalette.accent3) {
                    Row {
                        CircularProgressIndicator(
                            Modifier.height(20.dp).padding(end = 12.dp),
                            color = NeonPalette.accent3, strokeWidth = 2.dp,
                        )
                        val stepText = listOf(
                            s["gen_step_register"].orEmpty(),
                            s["gen_step_endpoint"].orEmpty(),
                            s["gen_step_build"].orEmpty(),
                        ).getOrNull(st.step.coerceIn(0, 2)).orEmpty()
                        Text(stepText, fontSize = 13.sp, color = NeonPalette.textPrimary)
                    }
                }
                VerticalSpacer(10)
            }
            is GenState.Failed -> {
                NeonCard(accent = NeonPalette.red) {
                    Text(s["gen_failed"].orEmpty(), color = NeonPalette.red, fontWeight = FontWeight.Bold)
                    Text(st.message, Modifier.padding(top = 6.dp), fontSize = 12.sp, color = NeonPalette.textSecondary)
                    st.hint?.let {
                        Text(it, Modifier.padding(top = 6.dp), fontSize = 11.sp, color = NeonPalette.textSecondary)
                    }
                }
                VerticalSpacer(10)
            }
            is GenState.Done -> {
                LaunchedEffectKey("warp-done") { onResult() }
                NeonCard(accent = NeonPalette.accent2) {
                    Text(s["result_title"].orEmpty(), color = NeonPalette.accent2, fontWeight = FontWeight.Bold)
                }
                VerticalSpacer(10)
            }
            GenState.Idle -> Unit
        }

        // Кнопка генерации
        NeonCard(
            accent = NeonPalette.accent3,
            onClick = {
                if (genState is GenState.Working) return@NeonCard
                vm.resetGen()
                vm.generateWarp(
                    WarpGenParams(
                        profile = profile,
                        endpointMode = endpointMode,
                        manualEndpointIp = if (endpointMode == EndpointMode.MANUAL) manualIp else null,
                        port = port.toIntOrNull() ?: 4500,
                        dns = dns,
                        routeMode = routeMode,
                        presets = presets,
                        includeIpv6 = includeIpv6,
                        noRouteLimit = noLimit,
                        device = device,
                        cpsProtocol = cps,
                        cps5 = cps5,
                        count = count,
                        wantVpnLink = wantVpnLink,
                    )
                )
            },
        ) {
            Text(
                if (genState is GenState.Working) s["action_generating"].orEmpty() else s["action_generate"].orEmpty(),
                Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NeonPalette.accent3,
            )
        }
        VerticalSpacer(28)
    }
}

/** Локальный язык без композиции (для НЕ-composable веток). */
@Composable
fun LocalLangSafe(): String {
    return com.awgconfig.android.i18n.LocalLang.current
}

/** Карточка профиля. */
@Composable
private fun ProfileCard(
    title: String,
    desc: String,
    badge: String,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    recommended: Boolean = false,
    onClick: () -> Unit,
) {
    val s = LocalStrings.current
    NeonCard(
        accent = if (selected) accent else NeonPalette.outline,
        onClick = onClick,
    ) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Chip(badge, accent = if (selected) accent else NeonPalette.textSecondary)
            Spacer(Modifier.weight(1f))
        }
        Text(
            title + if (recommended) " ★" else "",
            Modifier.padding(top = 8.dp),
            fontSize = 16.sp, fontWeight = FontWeight.Bold,
            color = if (selected) accent else NeonPalette.textPrimary,
        )
        Text(desc, Modifier.padding(top = 4.dp), fontSize = 11.sp, color = NeonPalette.textSecondary, lineHeight = 15.sp)
    }
}

/** Строка переключателей с описанием. */
@Composable
fun ToggleRow(title: String, desc: String, value: Boolean, onChange: (Boolean) -> Unit) {
    NeonCard(accent = if (value) NeonPalette.accent2 else NeonPalette.outline, onClick = { onChange(!value) }) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = NeonPalette.textPrimary)
                if (desc.isNotBlank()) {
                    Text(desc, Modifier.padding(top = 3.dp), fontSize = 11.sp, color = NeonPalette.textSecondary, lineHeight = 15.sp)
                }
            }
            Box(
                Modifier
                    .padding(start = 8.dp)
                    .size(18.dp)
                    .background(
                        if (value) NeonPalette.accent2 else NeonPalette.outline,
                        androidx.compose.foundation.shape.CircleShape,
                    ),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                if (value) {
                    Box(
                        Modifier.size(8.dp).background(NeonPalette.background, androidx.compose.foundation.shape.CircleShape)
                    )
                }
            }
        }
    }
}

/** Горизонтальный выбор сегментов (2–3 опции с описанием выбранной). */
@Composable
fun ChipsRow(
    options: List<Pair<String, String>>,
    selected: Int,
    onSelected: (Int) -> Unit,
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, (label, _) ->
                Chip(
                    label,
                    accent = if (index == selected) NeonPalette.accent2 else NeonPalette.textSecondary,
                ) { onSelected(index) }
            }
        }
        options.getOrNull(selected)?.second?.let {
            Text(it, Modifier.padding(top = 6.dp), fontSize = 11.sp, color = NeonPalette.textSecondary, lineHeight = 15.sp)
        }
    }
}

/** Сетка кликабельных чипов с выбором (одиночным или множественным). */
@Composable
fun FlowChips(
    items: List<Pair<String, String>>,
    selectedKeys: Set<String>,
    onToggle: (String) -> Unit,
    accent: androidx.compose.ui.graphics.Color = NeonPalette.accent,
) {
    // группы по 3 в ряд на узких экранах; FlowRow недоступен без experimental API
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowItems.forEach { (key, label) ->
                    val selected = key in selectedKeys
                    Chip(
                        (if (selected) "● " else "") + label,
                        accent = if (selected) accent else NeonPalette.textSecondary,
                        onClick = { onToggle(key) },
                    )
                }
            }
        }
    }
}

/** Утилита одноразового эффекта с ключом. */
@Composable
fun LaunchedEffectKey(key: String, effect: suspend () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(key) { effect() }
}
