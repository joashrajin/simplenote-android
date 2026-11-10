package com.automattic.simplenote.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Debug-only pin for the [SimplenoteTheme] bridge: a labeled block per resolved ColorScheme role
 * and per surviving custom attr, plus a typography line that surfaces `styleFontFamily`. Rendered
 * by ThemeSwatchScreenshotTest under each harness style x mode, and previewable in the IDE (where
 * unresolved attrs fall back to the Compose baseline scheme).
 */
@Composable
fun ThemeSwatchScreen() = SimplenoteTheme {
    val scheme = MaterialTheme.colorScheme
    val extras = LocalSimplenoteColors.current
    val swatches = listOf(
        "primary" to scheme.primary,
        "onPrimary" to scheme.onPrimary,
        "primaryContainer" to scheme.primaryContainer,
        "onPrimaryContainer" to scheme.onPrimaryContainer,
        "secondary" to scheme.secondary,
        "secondaryContainer" to scheme.secondaryContainer,
        "onSecondaryContainer" to scheme.onSecondaryContainer,
        "tertiary" to scheme.tertiary,
        "background" to scheme.background,
        "surface" to scheme.surface,
        "onSurface" to scheme.onSurface,
        "surfaceVariant" to scheme.surfaceVariant,
        "onSurfaceVariant" to scheme.onSurfaceVariant,
        "surfaceContainerLow" to scheme.surfaceContainerLow,
        "surfaceContainer" to scheme.surfaceContainer,
        "surfaceContainerHigh" to scheme.surfaceContainerHigh,
        "surfaceContainerHighest" to scheme.surfaceContainerHighest,
        "inverseSurface" to scheme.inverseSurface,
        "error" to scheme.error,
        "outline" to scheme.outline,
        "outlineVariant" to scheme.outlineVariant,
        "surfaceTint" to scheme.surfaceTint,
        "listSearchHighlightFg" to extras.listSearchHighlightForeground,
        "listSearchHighlightBg" to extras.listSearchHighlightBackground,
        "editorSearchHighlightFg" to extras.editorSearchHighlightForeground,
        "editorSearchHighlightBg" to extras.editorSearchHighlightBackground,
        "emptyImage" to extras.emptyImage,
        "toolbarIcon" to extras.toolbarIcon,
    )
    Surface(modifier = Modifier.fillMaxSize(), color = scheme.surface) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Sphinx of black quartz, judge my vow",
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onSurface,
            )
            swatches.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (name, color) -> SwatchCell(name, color, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SwatchCell(name: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(16.dp)
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
        )
        Box(Modifier.width(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

@Preview(name = "Theme swatches", showBackground = true)
@Composable
private fun ThemeSwatchScreenPreview() = ThemeSwatchScreen()
