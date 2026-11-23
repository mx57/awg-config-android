package com.awgconfig.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awgconfig.android.AppViewModel
import com.awgconfig.android.VerticalSpacer
import com.awgconfig.android.core.model.HistoryEntry
import com.awgconfig.android.core.model.ProtocolKind
import com.awgconfig.android.core.model.TestReport
import com.awgconfig.android.core.model.TestVerdict
import com.awgconfig.android.i18n.LocalLang
import com.awgconfig.android.i18n.LocalStrings
import com.awgconfig.android.i18n.tr
import com.awgconfig.android.ui.components.Chip
import com.awgconfig.android.ui.components.NeonCard
import com.awgconfig.android.ui.components.SectionTitle
import com.awgconfig.android.ui.theme.NeonPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * «Мои конфиги»: все сгенерированные конфиги с быстрым и реальным
 * пакетным тестированием, ZIP-экспортом и удалением.
 */
@Composable
fun ConfigsScreen(vm: AppViewModel, onOpen: () -> Unit) {
    val s = LocalStrings.current
    val lang = LocalLang.current
    val context = LocalContext.current

    val tick by vm.historyTick.collectAsState()
    val entries = remember(tick) { vm.history.entries() }
    val reports by vm.testReports.collectAsState()
    val progress by vm.batchTest.collectAsState()

    var filter by rememberSaveable { mutableStateOf("all") }
    var selected by remember(tick) { mutableStateOf(setOf<String>()) }

    val filtered = remember(entries, filter) {
        when (filter) {
            "warp" -> entries.filter { it.protocolKind == ProtocolKind.WARP }
            "awg" -> entries.filter { it.protocolKind == ProtocolKind.AWG_SERVER }
            "masque" -> entries.filter { it.protocolKind == ProtocolKind.MASQUE }
            else -> entries
        }
    }
    val selectedConfigs = remember(entries, selected) {
        entries.filter { it.id in selected }.map { it.toGeneratedConfig() }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(s["configs_desc"].orEmpty(), fontSize = 11.sp, color = NeonPalette.textSecondary, lineHeight = 15.sp)
        VerticalSpacer(12)

        // Фильтры
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "all" to (s["configs_filter_all"].orEmpty() + " (${entries.size})"),
                "warp" to "WARP (${entries.count { it.protocolKind == ProtocolKind.WARP }})",
                "awg" to "AWG (${entries.count { it.protocolKind == ProtocolKind.AWG_SERVER }})",
                "masque" to "MASQUE (${entries.count { it.protocolKind == ProtocolKind.MASQUE }})",
            ).forEach { (key, label) ->
                Chip(
                    label,
                    accent = if (filter == key) NeonPalette.accent2 else NeonPalette.textSecondary,
                ) { filter = key }
            }
        }

        // Прогресс пакетного теста
        progress?.let { p ->
            VerticalSpacer(10)
            val accent = if (p.running) NeonPalette.accent3 else NeonPalette.accent2
            NeonCard(accent = accent) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (p.running) {
                        CircularProgressIndicator(
                            Modifier.size(16.dp).padding(end = 10.dp),
                            color = NeonPalette.accent3, strokeWidth = 2.dp,
                        )
                    }
                    Text(
                        tr(
                            s,
                            if (p.running) "configs_test_progress" else "configs_test_finished",
                            p.done, p.total,
                        ) + "  ·  " + if (p.real) s["check_real"].orEmpty() else s["check_quick"].orEmpty(),
                        fontSize = 13.sp, color = NeonPalette.textPrimary,
                    )
                }
                if (!p.running) {
                    Row(Modifier.padding(top = 6.dp)) {
                        Chip(s["action_close"].orEmpty(), accent = NeonPalette.textSecondary) { vm.clearBatchTest() }
                    }
                }
            }
        }

        VerticalSpacer(12)
        if (entries.isEmpty()) {
            Text(
                s["configs_empty"].orEmpty(),
                Modifier.padding(vertical = 24.dp),
                fontSize = 13.sp, color = NeonPalette.textSecondary,
            )
        } else {
            // Выделение: все/ничего + очистка истории
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Chip(s["configs_select_all"].orEmpty(), accent = NeonPalette.textSecondary) {
                    selected = if (selected.size == filtered.size) emptySet() else filtered.map { it.id }.toSet()
                }
                Chip(
                    s["configs_invert"].orEmpty(),
                    accent = NeonPalette.textSecondary,
                ) { selected = filtered.map { it.id }.filterNot { it in selected }.toSet() }
                Chip(s["action_clear"].orEmpty(), accent = NeonPalette.red) {
                    vm.clearHistory()
                    Toast.makeText(context, s["configs_cleared"].orEmpty(), Toast.LENGTH_SHORT).show()
                }
            }

            // Действия над выбором
            if (selected.isNotEmpty() && progress?.running != true) {
                VerticalSpacer(10)
                NeonCard(accent = NeonPalette.accent3) {
                    Text(
                        tr(s, "configs_selected", selected.size),
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeonPalette.accent3,
                    )
                    VerticalSpacer(6)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(s["check_quick"].orEmpty(), accent = NeonPalette.accent2) {
                            vm.batchTest(selectedConfigs, real = false)
                        }
                        Chip(s["check_real"].orEmpty(), accent = NeonPalette.accent3) {
                            vm.batchTest(selectedConfigs, real = true)
                        }
                    }
                    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(s["action_export_zip"].orEmpty(), accent = NeonPalette.accent) {
                            val name = vm.saveConfigsZip(selectedConfigs, "awg_configs")
                            Toast.makeText(
                                context,
                                if (name != null) "$name → Downloads" else (if (lang == "en") "Save failed" else "Не удалось сохранить"),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                        Chip(
                            s["action_delete"].orEmpty(),
                            accent = NeonPalette.red,
                        ) {
                            selected.forEach { vm.deleteHistoryEntry(it) }
                            selected = emptySet()
                        }
                    }
                    Text(
                        s["configs_real_warn"].orEmpty(),
                        Modifier.padding(top = 8.dp),
                        fontSize = 10.sp, color = NeonPalette.textSecondary, lineHeight = 14.sp,
                    )
                }
            }

            VerticalSpacer(10)

            // Список конфигов
            filtered.forEach { entry ->
                ConfigCard(
                    entry = entry,
                    report = reports[entry.id],
                    isSelected = entry.id in selected,
                    onToggleSelect = {
                        selected = if (entry.id in selected) selected - entry.id else selected + entry.id
                    },
                    onOpen = {
                        vm.openFromHistory(entry)
                        onOpen()
                    },
                )
                VerticalSpacer(8)
            }
        }
        VerticalSpacer(24)
    }
}

/** Карточка одного конфига: метаданные, вердикт последней проверки, выбор, открыть. */
@Composable
private fun ConfigCard(
    entry: HistoryEntry,
    report: TestReport?,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onOpen: () -> Unit,
) {
    val s = LocalStrings.current
    val lang = LocalLang.current
    val accent = when (entry.protocolKind) {
        ProtocolKind.WARP -> NeonPalette.accent3
        ProtocolKind.AWG_SERVER -> NeonPalette.accent
        ProtocolKind.MASQUE -> NeonPalette.accent4
        else -> NeonPalette.accent2
    }
    NeonCard(accent = if (isSelected) NeonPalette.accent2 else accent, onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Кружок выбора
            Row(
                Modifier.size(22.dp).background(
                    if (isSelected) NeonPalette.accent2 else NeonPalette.outline,
                    CircleShape,
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                if (isSelected) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(9.dp).background(NeonPalette.background, CircleShape)
                    )
                }
            }
            Text(
                "  " + entry.title,
                Modifier.weight(1f),
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeonPalette.textPrimary,
            )
            // Вердикт
            val verdict = report?.verdict
            if (verdict != null) {
                val (label, color) = when (verdict) {
                    TestVerdict.VALID -> s["check_verdict_valid"].orEmpty() to NeonPalette.accent2
                    TestVerdict.PARTIAL -> s["check_verdict_partial"].orEmpty() to NeonPalette.amber
                    TestVerdict.INVALID -> s["check_verdict_invalid"].orEmpty() to NeonPalette.red
                }
                Chip(label, accent = color)
            }
        }
        Text(
            SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(entry.createdAt)) +
                "  ·  " + (entry.details["endpoint"] ?: entry.details["clientIp"] ?: ""),
            Modifier.padding(top = 4.dp, start = 30.dp),
            fontSize = 11.sp, color = NeonPalette.textSecondary,
        )
        if (report != null) {
            val line = buildList {
                if (report.endpointLatencyMs != null) add("DNS ${report.endpointLatencyMs} ms")
                if (report.level == com.awgconfig.android.core.model.TestLevel.REAL && report.handshakeOk == true) {
                    add(s["check_handshake"].orEmpty() + " ✓")
                    add((if (lang == "en") "exit " else "выход ") + (report.exitIp ?: "?"))
                }
                if (report.structuralErrors.isNotEmpty()) add(report.structuralErrors.first())
                if (report.tunnelError != null) add(report.tunnelError)
            }.joinToString("  ·  ")
            if (line.isNotBlank()) {
                Text(
                    line,
                    Modifier.padding(top = 4.dp, start = 30.dp),
                    fontSize = 10.sp, fontFamily = FontFamily.Monospace,
                    color = when (report.verdict) {
                        TestVerdict.VALID -> NeonPalette.textSecondary
                        TestVerdict.PARTIAL -> NeonPalette.amber
                        TestVerdict.INVALID -> NeonPalette.red
                    },
                    lineHeight = 13.sp,
                )
            }
        }
    }
}
