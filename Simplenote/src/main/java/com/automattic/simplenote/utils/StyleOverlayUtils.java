package com.automattic.simplenote.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;

import com.automattic.simplenote.R;

public final class StyleOverlayUtils {
    private StyleOverlayUtils() {
    }

    @StyleRes
    public static int getStyleOverlay(@NonNull Context context) {
        if (!PrefUtils.isPremium(context)) {
            return R.style.ThemeOverlay_Simplestyle_Style_Default;
        }

        return getStyleOverlay(PrefUtils.getStyleIndexSelected(context));
    }

    @StyleRes
    public static int getStyleOverlay(int styleIndex) {
        switch (styleIndex) {
            case ThemeUtils.STYLE_BLACK:
                return R.style.ThemeOverlay_Simplestyle_Style_Black;
            case ThemeUtils.STYLE_CLASSIC:
                return R.style.ThemeOverlay_Simplestyle_Style_Classic;
            case ThemeUtils.STYLE_MATRIX:
                return R.style.ThemeOverlay_Simplestyle_Style_Matrix;
            case ThemeUtils.STYLE_MONO:
                return R.style.ThemeOverlay_Simplestyle_Style_Mono;
            case ThemeUtils.STYLE_PUBLICATION:
                return R.style.ThemeOverlay_Simplestyle_Style_Publication;
            case ThemeUtils.STYLE_SEPIA:
                return R.style.ThemeOverlay_Simplestyle_Style_Sepia;
            case ThemeUtils.STYLE_DEFAULT:
            default:
                return R.style.ThemeOverlay_Simplestyle_Style_Default;
        }
    }
}
