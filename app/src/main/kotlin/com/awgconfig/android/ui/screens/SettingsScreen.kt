package com.awgconfig.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awgconfig.android.AppViewModel
import com.awgconfig.android.VerticalSpacer
import com.awgconfig.android.core.model.DnsPreset
import com.awgconfig.android.i18n.LocalStrings
import com.awgconfig.android.ui.components.Chip
import com.awgconfig.android.ui.components.NeonCard
import com.awgconfig.android.ui.components.SectionTitle
import com.awgconfig.android.ui.theme.NeonPalette

/** Настройки: язык, DNS по умолчанию, приватность, о приложении. */
@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s = LocalStrings.current
    val lang by vm.lang.collectAsState()

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        SectionTitle(s["settings_language"].orEmpty())
        Row {
            Chip("Русский", accent = if (lang == "ru") NeonPalette.accent2 else NeonPalette.textSecondary) {
                vm.setLanguage("ru")
            }
            Spacer(Modifier.padding(horizontal = 4.dp))
            Chip("English", accent = if (lang == "en") NeonPalette.accent2 else NeonPalette.textSecondary) {
                vm.setLanguage("en")
            }
        }

        VerticalSpacer(12)
        SectionTitle(s["settings_dns_default"].orEmpty())
        FlowChips(
            items = DnsPreset.entries.map { it.id to it.name.replace("_", " ") },
            selectedKeys = setOf(vm.settings.defaultDns),
            onToggle = { key -> vm.settings.defaultDns = key },
        )

        VerticalSpacer(12)
        ToggleRow(
            s["settings_hide_priv"].orEmpty(),
            s["result_preview_hidden"].orEmpty(),
            vm.settings.hidePrivateKey,
        ) { vm.settings.hidePrivateKey = it }

        VerticalSpacer(12)
        SectionTitle(s["settings_about"].orEmpty())
        NeonCard(accent = NeonPalette.outline) {
            Text(s["settings_about_text"].orEmpty(), fontSize = 11.sp, color = NeonPalette.textSecondary, lineHeight = 17.sp)
        }

        VerticalSpacer(12)
        SectionTitle(s["settings_licenses"].orEmpty())
        NeonCard(accent = NeonPalette.outline) {
            Text(
                "amnezia-config-gen / awgconfig.com — AGPL-3.0\n" +
                    "wireguard-android / awg-android (Curve25519, GoBackend) — Apache-2.0 WireGuard LLC\n" +
                    "ZXing (QR) — Apache-2.0\n" +
                    "Kotlin · Jetpack Compose · Material 3",
                fontSize = 11.sp, color = NeonPalette.textSecondary, lineHeight = 17.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            )
        }
        VerticalSpacer(24)
    }
}
