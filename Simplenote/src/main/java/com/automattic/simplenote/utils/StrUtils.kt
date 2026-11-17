package com.automattic.simplenote.utils

import android.text.TextUtils
import androidx.core.util.PatternsCompat.EMAIL_ADDRESS

object StrUtils {
    const val SPACE_STRING = " "

    @JvmStatic
    fun isEmail(str: String?): Boolean {
        // TextUtils.isEmpty has no Kotlin contract, so the compiler cannot smart-cast str here.
        return !TextUtils.isEmpty(str) && EMAIL_ADDRESS.matcher(str!!).matches()
    }

    @JvmStatic
    fun notNullStr(value: String?): String {
        return value ?: ""
    }

    @JvmStatic
    fun strToInt(value: String?, defaultInt: Int): Int {
        if (value == null) {
            return defaultInt
        }
        return try {
            value.toInt()
        } catch (e: NumberFormatException) {
            defaultInt
        }
    }

    @JvmStatic
    fun isSameStr(s1: String?, s2: String?): Boolean {
        return s1 == s2
    }
}
