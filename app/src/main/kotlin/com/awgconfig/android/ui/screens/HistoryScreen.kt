package com.awgconfig.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awgconfig.android.AppViewModel
import com.awgconfig.android.VerticalSpacer
import com.awgconfig.android.i18n.LocalStrings
import com.awgconfig.android.ui.components.NeonCard
import com.awgconfig.android.ui.theme.NeonPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** История генераций (до 20 записей, локально). */
@Composable
fun HistoryScreen(vm: AppViewModel, onOpen: () -> Unit) {
    val s = LocalStrings.current
    val entries = vm.history.entries()

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(s["history_desc"].orEmpty(), fontSize = 11.sp, color = NeonPalette.textSecondary)
        VerticalSpacer(12)

        if (entries.isEmpty()) {
            Text(
                s["history_empty"].orEmpty(),
                Modifier.padding(vertical = 24.dp),
                fontSize = 13.sp, color = NeonPalette.textSecondary,
            )
        } else {
            Row {
                TextButton(onClick = { vm.history.clear() }) {
                    Text(s["action_clear"].orEmpty(), color = NeonPalette.red, fontSize = 12.sp)
                }
            }
            LazyColumn(state = rememberLazyListState()) {
                itemsIndexed(entries) { index, entry ->
                    NeonCard(
                        accent = when (entry.protocolKind) {
                            com.awgconfig.android.core.model.ProtocolKind.WARP -> NeonPalette.accent3
                            com.awgconfig.android.core.model.ProtocolKind.AWG_SERVER -> NeonPalette.accent
                            com.awgconfig.android.core.model.ProtocolKind.MASQUE -> NeonPalette.accent4
                            else -> NeonPalette.accent2
                        },
                        onClick = {
                            vm.openFromHistory(entry.toGeneratedConfig("history-$index"))
                            onOpen()
                        },
                    ) {
                        Text(entry.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NeonPalette.textPrimary)
                        Text(
                            SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.getDefault()).format(Date(entry.createdAt)) +
                                "  ·  " + (entry.details["endpoint"] ?: entry.details["clientIp"] ?: ""),
                            Modifier.padding(top = 4.dp),
                            fontSize = 11.sp, color = NeonPalette.textSecondary,
                        )
                    }
                    VerticalSpacer(8)
                }
            }
        }
    }
}
