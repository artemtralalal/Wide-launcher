package com.kyy.launcher

import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.material.color.utilities.*

/** Скругление с учётом настройки «Скругление углов». */
fun rc(base: Int) = RoundedCornerShape((base * prefs.corner / 100f).dp)

fun buildScheme(seed: Int, dark: Boolean, style: String, contrast: Double): ColorScheme {
    val h = Hct.fromInt(seed)
    val sc: DynamicScheme = when (style) {
        "fidelity" -> SchemeFidelity(h, dark, contrast)
        "vibrant" -> SchemeVibrant(h, dark, contrast)
        "expressive" -> SchemeExpressive(h, dark, contrast)
        "rainbow" -> SchemeRainbow(h, dark, contrast)
        "fruit" -> SchemeFruitSalad(h, dark, contrast)
        "neutral" -> SchemeNeutral(h, dark, contrast)
        "mono" -> SchemeMonochrome(h, dark, contrast)
        else -> SchemeTonalSpot(h, dark, contrast)
    }
    val p = sc.primaryPalette
    val sp = sc.secondaryPalette
    val tp = sc.tertiaryPalette
    val n = sc.neutralPalette
    val nv = sc.neutralVariantPalette
    val ep = sc.errorPalette
    val k = contrast.coerceIn(0.0, 1.0)
    fun t(base: Int, to: Int) = (base + (to - base) * k).toInt()
    fun c(v: Int) = Color(v)
    return if (dark) darkColorScheme(
        primary = c(p.tone(t(80, 92))), onPrimary = c(p.tone(t(20, 8))),
        primaryContainer = c(p.tone(t(30, 22))), onPrimaryContainer = c(p.tone(t(90, 100))),
        inversePrimary = c(p.tone(40)),
        secondary = c(sp.tone(t(80, 92))), onSecondary = c(sp.tone(t(20, 8))),
        secondaryContainer = c(sp.tone(t(30, 22))), onSecondaryContainer = c(sp.tone(t(90, 100))),
        tertiary = c(tp.tone(t(80, 92))), onTertiary = c(tp.tone(t(20, 8))),
        tertiaryContainer = c(tp.tone(t(30, 22))), onTertiaryContainer = c(tp.tone(t(90, 100))),
        background = c(n.tone(6)), onBackground = c(n.tone(90)),
        surface = c(n.tone(6)), onSurface = c(n.tone(t(90, 100))),
        surfaceVariant = c(nv.tone(30)), onSurfaceVariant = c(nv.tone(t(80, 95))),
        surfaceDim = c(n.tone(6)), surfaceBright = c(n.tone(24)),
        surfaceContainerLowest = c(n.tone(4)), surfaceContainerLow = c(n.tone(10)),
        surfaceContainer = c(n.tone(12)), surfaceContainerHigh = c(n.tone(17)),
        surfaceContainerHighest = c(n.tone(22)),
        inverseSurface = c(n.tone(90)), inverseOnSurface = c(n.tone(20)),
        outline = c(nv.tone(t(60, 80))), outlineVariant = c(nv.tone(30)),
        error = c(ep.tone(80)), onError = c(ep.tone(20)),
        errorContainer = c(ep.tone(30)), onErrorContainer = c(ep.tone(90))
    ) else lightColorScheme(
        primary = c(p.tone(t(40, 25))), onPrimary = c(p.tone(100)),
        primaryContainer = c(p.tone(t(90, 80))), onPrimaryContainer = c(p.tone(t(10, 0))),
        inversePrimary = c(p.tone(80)),
        secondary = c(sp.tone(t(40, 25))), onSecondary = c(sp.tone(100)),
        secondaryContainer = c(sp.tone(t(90, 80))), onSecondaryContainer = c(sp.tone(t(10, 0))),
        tertiary = c(tp.tone(t(40, 25))), onTertiary = c(tp.tone(100)),
        tertiaryContainer = c(tp.tone(t(90, 80))), onTertiaryContainer = c(tp.tone(t(10, 0))),
        background = c(n.tone(98)), onBackground = c(n.tone(10)),
        surface = c(n.tone(98)), onSurface = c(n.tone(t(10, 0))),
        surfaceVariant = c(nv.tone(90)), onSurfaceVariant = c(nv.tone(t(30, 10))),
        surfaceDim = c(n.tone(87)), surfaceBright = c(n.tone(98)),
        surfaceContainerLowest = c(n.tone(100)), surfaceContainerLow = c(n.tone(96)),
        surfaceContainer = c(n.tone(94)), surfaceContainerHigh = c(n.tone(92)),
        surfaceContainerHighest = c(n.tone(90)),
        inverseSurface = c(n.tone(20)), inverseOnSurface = c(n.tone(95)),
        outline = c(nv.tone(t(50, 30))), outlineVariant = c(nv.tone(80)),
        error = c(ep.tone(40)), onError = c(ep.tone(100)),
        errorContainer = c(ep.tone(90)), onErrorContainer = c(ep.tone(10))
    )
}

private fun buildTypography(): Typography {
    val f = prefs.font
    val fam: FontFamily = if (f.startsWith(CustomFonts.PREFIX)) {
        CustomFonts.family(f.removePrefix(CustomFonts.PREFIX)) ?: FontFamily.SansSerif
    } else when (f) {
        "rounded" -> try { FontFamily(Typeface.create("sans-serif-rounded", Typeface.NORMAL)) } catch (e: Exception) { FontFamily.SansSerif }
        "serif" -> FontFamily.Serif
        "mono" -> FontFamily.Monospace
        else -> SysFonts.family(f) ?: FontFamily.SansSerif
    }
    val boost = prefs.weightBoost
    fun TextStyle.f(): TextStyle {
        val w = ((fontWeight?.weight ?: 400) + boost).coerceIn(100, 900)
        return copy(fontFamily = fam, fontWeight = FontWeight(w))
    }
    val b = Typography()
    return Typography(
        displayLarge = b.displayLarge.f(), displayMedium = b.displayMedium.f(), displaySmall = b.displaySmall.f(),
        headlineLarge = b.headlineLarge.f(), headlineMedium = b.headlineMedium.f(), headlineSmall = b.headlineSmall.f(),
        titleLarge = b.titleLarge.f(), titleMedium = b.titleMedium.f(), titleSmall = b.titleSmall.f(),
        bodyLarge = b.bodyLarge.f(), bodyMedium = b.bodyMedium.f(), bodySmall = b.bodySmall.f(),
        labelLarge = b.labelLarge.f(), labelMedium = b.labelMedium.f(), labelSmall = b.labelSmall.f()
    )
}

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    val dark = when (prefs.themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val ctx = LocalContext.current
    val system = prefs.accent == "system"
    val scheme = if (system && prefs.palette == "tonal" && prefs.contrast == 0 && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        val seed = if (system && Build.VERSION.SDK_INT >= 31) ctx.getColor(android.R.color.system_accent1_500)
        else (Palette[prefs.accent] ?: Palette.getValue("teal")).toInt()
        buildScheme(seed, dark, prefs.palette, prefs.contrast / 100.0)
    }
    val shapes = Shapes(
        extraSmall = rc(4), small = rc(8), medium = rc(16), large = rc(24), extraLarge = rc(32)
    )
    MaterialTheme(colorScheme = scheme, typography = buildTypography(), shapes = shapes, content = content)
}

/**
 * Окно на весь экран с полностью прозрачными строкой состояния и кнопками навигации.
 * Без этого enableEdgeToEdge() сам рисует под кнопками светлую плашку (при трёх кнопках), а система ещё
 * принудительно повышает контраст панели, поэтому на Redmi/Xiaomi кнопки оказывались на белом фоне.
 */
fun ComponentActivity.edgeToEdgeTransparent() {
    var dark: (android.context.res.Resources) -> Boolean = { r ->
        when (prefs.themeMode) {
            "dark" -> true
            "light" -> false
            else -> (r.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }
    val clear = android.graphics.Color.TRANSPARENT
    enableEdgeToEdge(
        statusBarStyle = SystemBarStyle.auto(clear, clear, dark),
        navigationBarStyle = SystemBarStyle.auto(clear, clear, dark)
    )
    if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
}
