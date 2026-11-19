package com.automattic.simplenote

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.automattic.simplenote.compose.SimplenoteTheme
import com.automattic.simplenote.compose.StyleOption
import com.automattic.simplenote.compose.StyleScreen
import com.automattic.simplenote.utils.PrefUtils
import com.automattic.simplenote.utils.ThemeUtils.STYLE_ARRAY
import com.automattic.simplenote.utils.ThemeUtils.STYLE_BLACK
import com.automattic.simplenote.utils.ThemeUtils.STYLE_CLASSIC
import com.automattic.simplenote.utils.ThemeUtils.STYLE_MATRIX
import com.automattic.simplenote.utils.ThemeUtils.STYLE_MONO
import com.automattic.simplenote.utils.ThemeUtils.STYLE_PUBLICATION
import com.automattic.simplenote.utils.ThemeUtils.STYLE_SEPIA

/**
 * The style picker, a single ComposeView hosting [StyleScreen] under [SimplenoteTheme].
 *
 * Selecting a row writes [PrefUtils.PREF_STYLE_INDEX]; ThemedAppCompatActivity's preference
 * listener then calls [recreate], which relaunches this screen under the new style with the
 * list's scroll position in [EXTRA_SCROLL].
 */
class StyleActivity : ThemedAppCompatActivity() {
    private lateinit var listState: LazyListState
    private var selectedIndex by mutableIntStateOf(0)

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        selectedIndex = PrefUtils.getStyleIndexSelected(this)
        val scroll = savedInstanceState?.getIntArray(EXTRA_SCROLL) ?: intent.getIntArrayExtra(EXTRA_SCROLL)
        listState = LazyListState(
            firstVisibleItemIndex = scroll?.getOrNull(0) ?: 0,
            firstVisibleItemScrollOffset = scroll?.getOrNull(1) ?: 0,
        )
        val styles = STYLE_ARRAY.map { index ->
            styleOption(index, PrefUtils.getStyleNameFromIndex(this, index))
        }

        setContent {
            SimplenoteTheme {
                StyleScreen(
                    styles = styles,
                    selectedIndex = selectedIndex,
                    listState = listState,
                    onStyleSelected = { index ->
                        selectedIndex = index
                        PrefUtils.setStyleIndex(this, index)
                    },
                    onNavigateUp = { onSupportNavigateUp() },
                )
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putIntArray(EXTRA_SCROLL, scrollPosition())
    }

    /**
     *  Overrides recreate to allow restoring the scroll position
     */
    @Suppress("DEPRECATION")
    override fun recreate() {
        val intent = Intent(this, StyleActivity::class.java)
        intent.putExtra(EXTRA_SCROLL, scrollPosition())
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private fun scrollPosition() =
        intArrayOf(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)

    private fun styleOption(index: Int, name: String): StyleOption = when (index) {
        STYLE_BLACK -> StyleOption(
            index,
            name,
            R.style.Style_Black,
            titleColor = R.color.style_preview_black_title,
            contentColor = R.color.style_preview_black_content,
        )
        STYLE_CLASSIC -> StyleOption(index, name, R.style.Style_Classic)
        STYLE_MATRIX -> StyleOption(index, name, R.style.Style_Matrix)
        STYLE_MONO -> StyleOption(index, name, R.style.Style_Mono)
        STYLE_PUBLICATION -> StyleOption(index, name, R.style.Style_Publication)
        STYLE_SEPIA -> StyleOption(index, name, R.style.Style_Sepia)
        else -> StyleOption(index, name, R.style.Style_Default)
    }

    companion object {
        private const val EXTRA_SCROLL = "EXTRA_SCROLL"
    }
}
