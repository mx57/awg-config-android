package com.awgconfig.android.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awgconfig.android.AppViewModel
import com.awgconfig.android.VerticalSpacer
import com.awgconfig.android.core.model.TestLevel
import com.awgconfig.android.core.model.TestReport
import com.awgconfig.android.core.model.TestVerdict
import com.awgconfig.android.i18n.LocalStrings
import com.awgconfig.android.i18n.tr
import com.awgconfig.android.ui.components.Chip
import com.awgconfig.android.ui.components.CopyField
import com.awgconfig.android.ui.components.KeyValueRow
import com.awgconfig.android.ui.components.NeonCard
import com.awgconfig.android.ui.components.SectionTitle
import com.awgconfig.android.ui.components.WarningText
import com.awgconfig.android.ui.qr.QrCode
import com.awgconfig.android.ui.theme.NeonPalette

/** Экран результата: превью, QR, экспорт, vpn://, подключение туннеля. */
@Composable
fun ResultScreen(vm: AppViewModel, back: AppRoute?) {
    val s = LocalStrings.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    val outcome by vm.lastOutcome.collectAsState()
    val index by vm.resultIndex.collectAsState()
    val tunnelState by vm.tunnel.state.collectAsState()
    var qrContent by remember { mutableStateOf<String?>(null) }
    val lang = com.awgconfig.android.i18n.LocalLang.current
    val hidePriv = vm.settings.hidePrivateKey

    val configs = outcome?.configs.orEmpty()
    if (configs.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(s["gen_failed"].orEmpty(), color = NeonPalette.textSecondary)
        }
        return
    }
    val config = configs[index.coerceIn(0, configs.size - 1)]

    val detailsLabel = mapOf(
        "profile" to (if (lang == "en") "Profile" else "Профиль"),
        "endpoint" to (if (lang == "en") "Endpoint" else "Endpoint"),
        "endpointSource" to (if (lang == "en") "Endpoint source" else "Источник endpoint"),
        "dns" to "DNS",
        "routes" to (if (lang == "en") "Routes" else "Маршруты"),
        "device" to (if (lang == "en") "Device" else "Устройство"),
        "cps" to "CPS",
        "keepalive" to "Keepalive",
        "clientIp" to (if (lang == "en") "Client IP" else "IP клиента"),
        "sni" to "SNI",
        "token" to (if (lang == "en") "Token" else "Токен"),
        "publicKey" to (if (lang == "en") "Public key" else "Публичный ключ"),
    )

    fun displayText(): String {
        if (!hidePriv) return config.text
        return config.text.lineSequence().joinToString("\n") { line ->
            if (line.trim().startsWith("PrivateKey =")) "PrivateKey = ████…████" else line
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // Пагинация пакета
        if (configs.size > 1) {
            Row {
                Text(
                    tr(s, "result_batch", index + 1, configs.size),
                    Modifier.weight(1f),
                    fontSize = 12.sp, color = NeonPalette.textSecondary,
                )
            }
            Row {
                Chip("←", accent = NeonPalette.accent, modifier = Modifier.padding(end = 6.dp)) {
                    vm.selectResult(((index - 1 + configs.size) % configs.size))
                }
                Chip("→", accent = NeonPalette.accent) {
                    vm.selectResult(((index + 1) % configs.size))
                }
            }
            VerticalSpacer(10)
        }

        Text(
            config.title,
            fontSize = 18.sp, fontWeight = FontWeight.Bold,
            color = NeonPalette.textPrimary,
        )
        config.details["endpoint"]?.let {
            Text(it, fontSize = 12.sp, color = NeonPalette.accent3, fontFamily = FontFamily.Monospace)
        }

        VerticalSpacer(12)

        // Превью конфига
        SectionTitle(s["result_preview"].orEmpty())
        NeonCard(accent = NeonPalette.accent) {
            SelectionContainer {
                Text(
                    displayText(),
                    Modifier.heightIn(max = 320.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = NeonPalette.textPrimary,
                )
            }
            if (hidePriv) {
                Text(
                    s["result_preview_hidden"].orEmpty(),
                    Modifier.padding(top = 8.dp),
                    fontSize = 10.sp, color = NeonPalette.textSecondary,
                )
            }
        }
        VerticalSpacer(12)

        // Действия
        SectionTitle(s["action_more"].orEmpty())
        Row {
            Chip(s["action_copy"].orEmpty(), accent = NeonPalette.accent2) {
                clipboard.setText(AnnotatedString(config.text))
                Toast.makeText(context, s["action_copied"], Toast.LENGTH_SHORT).show()
            }
        }
        Row(Modifier.padding(top = 6.dp)) {
            Chip(s["action_save"].orEmpty(), accent = NeonPalette.accent) {
                val name = vm.saveConfigFile(config)
                Toast.makeText(
                    context,
                    if (name != null) "$name → Downloads" else (if (lang == "en") "Save failed" else "Не удалось сохранить"),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
        Row(Modifier.padding(top = 6.dp)) {
            Chip(s["action_share"].orEmpty(), accent = NeonPalette.accent3) {
                context.startActivity(
                    android.content.Intent.createChooser(
                        vm.shareIntent(config.title, config.text),
                        config.title,
                    )
                )
            }
        }
        Row(Modifier.padding(top = 6.dp)) {
            Chip(s["action_qr"].orEmpty(), accent = NeonPalette.accent) {
                if (config.text.length > 2900) {
                    Toast.makeText(context, s["qr_too_long"], Toast.LENGTH_LONG).show()
                } else {
                    qrContent = config.text
                }
            }
        }

        // ── Проверка валидности (реальная) ──────────────────────────────
        VerticalSpacer(12)
        SectionTitle(s["check_title"].orEmpty())
        val test by vm.currentTest.collectAsState()
        var testing by remember(config.id) { mutableStateOf(false) }
        if (test != null && test?.configId != config.id) testing = false
        when {
            test != null && test?.configId == config.id -> TestReportCard(s, lang, test!!)
            testing -> NeonCard(accent = NeonPalette.accent3) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(
                        Modifier.height(18.dp).padding(end = 10.dp),
                        color = NeonPalette.accent3, strokeWidth = 2.dp,
                    )
                    Text(s["check_running"].orEmpty(), fontSize = 13.sp, color = NeonPalette.textPrimary)
                }
            }
            else -> NeonCard(accent = NeonPalette.outline) {
                Text(
                    s["check_desc"].orEmpty(),
                    fontSize = 11.sp, color = NeonPalette.textSecondary, lineHeight = 15.sp,
                )
                Row(Modifier.padding(top = 8.dp)) {
                    Chip(s["check_quick"].orEmpty(), accent = NeonPalette.accent2) {
                        testing = true
                        vm.testQuick(config)
                    }
                    Chip(
                        s["check_real"].orEmpty(),
                        accent = NeonPalette.accent3,
                        modifier = Modifier.padding(start = 6.dp),
                    ) {
                        testing = true
                        vm.testReal(config)
                    }
                }
                Text(
                    s["check_real_note"].orEmpty(),
                    Modifier.padding(top = 6.dp),
                    fontSize = 10.sp, color = NeonPalette.textSecondary, lineHeight = 14.sp,
                )
            }
        }

        // vpn://-ссылка
        config.vpnLink?.let { link ->
            VerticalSpacer(12)
            SectionTitle(s["result_vpn_link"].orEmpty())
            CopyField(value = link, maxLines = 2, onCopy = {
                Toast.makeText(context, s["action_copied"], Toast.LENGTH_SHORT).show()
            })
            Row(Modifier.padding(top = 6.dp)) {
                Chip("QR vpn://", accent = NeonPalette.accent) { qrContent = link }
            }
        }
        if (config.protocolKind == com.awgconfig.android.core.model.ProtocolKind.WARP &&
            config.vpnLink == null && vm.settings.hidePrivateKey.not()
        ) {
            WarningText(s["result_vpn_unavailable"].orEmpty(), Modifier.padding(top = 6.dp))
        }

        // Подключение встроенным туннелем (движок AmneziaWG: WARP и собственные AWG-серверы)
        if (config.tunnelCapable) {
            VerticalSpacer(12)
            NeonCard(accent = NeonPalette.accent2, onClick = { vm.requestConnect(config) }) {
                Text(
                    s["action_connect"].orEmpty(),
                    Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = NeonPalette.accent2,
                )
                Text(
                    s["result_connect_hint"].orEmpty(),
                    Modifier.padding(top = 6.dp), textAlign = TextAlign.Center,
                    fontSize = 10.sp, color = NeonPalette.textSecondary,
                )
            }
        } else if (config.protocolKind == com.awgconfig.android.core.model.ProtocolKind.MASQUE) {
            VerticalSpacer(12)
            WarningText(s["result_connect_masque_hint"].orEmpty())
        }

        // Пакет: ZIP + отправить все
        if (configs.size > 1) {
            VerticalSpacer(12)
            SectionTitle(s["action_more"].orEmpty())
            Row {
                Chip(s["action_zip"].orEmpty(), accent = NeonPalette.amber) {
                    val name = vm.saveBatchZip(outcome!!, config.title.take(8))
                    Toast.makeText(
                        context,
                        if (name != null) "$name → Downloads" else (if (lang == "en") "Save failed" else "Не удалось сохранить"),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            Row(Modifier.padding(top = 6.dp)) {
                Chip(s["action_share_all"].orEmpty(), accent = NeonPalette.amber) {
                    val all = configs.joinToString("\n\n\n") { it.text }
                    context.startActivity(
                        android.content.Intent.createChooser(
                            vm.shareIntent("${configs.size} × ${config.title}", all),
                            config.title,
                        )
                    )
                }
            }
        }

        // Детали
        VerticalSpacer(12)
        SectionTitle(s["result_details"].orEmpty())
        NeonCard(accent = NeonPalette.outline) {
            config.details.forEach { (key, value) ->
                KeyValueRow(detailsLabel[key] ?: key, value)
            }
        }

        // Предупреждения
        if (config.warnings.isNotEmpty() || (outcome?.warnings ?: emptyList()).isNotEmpty()) {
            VerticalSpacer(12)
            SectionTitle(s["result_warnings"].orEmpty())
            val all = config.warnings + (outcome?.warnings ?: emptyList())
            all.distinct().forEach {
                WarningText(it, Modifier.padding(bottom = 6.dp))
            }
        }

        VerticalSpacer(28)
    }

    // Диалог QR
    qrContent?.let { content ->
        AlertDialog(
            onDismissRequest = { qrContent = null },
            title = { Text(s["qr_title"].orEmpty(), fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    QrCode(content)
                    Text(
                        s["qr_hint"].orEmpty(),
                        Modifier.padding(top = 10.dp),
                        fontSize = 11.sp, color = NeonPalette.textSecondary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { qrContent = null }) {
                    Text(s["action_close"].orEmpty(), color = NeonPalette.accent2)
                }
            },
            containerColor = NeonPalette.surface,
            titleContentColor = NeonPalette.textPrimary,
            textContentColor = NeonPalette.textSecondary,
        )
    }
}

/** Карточка отчёта проверки: чек-лист по уровням. */
@Composable
private fun TestReportCard(s: Map<String, String>, lang: String, t: TestReport) {
    val verdictColor = when (t.verdict) {
        TestVerdict.VALID -> NeonPalette.accent2
        TestVerdict.PARTIAL -> NeonPalette.amber
        TestVerdict.INVALID -> NeonPalette.red
    }
    val verdictLabel = when (t.verdict) {
        TestVerdict.VALID -> s["check_verdict_valid"].orEmpty()
        TestVerdict.PARTIAL -> s["check_verdict_partial"].orEmpty()
        TestVerdict.INVALID -> s["check_verdict_invalid"].orEmpty()
    }
    NeonCard(accent = verdictColor) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Box(
                Modifier.size(10.dp).background(verdictColor, androidx.compose.foundation.shape.CircleShape)
            )
            val levelLabel = if (lang == "en") {
                if (t.level == TestLevel.REAL) "real check" else "quick check"
            } else {
                "проверка: " + if (t.level == TestLevel.REAL) "реальная" else "быстрая"
            }
            Text(
                "  $verdictLabel  ·  $levelLabel",
                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = verdictColor,
            )
        }
        VerticalSpacer(8)
        CheckRow(s["check_structure"].orEmpty(), t.structuralOk, t.structuralErrors.joinToString("; "))
        if (t.endpoint != null) {
            CheckRow(
                s["check_endpoint"].orEmpty() + " · " + t.endpoint,
                t.dnsOk,
                t.endpointLatencyMs?.let { "$it ms" } ?: t.endpointError,
            )
        }
        if (t.level == TestLevel.REAL && !t.tunnelAttempted) {
            Text(
                s["check_na_tunnel"].orEmpty(),
                Modifier.padding(top = 6.dp),
                fontSize = 11.sp, color = NeonPalette.textSecondary, lineHeight = 15.sp,
            )
        }
        if (t.tunnelAttempted) {
            CheckRow(s["check_tunnel"].orEmpty(), t.tunnelUp, t.tunnelError)
            if (t.tunnelUp == true) {
                CheckRow(s["check_handshake"].orEmpty(), t.handshakeOk, null)
                CheckRow(s["check_http"].orEmpty(), t.httpOk, null)
                CheckRow(
                    s["check_exit_ip"].orEmpty() + ": ${t.directIp ?: "?"} → ${t.exitIp ?: "?"}",
                    if (t.fullTunnel == true) (t.exitIp != null && t.directIp != null && t.exitIp != t.directIp) else null,
                    if (t.fullTunnel != true) s["check_split_note"].orEmpty() else null,
                )
                if (t.warpOn != null && t.exitIp != null) {
                    CheckRow(
                        "WARP: " + if (t.warpOn == true) "on" else "off",
                        null,
                        if (t.warpOn != true) s["check_warp_off_note"].orEmpty() else null,
                    )
                }
            }
        }
    }
}

/** Строка чек-листа: ✓/✗/— + подпись + деталь. */
@Composable
private fun CheckRow(label: String, ok: Boolean?, detail: String?) {
    val mark = when (ok) {
        true -> "✓"
        false -> "✗"
        null -> "—"
    }
    val color = when (ok) {
        true -> NeonPalette.accent2
        false -> NeonPalette.red
        null -> NeonPalette.textSecondary
    }
    Row(Modifier.padding(top = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.Top) {
        Text(mark, fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold)
        Column(Modifier.padding(start = 8.dp)) {
            Text(label, fontSize = 12.sp, color = NeonPalette.textPrimary)
            detail?.takeIf { it.isNotBlank() }?.let {
                Text(it, fontSize = 10.sp, color = NeonPalette.textSecondary, lineHeight = 13.sp)
            }
        }
    }
}
