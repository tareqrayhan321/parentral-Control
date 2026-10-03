package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Bengali needs generous line height (matras/conjuncts above and below the line) and NO extra
// letter spacing, which pulls conjuncts apart. The system font already contains Noto Sans Bengali.
private fun style(size: Int, weight: FontWeight, line: Int) = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = 0.sp
)

val Typography = Typography(
    headlineMedium = style(26, FontWeight.Bold, 38),
    headlineSmall = style(22, FontWeight.Bold, 33),
    titleLarge = style(20, FontWeight.Bold, 30),
    titleMedium = style(16, FontWeight.SemiBold, 25),
    titleSmall = style(14, FontWeight.SemiBold, 22),
    bodyLarge = style(16, FontWeight.Normal, 26),
    bodyMedium = style(14, FontWeight.Normal, 23),
    bodySmall = style(12, FontWeight.Normal, 19),
    labelLarge = style(14, FontWeight.SemiBold, 20),
    labelMedium = style(12, FontWeight.Medium, 18),
    labelSmall = style(11, FontWeight.Medium, 17)
)
