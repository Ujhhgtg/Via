package dev.ujhhgtg.via.settings.compose

import android.os.Build
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Material 3 Expressive theme for the KernelSU-style settings surface.
 *
 * Via's classic settings inherit the activity theme and the skin system; the Compose screen
 * instead owns its palette, mirroring KernelSU's Material variant (dynamic colour where the
 * platform exposes it, the baseline M3 schemes elsewhere).
 */
@Composable
fun ViaKsuTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = robotoBoldTypography(),
        content = content,
    )
}

/** Every settings text style uses Roboto (system sans-serif) at Bold weight. */
private fun robotoBoldTypography(): Typography {
    fun bold(style: TextStyle) = style.copy(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold)
    val base = Typography()
    return base.copy(
        displayLarge = bold(base.displayLarge),
        displayMedium = bold(base.displayMedium),
        displaySmall = bold(base.displaySmall),
        headlineLarge = bold(base.headlineLarge),
        headlineMedium = bold(base.headlineMedium),
        headlineSmall = bold(base.headlineSmall),
        titleLarge = bold(base.titleLarge),
        titleMedium = bold(base.titleMedium),
        titleSmall = bold(base.titleSmall),
        bodyLarge = bold(base.bodyLarge),
        bodyMedium = bold(base.bodyMedium),
        bodySmall = bold(base.bodySmall),
        labelLarge = bold(base.labelLarge),
        labelMedium = bold(base.labelMedium),
        labelSmall = bold(base.labelSmall),
    )
}
