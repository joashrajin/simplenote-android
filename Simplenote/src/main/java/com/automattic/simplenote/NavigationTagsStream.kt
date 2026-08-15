package com.automattic.simplenote

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.automattic.simplenote.models.Tag
import com.automattic.simplenote.usecases.GetTagsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Lifecycle bridge between the Java activity and the repository-backed navigation-tag flow. */
class NavigationTagsStream(
    private val getTagsUseCase: GetTagsUseCase,
    private val scope: CoroutineScope,
) {
    fun interface SortProvider {
        fun sortAlphabetically(): Boolean
    }

    fun interface Listener {
        fun onNavigationTags(tags: List<@JvmSuppressWildcards Tag>, isInitial: Boolean)
    }

    fun start(lifecycle: Lifecycle, sortProvider: SortProvider, listener: Listener) {
        scope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                var isInitial = true
                getTagsUseCase.navigationTags(sortProvider.sortAlphabetically()).collect { tags ->
                    if (isActive) {
                        listener.onNavigationTags(tags, isInitial)
                        isInitial = false
                    }
                }
            }
        }
    }
}
