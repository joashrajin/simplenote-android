package com.automattic.simplenote.compose

import android.view.animation.AnimationUtils
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberPlatformOverscrollFactory
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Text
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automattic.simplenote.R
import com.automattic.simplenote.widgets.TypefaceCache
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AboutLink { BLOG, TWITTER, HELP, STORE, CONTRIBUTE, CAREERS, PRIVACY, TERMS, CALIFORNIA }

private const val LOGO_LONG_PRESS_MS = 1000L
private const val LOGO_ACCELERATE_MS = 750
private const val LOGO_FULL_SPEED_MS = 250
private const val LOGO_DECELERATE_MS = 600

/**
 * The About screen, blue in both modes. Its palette is the fixed set of colors the View screen
 * used, not [SimplenoteTheme], whose colors follow the app style. Text uses the bundled Roboto
 * assets and keeps font padding, so line metrics match the RobotoRegular/MediumTextView screen.
 */
@Composable
fun AboutScreen(
    versionInfo: String,
    year: Int,
    onClose: () -> Unit,
    onLinkClick: (AboutLink) -> Unit,
    onLogoMaximumSpeed: () -> Unit,
) {
    val colors = aboutColors()
    val fonts = aboutFonts()
    // Theme.Simplestyle.About's colorControlHighlight; a platform ripple draws it at half alpha.
    val ripple = remember(colors.highlight) {
        RippleConfiguration(color = colors.highlight, rippleAlpha = RippleAlpha(0.5f, 0.5f, 0.5f, 0.5f))
    }
    CompositionLocalProvider(
        LocalContentColor provides Color.White,
        LocalRippleConfiguration provides ripple,
        // Theme.Simplestyle.About's colorEdgeEffect, for the pre-stretch (API < 31) glow.
        LocalOverscrollFactory provides rememberPlatformOverscrollFactory(glowColor = Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal)),
        ) {
            Box(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                    .fillMaxWidth()
                    .height(56.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                IconButton(onClick = onClose, modifier = Modifier.padding(start = 4.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_cross_24dp),
                        contentDescription = stringResource(R.string.description_close),
                        tint = Color.White,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(
                        WindowInsets.systemBars.union(WindowInsets.ime).only(WindowInsetsSides.Bottom)
                    )
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AboutContent(
                    versionInfo = versionInfo,
                    year = year,
                    colors = colors,
                    fonts = fonts,
                    onLinkClick = onLinkClick,
                    onLogoMaximumSpeed = onLogoMaximumSpeed,
                    modifier = Modifier.contentWidth(),
                )
            }
        }
    }
}

@Composable
private fun AboutContent(
    versionInfo: String,
    year: Int,
    colors: AboutColors,
    fonts: AboutFonts,
    onLinkClick: (AboutLink) -> Unit,
    onLogoMaximumSpeed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        SpinningLogo(
            onMaximumSpeed = onLogoMaximumSpeed,
            modifier = Modifier
                .padding(vertical = 16.dp)
                .size(56.dp),
        )
        Text(
            text = stringResource(R.string.app_name),
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
            style = aboutTextStyle(fonts.medium, 24.sp, Color.White),
            textAlign = TextAlign.Center,
        )
        Text(
            text = versionInfo,
            modifier = Modifier.fillMaxWidth(),
            style = aboutTextStyle(fonts.regular, 16.sp, colors.subtitle),
            textAlign = TextAlign.Center,
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            thickness = 1.dp,
            color = colors.divider,
        )
        AboutRow(R.string.blog, R.string.simplenote_blog_url, colors, fonts) { onLinkClick(AboutLink.BLOG) }
        AboutRow(R.string.twitter, R.string.about_twitter_handle, colors, fonts) { onLinkClick(AboutLink.TWITTER) }
        AboutRow(R.string.about_help_title, R.string.about_help_summary, colors, fonts) {
            onLinkClick(AboutLink.HELP)
        }
        AboutRow(R.string.rate_us, R.string.about_play_store, colors, fonts) { onLinkClick(AboutLink.STORE) }
        AboutRow(R.string.about_contribute_title, R.string.about_contribute_subtitle, colors, fonts) {
            onLinkClick(AboutLink.CONTRIBUTE)
        }
        AboutRow(R.string.about_hiring_title, R.string.about_hiring, colors, fonts) {
            onLinkClick(AboutLink.CAREERS)
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
            thickness = 1.dp,
            color = colors.divider,
        )
        // Each link takes half the row, so at large font scales they wrap instead of clipping.
        Row(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                LegalLink(R.string.link_privacy, TextAlign.End, colors, fonts) { onLinkClick(AboutLink.PRIVACY) }
            }
            Text(
                text = stringResource(R.string.link_divider),
                modifier = Modifier.alignByBaseline(),
                style = aboutTextStyle(fonts.regular, 14.sp, colors.subtitle),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .alignByBaseline(),
                contentAlignment = Alignment.CenterStart,
            ) {
                LegalLink(R.string.link_terms, TextAlign.Start, colors, fonts) { onLinkClick(AboutLink.TERMS) }
            }
        }
        LegalLink(R.string.link_california, TextAlign.Center, colors, fonts) { onLinkClick(AboutLink.CALIFORNIA) }
        Text(
            text = stringResource(R.string.about_copyright, year),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            style = aboutTextStyle(fonts.regular, 14.sp, colors.subtitle),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AboutRow(
    @StringRes title: Int,
    @StringRes subtitle: Int,
    colors: AboutColors,
    fonts: AboutFonts,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(title),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = aboutTextStyle(fonts.regular, 16.sp, Color.White),
            )
            Text(
                text = stringResource(subtitle),
                modifier = Modifier.padding(horizontal = 16.dp),
                style = aboutTextStyle(fonts.regular, 14.sp, colors.subtitle),
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_arrow_top_right_24dp),
            contentDescription = null,
            modifier = Modifier.padding(end = 16.dp),
            tint = colors.arrow,
        )
    }
}

@Composable
private fun LegalLink(
    @StringRes text: Int,
    textAlign: TextAlign,
    colors: AboutColors,
    fonts: AboutFonts,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .sizeIn(minWidth = MinimumTouchTarget, minHeight = MinimumTouchTarget)
            .padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            // The link strings carry four markup placeholders the View screen filled with HTML.
            text = stringResource(text, "", "", "", ""),
            style = aboutTextStyle(fonts.regular, 14.sp, colors.link).copy(textDecoration = TextDecoration.Underline),
            textAlign = textAlign,
        )
    }
}

/**
 * Hold the logo for a second and it spins up; at full speed it reports [onMaximumSpeed] and keeps
 * spinning until released, then winds down. Dragging away or a parent scroll stops it. Touch only:
 * the logo has no semantics, so it is neither announced nor a focus stop.
 */
@Composable
private fun SpinningLogo(onMaximumSpeed: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val rotation = remember { Animatable(0f) }
    val currentOnMaximumSpeed by rememberUpdatedState(onMaximumSpeed)
    val accelerate = remember(context) { context.easing(android.R.interpolator.accelerate_quad) }
    val decelerate = remember(context) { context.easing(android.R.interpolator.decelerate_cubic) }

    Image(
        painter = painterResource(R.drawable.ic_simplenote_24dp),
        contentDescription = null,
        modifier = modifier
            .graphicsLayer { rotationZ = rotation.value }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    var maximumSpeed = false
                    val spin: Job = scope.launch {
                        delay(LOGO_LONG_PRESS_MS)
                        rotation.snapTo(360f)
                        rotation.animateTo(0f, tween(LOGO_ACCELERATE_MS, easing = accelerate))
                        maximumSpeed = true
                        currentOnMaximumSpeed()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        while (true) {
                            rotation.snapTo(360f)
                            rotation.animateTo(0f, tween(LOGO_FULL_SPEED_MS, easing = LinearEasing))
                        }
                    }
                    val slop = viewConfiguration.touchSlop
                    var released = false
                    while (true) {
                        val change = awaitPointerEvent(PointerEventPass.Final).changes.first()
                        if (change.isConsumed) {
                            break
                        }
                        if (!change.pressed) {
                            released = true
                            break
                        }
                        val position = change.position
                        val outside = position.x < -slop || position.y < -slop ||
                            position.x > size.width + slop || position.y > size.height + slop
                        if (outside) {
                            break
                        }
                    }
                    spin.cancel()
                    scope.launch {
                        if (released && maximumSpeed) {
                            rotation.snapTo(360f)
                            rotation.animateTo(0f, tween(LOGO_DECELERATE_MS, easing = decelerate))
                        } else {
                            rotation.snapTo(0f)
                        }
                    }
                }
            },
    )
}

private val MinimumTouchTarget = 48.dp

private class AboutColors(
    val background: Color,
    val subtitle: Color,
    val divider: Color,
    val arrow: Color,
    val link: Color,
    val highlight: Color,
)

@Composable
private fun aboutColors() = AboutColors(
    background = colorResource(R.color.blue),
    subtitle = colorResource(R.color.simplenote_blue_5),
    divider = colorResource(R.color.simplenote_blue_40),
    arrow = colorResource(R.color.simplenote_blue_10),
    link = colorResource(R.color.blue_5),
    highlight = colorResource(R.color.item_default_dark),
)

private class AboutFonts(val regular: FontFamily, val medium: FontFamily)

@Composable
private fun aboutFonts(): AboutFonts {
    val context = LocalContext.current
    return remember(context) {
        fun family(name: String) = TypefaceCache.getTypeface(context, name)?.let(::FontFamily) ?: FontFamily.Default
        AboutFonts(
            regular = family(TypefaceCache.TYPEFACE_NAME_ROBOTO_REGULAR),
            medium = family(TypefaceCache.TYPEFACE_NAME_ROBOTO_MEDIUM),
        )
    }
}

@Suppress("DEPRECATION")
private fun aboutTextStyle(fontFamily: FontFamily, fontSize: TextUnit, color: Color) = TextStyle(
    color = color,
    fontSize = fontSize,
    fontFamily = fontFamily,
    platformStyle = PlatformTextStyle(includeFontPadding = true),
)

/** content_width is a fixed column on large screens and match_parent (-1px) elsewhere. */
@Composable
private fun Modifier.contentWidth(): Modifier {
    val width: Dp = dimensionResource(R.dimen.content_width)
    return if (width > 0.dp) widthIn(max = width).fillMaxWidth() else fillMaxWidth()
}

private fun android.content.Context.easing(interpolatorRes: Int): Easing {
    val interpolator = AnimationUtils.loadInterpolator(this, interpolatorRes)
    return Easing { fraction -> interpolator.getInterpolation(fraction) }
}
