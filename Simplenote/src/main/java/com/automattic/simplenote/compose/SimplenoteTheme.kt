package com.automattic.simplenote.compose

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.core.content.res.ResourcesCompat
import com.automattic.simplenote.R

/**
 * Bridges the hosting activity's XML theme into Material 3 Compose. Every color below is resolved
 * from [LocalContext]'s theme at composition time, so the premium styles (Style.Black, Style.Sepia,
 * Style.Matrix, ...) - which ThemedAppCompatActivity applies as full activity themes - flow into
 * Compose without any per-style code here.
 *
 * Slot sources:
 * - App-pinned roles (set by Base.Theme.Simplestyle or a Style.* theme): primary, onPrimary,
 *   secondary, secondaryContainer, onSecondaryContainer, surface, onSurface, onSurfaceVariant,
 *   surfaceContainer(+Low/High/Highest), background, outline, outlineVariant.
 * - XML-parent roles (resolve to Theme.Material3's baseline palette because no Simplenote theme
 *   overrides them yet): primaryContainer, onPrimaryContainer, inversePrimary, onSecondary,
 *   tertiary family, onBackground, surfaceVariant, inverseSurface, inverseOnSurface, error family,
 *   surfaceBright, surfaceDim, surfaceContainerLowest.
 * - No XML attr exists: surfaceTint (aliased to the resolved primary, matching Compose's default)
 *   and scrim (Compose default black). Any slot not named in [resolveColorScheme] (e.g. the fixed
 *   accent roles) keeps the lightColorScheme()/darkColorScheme() baseline default.
 *
 * Typography is the Material 3 baseline with every style's fontFamily swapped to the theme's
 * `styleFontFamily` (Matrix/Mono -> monospace, Publication -> serif, otherwise sans-serif), and the
 * 8 surviving custom attrs are exposed through [LocalSimplenoteColors].
 */
@Composable
fun SimplenoteTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    val colorScheme = remember(context, darkTheme) { context.resolveColorScheme(darkTheme) }
    val extraColors = remember(context, darkTheme) { context.resolveExtraColors() }
    val typography = remember(context, darkTheme) { typographyWith(context.resolveFontFamily()) }
    CompositionLocalProvider(LocalSimplenoteColors provides extraColors) {
        MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
    }
}

/** The surviving custom theme attrs that have no Material 3 role, resolved per style x mode. */
@Immutable
data class SimplenoteExtraColors(
    val listSearchHighlightForeground: Color,
    val listSearchHighlightBackground: Color,
    val editorSearchHighlightForeground: Color,
    val editorSearchHighlightBackground: Color,
    val emptyImage: Color,
    val toolbarIcon: Color,
    /** `listBackgroundSelector` is a per-style drawable, so it crosses the bridge as a res id. */
    val listBackgroundSelectorRes: Int,
)

val LocalSimplenoteColors = staticCompositionLocalOf<SimplenoteExtraColors> {
    error("No SimplenoteExtraColors provided; wrap this composition in SimplenoteTheme")
}

private fun Context.resolveColorScheme(darkTheme: Boolean): ColorScheme {
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    fun role(@AttrRes attr: Int, fallback: Color) = attrColor(attr, fallback)
    val primary = role(R.attr.colorPrimary, base.primary)
    return base.copy(
        primary = primary,
        onPrimary = role(R.attr.colorOnPrimary, base.onPrimary),
        primaryContainer = role(R.attr.colorPrimaryContainer, base.primaryContainer),
        onPrimaryContainer = role(R.attr.colorOnPrimaryContainer, base.onPrimaryContainer),
        inversePrimary = role(R.attr.colorPrimaryInverse, base.inversePrimary),
        secondary = role(R.attr.colorSecondary, base.secondary),
        onSecondary = role(R.attr.colorOnSecondary, base.onSecondary),
        secondaryContainer = role(R.attr.colorSecondaryContainer, base.secondaryContainer),
        onSecondaryContainer = role(R.attr.colorOnSecondaryContainer, base.onSecondaryContainer),
        tertiary = role(R.attr.colorTertiary, base.tertiary),
        onTertiary = role(R.attr.colorOnTertiary, base.onTertiary),
        tertiaryContainer = role(R.attr.colorTertiaryContainer, base.tertiaryContainer),
        onTertiaryContainer = role(R.attr.colorOnTertiaryContainer, base.onTertiaryContainer),
        background = role(android.R.attr.colorBackground, base.background),
        onBackground = role(R.attr.colorOnBackground, base.onBackground),
        surface = role(R.attr.colorSurface, base.surface),
        onSurface = role(R.attr.colorOnSurface, base.onSurface),
        surfaceVariant = role(R.attr.colorSurfaceVariant, base.surfaceVariant),
        onSurfaceVariant = role(R.attr.colorOnSurfaceVariant, base.onSurfaceVariant),
        surfaceTint = primary,
        inverseSurface = role(R.attr.colorSurfaceInverse, base.inverseSurface),
        inverseOnSurface = role(R.attr.colorOnSurfaceInverse, base.inverseOnSurface),
        error = role(R.attr.colorError, base.error),
        onError = role(R.attr.colorOnError, base.onError),
        errorContainer = role(R.attr.colorErrorContainer, base.errorContainer),
        onErrorContainer = role(R.attr.colorOnErrorContainer, base.onErrorContainer),
        outline = role(R.attr.colorOutline, base.outline),
        outlineVariant = role(R.attr.colorOutlineVariant, base.outlineVariant),
        surfaceBright = role(R.attr.colorSurfaceBright, base.surfaceBright),
        surfaceDim = role(R.attr.colorSurfaceDim, base.surfaceDim),
        surfaceContainer = role(R.attr.colorSurfaceContainer, base.surfaceContainer),
        surfaceContainerHigh = role(R.attr.colorSurfaceContainerHigh, base.surfaceContainerHigh),
        surfaceContainerHighest = role(R.attr.colorSurfaceContainerHighest, base.surfaceContainerHighest),
        surfaceContainerLow = role(R.attr.colorSurfaceContainerLow, base.surfaceContainerLow),
        surfaceContainerLowest = role(R.attr.colorSurfaceContainerLowest, base.surfaceContainerLowest),
    )
}

private fun Context.resolveExtraColors() = SimplenoteExtraColors(
    listSearchHighlightForeground = attrColor(R.attr.listSearchHighlightForegroundColor, Color.Unspecified),
    listSearchHighlightBackground = attrColor(R.attr.listSearchHighlightBackgroundColor, Color.Unspecified),
    editorSearchHighlightForeground = attrColor(R.attr.editorSearchHighlightForegroundColor, Color.Unspecified),
    editorSearchHighlightBackground = attrColor(R.attr.editorSearchHighlightBackgroundColor, Color.Unspecified),
    emptyImage = attrColor(R.attr.emptyImageColor, Color.Unspecified),
    toolbarIcon = attrColor(R.attr.toolbarIconColor, Color.Unspecified),
    listBackgroundSelectorRes = attrResourceId(R.attr.listBackgroundSelector),
)

/** `styleFontFamily` is a string in every current theme, but the attr format allows a @font ref. */
private fun Context.resolveFontFamily(): FontFamily {
    val value = TypedValue()
    if (!theme.resolveAttribute(R.attr.styleFontFamily, value, true)) {
        return FontFamily.Default
    }
    if (value.resourceId != 0) {
        ResourcesCompat.getFont(this, value.resourceId)?.let { return FontFamily(it) }
    }
    return when (val name = value.string?.toString()) {
        null, "", "sans-serif" -> FontFamily.SansSerif
        "monospace" -> FontFamily.Monospace
        "serif" -> FontFamily.Serif
        else -> FontFamily(Typeface.create(name, Typeface.NORMAL))
    }
}

private fun typographyWith(fontFamily: FontFamily): Typography {
    val base = Typography()
    return base.copy(
        displayLarge = base.displayLarge.copy(fontFamily = fontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = fontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = fontFamily),
        headlineLarge = base.headlineLarge.copy(fontFamily = fontFamily),
        headlineMedium = base.headlineMedium.copy(fontFamily = fontFamily),
        headlineSmall = base.headlineSmall.copy(fontFamily = fontFamily),
        titleLarge = base.titleLarge.copy(fontFamily = fontFamily),
        titleMedium = base.titleMedium.copy(fontFamily = fontFamily),
        titleSmall = base.titleSmall.copy(fontFamily = fontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = fontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = fontFamily),
        bodySmall = base.bodySmall.copy(fontFamily = fontFamily),
        labelLarge = base.labelLarge.copy(fontFamily = fontFamily),
        labelMedium = base.labelMedium.copy(fontFamily = fontFamily),
        labelSmall = base.labelSmall.copy(fontFamily = fontFamily),
    )
}

private fun Context.attrColor(@AttrRes attr: Int, fallback: Color): Color {
    val values = obtainStyledAttributes(intArrayOf(attr))
    try {
        return Color(values.getColor(0, fallback.toArgb()))
    } finally {
        values.recycle()
    }
}

private fun Context.attrResourceId(@AttrRes attr: Int): Int {
    val values = obtainStyledAttributes(intArrayOf(attr))
    try {
        return values.getResourceId(0, 0)
    } finally {
        values.recycle()
    }
}
