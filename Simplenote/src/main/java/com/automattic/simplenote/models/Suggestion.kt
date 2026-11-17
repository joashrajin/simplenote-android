package com.automattic.simplenote.models

import androidx.annotation.IntDef

class Suggestion(
    val name: String?,
    @param:Type @get:Type val type: Int,
) {
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(Type.HISTORY, Type.QUERY, Type.TAG)
    annotation class Type {
        companion object {
            const val HISTORY = 0
            const val QUERY = 1
            const val TAG = 2
        }
    }
}
