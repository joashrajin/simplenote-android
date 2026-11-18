package com.automattic.simplenote.compose

import android.view.ContextThemeWrapper
import androidx.annotation.ColorRes
import androidx.annotation.StyleRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.unit.sp
import com.automattic.simplenote.R
import com.automattic.simplenote.utils.ThemeUtils
import com.automattic.simplenote.widgets.TypefaceCache

// Same title inset as TagsScreen and CollaboratorsScreen, so the three Compose top bars line up.
// It lands the title ~4dp right of the View toolbar's 72dp toolbar_title_keyline.
private val TitleKeylinePadding = 20.dp

// The RecyclerView's paddingTop/paddingBottom (padding_small); the bottom one is a floor under
// the navigation bar inset, as SystemBarUtils.applyInsets(keepContentBottomPadding = true) kept it.
private val ListVerticalPadding = 8.dp

// TextView's default textAppearanceSmall, which the preview body inherited.
private val PreviewContentSize = 14.sp

// TextView keeps font padding; matching it keeps the rows the height the View cards were.
private val TextViewMetrics = PlatformTextStyle(includeFontPadding = true)

/**
 * One row of the picker. [theme] is the full Style_X theme the row previews; [titleColor] and
 * [contentColor] are the day/night color resources its deleted row layout set on its TextViews
 * (only the Black row had its own pair).
 */
internal data class StyleOption(
    val index: Int,
    val name: String,
    @StyleRes val theme: Int,
    @ColorRes val titleColor: Int = R.color.style_preview_default_title,
    @ColorRes val contentColor: Int = R.color.style_preview_default_content,
)

internal object StyleScreenTags {
    const val LIST = "style_list"
    fun row(index: Int) = "style_row_$index"
    fun title(index: Int) = "style_title_$index"
    fun content(index: Int) = "style_content_$index"
}

/**
 * Compose port of the style picker. The screen chrome (top bar, window) renders under the
 * activity's current style through [SimplenoteTheme]; each row composes under its own
 * Style_X theme by providing a [ContextThemeWrapper] as [LocalContext] to a nested
 * [SimplenoteTheme], so its surface, accent and `styleFontFamily` come from that style's XML.
 *
 * Insets: the activity goes edge to edge through ThemedAppCompatActivity; the Scaffold consumes
 * [WindowInsets.safeDrawing] in place of the deleted SystemBarUtils.applyInsets wiring.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StyleScreen(
    styles: List<StyleOption>,
    selectedIndex: Int,
    listState: LazyListState,
    onStyleSelected: (Int) -> Unit,
    onNavigateUp: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        // The RecyclerView was transparent over the style's windowBackground.
        containerColor = Color.Transparent,
        topBar = { StyleTopBar(onNavigateUp) },
    ) { innerPadding ->
        val layoutDirection = LocalLayoutDirection.current
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .selectableGroup()
                .testTag(StyleScreenTags.LIST),
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection),
                top = innerPadding.calculateTopPadding() + ListVerticalPadding,
                end = innerPadding.calculateEndPadding(layoutDirection),
                bottom = max(innerPadding.calculateBottomPadding(), ListVerticalPadding),
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(items = styles, key = { it.index }) { style ->
                StyleRow(
                    style = style,
                    selected = style.index == selectedIndex,
                    onClick = { onStyleSelected(style.index) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleTopBar(onNavigateUp: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                text = stringResource(R.string.style),
                modifier = Modifier.padding(start = TitleKeylinePadding),
            )
        },
        navigationIcon = {
            IconButton(onClick = onNavigateUp) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_left_24dp),
                    contentDescription = stringResource(androidx.appcompat.R.string.abc_action_bar_up_description),
                    tint = LocalSimplenoteColors.current.toolbarIcon,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            // The deleted toolbar include drew on colorSurfaceContainerLow.
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

@Composable
private fun StyleRow(style: StyleOption, selected: Boolean, onClick: () -> Unit) {
    val activityContext = LocalContext.current
    val rowContext = remember(activityContext, style.theme) { ContextThemeWrapper(activityContext, style.theme) }
    CompositionLocalProvider(LocalContext provides rowContext) {
        SimplenoteTheme {
            StyleCard(style = style, selected = selected, onClick = onClick)
        }
    }
}

/** Must be composed inside the row's own [SimplenoteTheme]. */
@Composable
private fun StyleCard(style: StyleOption, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    // Both the preview link and the activated stroke used the row theme's colorAccent.
    val accent = remember(context) {
        Color(ThemeUtils.getColorFromAttribute(context, androidx.appcompat.R.attr.colorAccent))
    }
    val fonts = rememberPreviewFonts(MaterialTheme.typography.bodyMedium.fontFamily)
    val stroke = if (selected) {
        dimensionResource(R.dimen.style_stroke_activated) to accent
    } else {
        dimensionResource(R.dimen.style_stroke) to colorResource(R.color.style_preview_stroke)
    }
    // The deleted shape drawables stroked a corner_radius path inset by half the stroke, so their
    // outer edge rounded at corner_radius + stroke / 2; Compose borders stroke inside the shape.
    val shape = RoundedCornerShape(dimensionResource(R.dimen.corner_radius) + stroke.first / 2)

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp) // padding_large / padding_small margins
            .contentWidth(dimensionResource(R.dimen.content_width))
            .heightIn(min = 48.dp) // minimum_target
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .border(stroke.first, stroke.second, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(StyleScreenTags.row(style.index))
            .padding(16.dp), // padding_large
        verticalArrangement = Arrangement.spacedBy(8.dp), // the title's padding_small bottom margin
    ) {
        Text(
            text = style.name,
            modifier = Modifier.testTag(StyleScreenTags.title(style.index)),
            color = colorResource(style.titleColor),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                fontSize = 16.sp, // text_content_title
                fontFamily = fonts.title,
                fontWeight = fonts.titleWeight,
                platformStyle = TextViewMetrics,
            ),
        )
        Text(
            text = previewText(accent),
            modifier = Modifier.testTag(StyleScreenTags.content(style.index)),
            color = colorResource(style.contentColor),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                fontSize = PreviewContentSize,
                fontFamily = fonts.content,
                platformStyle = TextViewMetrics,
            ),
        )
    }
}

/** content_width is -1px (match_parent) on phones and a fixed card width on large screens. */
private fun Modifier.contentWidth(contentWidth: Dp): Modifier =
    if (contentWidth > 0.dp) width(contentWidth) else fillMaxWidth()

/** The style_preview string with its image URL underlined in the row's accent, built as before. */
@Composable
private fun previewText(accent: Color): AnnotatedString {
    val hex = Integer.toHexString(accent.toArgb() and 0xffffff)
    val html = stringResource(R.string.style_preview, "<u><span style=\"color:#", hex, "\">", "</span></u>")
    return remember(html) { AnnotatedString.fromHtml(html) }
}

private val SansSerifDefaults = setOf(FontFamily.Default, FontFamily.SansSerif)

private class PreviewFonts(val title: FontFamily, val titleWeight: FontWeight, val content: FontFamily)

/**
 * The row's `styleFontFamily`, except where it is the sans-serif default: those rows used the
 * Roboto Medium/Regular assets (RobotoMediumTextView/RobotoRegularTextView), which also drop the
 * title's bold. Every other family keeps the bold title the plain TextViews had.
 *
 * The night Base.Theme.Simplestyle leaves `styleFontFamily` unset, which the bridge resolves to
 * [FontFamily.Default]; an unresolved `?attr/styleFontFamily` meant the default sans face in XML too.
 */
@Composable
private fun rememberPreviewFonts(styleFontFamily: FontFamily?): PreviewFonts {
    val assets = LocalContext.current.applicationContext.assets
    return remember(styleFontFamily, assets) {
        if (styleFontFamily == null || styleFontFamily in SansSerifDefaults) {
            PreviewFonts(
                title = FontFamily(
                    Font("fonts/${TypefaceCache.TYPEFACE_NAME_ROBOTO_MEDIUM}", assets, FontWeight.Medium)
                ),
                titleWeight = FontWeight.Medium,
                content = FontFamily(Font("fonts/${TypefaceCache.TYPEFACE_NAME_ROBOTO_REGULAR}", assets)),
            )
        } else {
            PreviewFonts(title = styleFontFamily, titleWeight = FontWeight.Bold, content = styleFontFamily)
        }
    }
}
