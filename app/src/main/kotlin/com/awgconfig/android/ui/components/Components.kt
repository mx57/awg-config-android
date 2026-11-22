package com.awgconfig.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.awgconfig.android.ui.theme.NeonPalette
import kotlinx.coroutines.delay

/** Карточка с неоновой рамкой и свечением. */
@Composable
fun NeonCard(
    modifier: Modifier = Modifier,
    accent: Color = NeonPalette.accent,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(NeonPalette.surface, NeonPalette.surfaceVariant)
                )
            )
            .border(1.dp, accent.copy(alpha = if (pressed) 0.9f else 0.35f), RoundedCornerShape(16.dp))
            .let { if (onClick != null) it.clickable { onClick() } else it }
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/** Заголовок секции. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier.padding(vertical = 4.dp),
        color = NeonPalette.textSecondary,
        fontSize = 12.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.Medium,
    )
}

/** Поле значения с кнопкой копирования (моно). */
@Composable
fun CopyField(
    value: String,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    onCopy: (String) -> Unit = {},
) {
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1200); copied = false } }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = NeonPalette.background,
        border = androidx.compose.foundation.BorderStroke(1.dp, NeonPalette.outline),
    ) {
        Row(
            Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                value,
                Modifier.weight(1f),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = NeonPalette.textPrimary,
                maxLines = maxLines,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(NeonPalette.surfaceVariant)
                    .clickable { copied = true; onCopy(value) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (copied) Icons.Filled.Done else Icons.Filled.ContentCopy,
                    contentDescription = "copy",
                    tint = if (copied) NeonPalette.accent2 else NeonPalette.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Бейдж-метка (endpoint, порт, профиль); кликабельный, если задан onClick. */
@Composable
fun Chip(
    text: String,
    accent: Color = NeonPalette.accent,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = 0.12f))
            .border(1.dp, accent.copy(alpha = if (onClick != null) 0.8f else 0.4f), RoundedCornerShape(8.dp))
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = accent,
        fontSize = 12.sp,
        fontFamily = FontFamily.Monospace,
    )
}

/** Строка «ключ — значение» в деталях. */
@Composable
fun KeyValueRow(key: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, color = NeonPalette.textSecondary, fontSize = 12.sp)
        Text(
            value,
            color = NeonPalette.textPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
        )
    }
}

/** Текст предупреждения. */
@Composable
fun WarningText(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .background(NeonPalette.amber, RoundedCornerShape(3.dp))
        )
        Text(
            text,
            Modifier.padding(start = 8.dp),
            color = NeonPalette.amber,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
    }
}
