package com.tms.banking.ui.theme

import androidx.compose.ui.graphics.Color

val Background = Color(0xFF1a1a2e)
val Surface = Color(0xFF16213e)
val SurfaceVariant = Color(0xFF1f2d4a)
val Primary = Color(0xFF7c4dff)
val PrimaryVariant = Color(0xFF9c6dff)
val OnPrimary = Color(0xFFFFFFFF)
val OnBackground = Color(0xFFE0E0E0)
val OnSurface = Color(0xFFBDBDBD)
val Outline = Color(0xFF3a3a5c)

val Positive = Color(0xFF4caf50)
val Negative = Color(0xFFff6b6b)
val NeutralAmount = Color(0xFFE0E0E0)

val BankUAE = Color(0xFF7eb8ff)
val BankRevolut = Color(0xFFb07eff)
val BankSparkasse = Color(0xFFff7e7e)
val BankStaytris = Color(0xFF7effb0)

val CategoryColors = listOf(
    Color(0xFF7c4dff),
    Color(0xFF7eb8ff),
    Color(0xFF4caf50),
    Color(0xFFff6b6b),
    Color(0xFFffb74d),
    Color(0xFF26c6da),
    Color(0xFFec407a),
    Color(0xFF8d6e63),
    Color(0xFF78909c),
    Color(0xFFb07eff)
)

fun bankColor(bank: String): Color {
    return when {
        bank.contains("Revolut", ignoreCase = true) -> BankRevolut
        bank.contains("Sparkasse", ignoreCase = true) -> BankSparkasse
        bank.contains("Staytris", ignoreCase = true) -> BankStaytris
        else -> BankUAE
    }
}
