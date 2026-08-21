@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.vpnmonitor.tv.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.*
import com.vpnmonitor.tv.ui.theme.VpnColors
import com.vpnmonitor.tv.ui.theme.VpnTypography
import kotlin.math.roundToInt

@Composable
fun GradientTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = VpnTypography.DisplayMedium.copy(
            brush = Brush.horizontalGradient(
                colors = listOf(VpnColors.GradientStart, VpnColors.GradientEnd)
            )
        ),
        modifier = modifier
    )
}

@Composable
fun StatusIndicator(status: String, modifier: Modifier = Modifier) {
    val (color, glowColor) = when (status) {
        "up" -> VpnColors.Success to VpnColors.SuccessGlow
        "down" -> VpnColors.Danger to VpnColors.DangerGlow
        "degraded" -> VpnColors.Warning to VpnColors.WarningGlow
        else -> VpnColors.TextMuted to VpnColors.TextMuted
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = if (status == "down") 1f else 0.7f,
        animationSpec = infiniteRepeatable(animation = tween(1100), repeatMode = RepeatMode.Reverse),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .size(14.dp)
            .drawBehind {
                drawCircle(color = color.copy(alpha = 0.25f), radius = size.width * 0.6f)
                drawCircle(
                    color = glowColor.copy(alpha = alpha * 0.8f),
                    radius = size.width * 0.4f
                )
                drawCircle(color = color, radius = size.width * 0.3f)
            }
    )
}

@Composable
fun ServerCard(
    name: String,
    location: String,
    status: String,
    vlessOk: Boolean,
    pingMs: Float?,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.02f else 1f, label = "cardScale")

    val cardColor = when (status) {
        "up" -> VpnColors.Success.copy(alpha = 0.06f)
        "down" -> VpnColors.Danger.copy(alpha = 0.06f)
        "degraded" -> VpnColors.Warning.copy(alpha = 0.06f)
        else -> VpnColors.Surface
    }

    val borderColor = when {
        isFocused -> VpnColors.Primary
        status == "up" -> VpnColors.Success.copy(alpha = 0.35f)
        status == "down" -> VpnColors.Danger.copy(alpha = 0.35f)
        status == "degraded" -> VpnColors.Warning.copy(alpha = 0.35f)
        else -> VpnColors.CardBorder
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .background(cardColor, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusIndicator(status = status)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(text = name, style = VpnTypography.TitleMedium)
                    Text(text = location, style = VpnTypography.BodyMedium)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (vlessOk) "✓ VLESS" else "✗ VLESS",
                    style = VpnTypography.Label.copy(
                        color = if (vlessOk) VpnColors.Success else VpnColors.Danger
                    )
                )
                if (pingMs != null && pingMs > 0) {
                    Text(text = "${pingMs.toInt()}ms", style = VpnTypography.Label)
                }
            }
        }
    }
}

@Composable
fun AnimatedCounter(value: Int, label: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(VpnColors.Surface, RoundedCornerShape(16.dp))
            .border(1.dp, VpnColors.CardBorder, RoundedCornerShape(16.dp))
            .padding(vertical = 20.dp, horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.height(12.dp))
        AnimatedContent(
            targetState = value,
            transitionSpec = {
                slideInVertically { it } + fadeIn() togetherWith
                slideOutVertically { -it } + fadeOut()
            },
            label = "counter"
        ) { targetCount ->
            Text(
                text = targetCount.toString(),
                style = VpnTypography.DisplayMedium.copy(color = color),
                textAlign = TextAlign.Center
            )
        }
        Text(text = label, style = VpnTypography.Label, textAlign = TextAlign.Center)
    }
}

@Composable
fun GlowButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.04f else 1f, label = "btnScale")

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp)),
        colors = ButtonDefaults.colors(
            containerColor = if (isFocused) VpnColors.Primary else VpnColors.SurfaceElevated,
            focusedContainerColor = VpnColors.Primary,
            contentColor = Color.White,
            focusedContentColor = Color.White
        )
    ) {
        Text(
            text = text,
            style = VpnTypography.BodyLarge.copy(color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        )
    }
}

/**
 * TV-слайдер: фокусируемая строка с ползунком.
 * Управление пультом: D-pad ◀ ▶ изменяет значение (шаг = 1/20 диапазона),
 * пульт/мышь — перетаскивание ползунка. onValueChangeFinished вызывается
 * при завершении изменения (сохранение в prefs).
 */
@Composable
fun TvSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    step: Float = 0f,
    valueSuffix: String = "%"
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.02f else 1f, label = "sliderScale")
    val keyStep = if (step > 0f) step else (valueRange.endInclusive - valueRange.start) / 20f
    val displayValue = if (step > 0f) {
        val rounded = (value / step).roundToInt() * step
        if (step >= 1f) "${rounded.toInt()}" else "%.1f".format(rounded)
    } else {
        "${((value - valueRange.start) / (valueRange.endInclusive - valueRange.start) * 100).toInt()}"
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionRight -> {
                            onValueChange((value + keyStep).coerceIn(valueRange))
                            onValueChangeFinished()
                            true
                        }
                        Key.DirectionLeft -> {
                            onValueChange((value - keyStep).coerceIn(valueRange))
                            onValueChangeFinished()
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) VpnColors.Primary else VpnColors.CardBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                color = if (isFocused) VpnColors.SurfaceElevated else VpnColors.Background,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = label, style = VpnTypography.BodyLarge)
            Text(
                text = "$displayValue$valueSuffix",
                style = VpnTypography.Label.copy(
                    color = if (isFocused) VpnColors.PrimaryLight else VpnColors.TextMuted,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            steps = if (step > 0f) ((valueRange.endInclusive - valueRange.start) / step).toInt() - 1 else 0,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = VpnColors.Primary,
                activeTrackColor = VpnColors.Primary,
                inactiveTrackColor = VpnColors.CardBorder
            )
        )
    }
}

/**
 * Фокусируемая строка-переключатель (текст + Switch) для TV-навигации пультом.
 */
@Composable
fun ToggleRow(
    text: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val rowScale by animateFloatAsState(targetValue = if (isFocused) 1.02f else 1f, label = "toggleRowScale")
    val switchScale by animateFloatAsState(targetValue = if (isFocused) 1.15f else 1f, label = "toggleSwitchScale")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .scale(rowScale)
            .onFocusChanged { isFocused = it.hasFocus }
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = if (isFocused) VpnColors.Primary else VpnColors.CardBorder,
                shape = RoundedCornerShape(12.dp)
            )
            .background(
                color = if (isFocused) VpnColors.SurfaceElevated else VpnColors.Background,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(text = text, style = VpnTypography.BodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.scale(switchScale)
        )
    }
}

/**
 * Кнопка выбора позиции оверлея (4 угла). Выбранная позиция подсвечивается Primary.
 * Применяйте `Modifier.weight(1f)` из Row-контекста вызывающей стороны.
 */
@Composable
fun PositionButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(targetValue = if (isFocused) 1.05f else 1f, label = "posBtnScale")

    Button(
        onClick = onClick,
        modifier = modifier
            .scale(scale)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(10.dp)),
        colors = ButtonDefaults.colors(
            containerColor = if (selected || isFocused) VpnColors.Primary else VpnColors.SurfaceElevated,
            focusedContainerColor = VpnColors.Primary,
            contentColor = Color.White,
            focusedContentColor = Color.White
        )
    ) {
        Text(
            text = text,
            style = VpnTypography.BodyMedium.copy(
                color = Color.White,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
        )
    }
}
