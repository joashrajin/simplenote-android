package com.automattic.simplenote.widgets

import android.content.Context
import android.graphics.Typeface
import android.widget.TextView
import java.util.Hashtable

object TypefaceCache {
    const val TYPEFACE_NAME_ROBOTO_REGULAR = "Roboto-Regular.ttf"
    const val TYPEFACE_NAME_ROBOTO_MEDIUM = "Roboto-Medium.ttf"

    private val typefaceCache = Hashtable<String, Typeface>()

    fun getTypeface(context: Context?, typefaceName: String?): Typeface? {
        if (context == null || typefaceName == null) {
            return null
        }

        if (!typefaceCache.containsKey(typefaceName)) {
            val typeface = Typeface.createFromAsset(context.applicationContext.assets, "fonts/$typefaceName")
            if (typeface != null) {
                typefaceCache[typefaceName] = typeface
            }
        }

        return typefaceCache[typefaceName]
    }

    /*
     * sets the typeface for a TextView (or TextView descendant such as EditText or Button) based on
     * the passed attributes, defaults to normal typeface
     */
    internal fun setCustomTypeface(context: Context?, view: TextView?, typefaceName: String?) {
        if (context == null || view == null || typefaceName == null) {
            return
        }

        // skip at design-time
        if (view.isInEditMode) return

        val typeface = getTypeface(context, typefaceName)
        if (typeface != null) {
            view.typeface = typeface
        }
    }
}
