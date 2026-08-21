package com.vpnmonitor.tv.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object VpnColors {
    // Фон и поверхности — глубокий тёмно-синий (флэт)
    val Background = Color(0xFF0B0F16)
    val Surface = Color(0xFF141A24)
    val SurfaceElevated = Color(0xFF1B2330)
    val CardBorder = Color(0xFF243042)

    // Единственный акцент — электрик-голубой
    val Primary = Color(0xFF2E9BFF)
    val PrimaryLight = Color(0xFF66C0FF)
    val PrimaryDark = Color(0xFF1A6FD4)

    // Статусы
    val Success = Color(0xFF22C55E)
    val SuccessGlow = Color(0xFF4ADE80)
    val Warning = Color(0xFFF59E0B)
    val WarningGlow = Color(0xFFFBBF24)
    val Danger = Color(0xFFEF4444)
    val DangerGlow = Color(0xFFF87171)

    // Текст
    val TextPrimary = Color(0xFFF1F5F9)
    val TextSecondary = Color(0xFF9AA7B8)
    val TextMuted = Color(0xFF5E6B7D)

    // Градиенты — оттенки акцентного синего (плоские, без разноцветия)
    val GradientStart = Color(0xFF2E9BFF)
    val GradientMid = Color(0xFF4FB3FF)
    val GradientEnd = Color(0xFF00C2FF)
}

object VpnTypography {
    val DisplayLarge = TextStyle(
        fontSize = 44.sp, fontWeight = FontWeight.Bold, color = VpnColors.TextPrimary
    )
    val DisplayMedium = TextStyle(
        fontSize = 30.sp, fontWeight = FontWeight.Bold, color = VpnColors.TextPrimary
    )
    val TitleLarge = TextStyle(
        fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = VpnColors.TextPrimary
    )
    val TitleMedium = TextStyle(
        fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = VpnColors.TextPrimary
    )
    val BodyLarge = TextStyle(
        fontSize = 16.sp, fontWeight = FontWeight.Normal, color = VpnColors.TextSecondary
    )
    val BodyMedium = TextStyle(
        fontSize = 14.sp, fontWeight = FontWeight.Normal, color = VpnColors.TextSecondary
    )
    val Label = TextStyle(
        fontSize = 12.sp, fontWeight = FontWeight.Medium,
        color = VpnColors.TextMuted, letterSpacing = 0.8.sp
    )
}
