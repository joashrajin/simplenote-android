package com.automattic.simplenote

import android.content.Intent
import android.os.Bundle
import android.text.Html
import android.text.SpannableString
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.automattic.simplenote.utils.PrefUtils
import com.automattic.simplenote.utils.SystemBarUtils
import com.automattic.simplenote.utils.ThemeUtils
import com.automattic.simplenote.utils.ThemeUtils.STYLE_ARRAY
import com.automattic.simplenote.utils.ThemeUtils.STYLE_BLACK
import com.automattic.simplenote.utils.ThemeUtils.STYLE_CLASSIC
import com.automattic.simplenote.utils.ThemeUtils.STYLE_MATRIX
import com.automattic.simplenote.utils.ThemeUtils.STYLE_MONO
import com.automattic.simplenote.utils.ThemeUtils.STYLE_PUBLICATION
import com.automattic.simplenote.utils.ThemeUtils.STYLE_SEPIA

class StyleActivity : ThemedAppCompatActivity() {
    private lateinit var layoutManager: LinearLayoutManager

    @Suppress("DEPRECATION")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_style)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        val title = SpannableString(getString(R.string.style))

        supportActionBar?.let { actionBar ->
            actionBar.title = title
            actionBar.setDisplayHomeAsUpEnabled(true)
        }

        val styles = STYLE_ARRAY.indices.map { PrefUtils.getStyleNameFromIndex(this, it) }

        val list = findViewById<RecyclerView>(R.id.list)
        list.adapter = StyleAdapter(styles)
        layoutManager = LinearLayoutManager(this)
        list.layoutManager = layoutManager

        // Edge-to-edge comes from ThemedAppCompatActivity; wire this screen's insets. The list
        // keeps its own bottom padding as a floor so the layout is untouched without insets.
        SystemBarUtils.applyInsets(
            findViewById(R.id.main_parent_view),
            toolbar,
            list,
            true
        )

        if (intent.hasExtra(EXTRA_SCROLL)) {
            layoutManager.onRestoreInstanceState(intent.getParcelableExtra(EXTRA_SCROLL))
        }
    }

    /**
     *  Overrides recreate to allow restoring the scroll position
     */
    @Suppress("DEPRECATION")
    override fun recreate() {
        val intent = Intent(this, StyleActivity::class.java)
        intent.putExtra(EXTRA_SCROLL, layoutManager.onSaveInstanceState())
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    private inner class StyleAdapter(styles: List<String>) : RecyclerView.Adapter<StyleAdapter.StyleHolder>() {
        private val styles = ArrayList(styles)
        private var selectedPosition = PrefUtils.getStyleIndexSelected(this@StyleActivity)

        override fun getItemCount(): Int = styles.size

        override fun getItemViewType(position: Int): Int = position

        @Suppress("DEPRECATION")
        override fun onBindViewHolder(holder: StyleHolder, position: Int) {
            val style = styles[position]
            holder.title.text = style
            holder.content.text = Html.fromHtml(
                String.format(
                    resources.getString(R.string.style_preview),
                    "<u><span style=\"color:#",
                    Integer.toHexString(
                        ContextCompat.getColor(
                            holder.view.context,
                            getLinkColorFromStyle(position)
                        ) and 0xffffff
                    ),
                    "\">",
                    "</span></u>"
                )
            )
            holder.view.isActivated = selectedPosition == position
            holder.view.setOnClickListener {
                notifyItemChanged(selectedPosition)
                selectedPosition = holder.adapterPosition
                notifyItemChanged(selectedPosition)
                PrefUtils.setStyleIndex(this@StyleActivity, holder.adapterPosition)
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, type: Int): StyleHolder {
            val (theme, layout) = when (type) {
                STYLE_BLACK -> R.style.Style_Black to R.layout.style_list_row_black
                STYLE_CLASSIC -> R.style.Style_Classic to R.layout.style_list_row_default
                STYLE_MATRIX -> R.style.Style_Matrix to R.layout.style_list_row_matrix
                STYLE_MONO -> R.style.Style_Mono to R.layout.style_list_row_mono
                STYLE_PUBLICATION -> R.style.Style_Publication to R.layout.style_list_row_publication
                STYLE_SEPIA -> R.style.Style_Sepia to R.layout.style_list_row_sepia
                else -> R.style.Style_Default to R.layout.style_list_row_default
            }
            return StyleHolder(
                LayoutInflater.from(ContextThemeWrapper(parent.context, theme)).inflate(layout, parent, false)
            )
        }

        @ColorRes
        private fun getLinkColorFromStyle(position: Int): Int {
            val isLight = ThemeUtils.isLightTheme(this@StyleActivity)
            return when (position) {
                STYLE_BLACK -> if (isLight) android.R.color.black else android.R.color.white
                STYLE_CLASSIC -> if (isLight) R.color.blue_50 else R.color.blue_20
                STYLE_MATRIX -> if (isLight) R.color.green_50 else R.color.green_20
                STYLE_MONO -> if (isLight) R.color.gray_50 else R.color.gray_20
                STYLE_PUBLICATION -> if (isLight) R.color.red_50 else R.color.red_20
                STYLE_SEPIA -> if (isLight) R.color.orange_50 else R.color.orange_20
                else -> if (isLight) R.color.simplenote_blue_50 else R.color.simplenote_blue_20
            }
        }

        inner class StyleHolder(val view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.preview_title)
            val content: TextView = view.findViewById(R.id.preview_content)
        }
    }

    companion object {
        private const val EXTRA_SCROLL = "EXTRA_SCROLL"
    }
}
