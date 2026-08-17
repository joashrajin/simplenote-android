package com.automattic.simplenote.viewmodels

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.ArrayDeque

class SingleLiveEventTest {
    private val taskExecutor = QueuedTaskExecutor()

    @Before
    fun setUp() {
        ArchTaskExecutor.getInstance().setDelegate(taskExecutor)
    }

    @After
    fun tearDown() {
        taskExecutor.clear()
        ArchTaskExecutor.getInstance().setDelegate(null)
    }

    @Test
    fun replacementObserverDoesNotReceiveConsumedValueWhilePostIsQueued() {
        val event = SingleLiveEvent<String>()
        val originalOwner = startedOwner()
        val originalValues = mutableListOf<String>()
        event.observe(originalOwner) { originalValues.add(it) }
        event.value = "previous"
        assertEquals(listOf("previous"), originalValues)

        event.postValue("next")
        originalOwner.destroy()
        val replacementValues = mutableListOf<String>()
        event.observe(startedOwner()) { replacementValues.add(it) }

        assertTrue(replacementValues.isEmpty())
        taskExecutor.drainMainThreadTasks()
        assertEquals(listOf("next"), replacementValues)
    }

    @Test
    fun replacementObserverDoesNotReceiveConsumedCallWhileAsyncCallIsQueued() {
        val event = SingleLiveEvent<Unit?>()
        val originalOwner = startedOwner()
        var originalCallCount = 0
        event.observe(originalOwner) { originalCallCount++ }
        event.call()
        assertEquals(1, originalCallCount)

        event.asyncCall()
        originalOwner.destroy()
        var replacementCallCount = 0
        event.observe(startedOwner()) { replacementCallCount++ }

        assertEquals(0, replacementCallCount)
        taskExecutor.drainMainThreadTasks()
        assertEquals(1, replacementCallCount)
    }

    @Test
    fun queuedPostsRetainLiveDataCoalescing() {
        val event = SingleLiveEvent<String>()
        val values = mutableListOf<String>()
        event.observe(startedOwner()) { values.add(it) }

        event.postValue("first")
        event.postValue("latest")

        assertEquals(1, taskExecutor.pendingMainThreadTaskCount)
        taskExecutor.drainMainThreadTasks()
        assertEquals(listOf("latest"), values)
    }

    @Test
    fun promotedPostWaitsForAnActiveObserver() {
        val event = SingleLiveEvent<String>()
        event.postValue("next")
        taskExecutor.drainMainThreadTasks()
        val values = mutableListOf<String>()

        event.observe(startedOwner()) { values.add(it) }

        assertEquals(listOf("next"), values)
    }

    private fun startedOwner() = TestLifecycleOwner().apply { start() }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle = registry

        fun start() {
            registry.currentState = Lifecycle.State.STARTED
        }

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
        }
    }

    private class QueuedTaskExecutor : TaskExecutor() {
        private val mainThreadTasks = ArrayDeque<Runnable>()
        val pendingMainThreadTaskCount: Int
            get() = mainThreadTasks.size

        override fun executeOnDiskIO(runnable: Runnable) {
            runnable.run()
        }

        override fun postToMainThread(runnable: Runnable) {
            mainThreadTasks.addLast(runnable)
        }

        override fun isMainThread() = true

        fun drainMainThreadTasks() {
            while (mainThreadTasks.isNotEmpty()) {
                mainThreadTasks.removeFirst().run()
            }
        }

        fun clear() {
            mainThreadTasks.clear()
        }
    }
}
