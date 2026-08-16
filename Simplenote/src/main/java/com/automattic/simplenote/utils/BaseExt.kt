package com.automattic.simplenote.utils

import android.widget.Toast
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

fun AppCompatActivity.toast(@StringRes resId: Int, length: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, getString(resId), length).show()
}

fun AppCompatActivity.getColorStr(@ColorRes color: Int): String {
    return Integer.toHexString(ContextCompat.getColor(this, color) and 0x00ffffff).padStart(6, '0')
}
