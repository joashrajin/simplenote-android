package com.automattic.simplenote

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Default instrumentation runner (see testInstrumentationRunner in Simplenote/build.gradle): the
 * UI tests run on [HiltTestApplication] with [TestDataModule] replacing the production
 * [com.automattic.simplenote.di.DataModule], so @HiltAndroidTest classes get in-memory buckets.
 * The fastlane screenshots lane uses [ScreenshotAppRunner] instead, which keeps the production
 * application class.
 */
class SimplenoteAppRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader, className: String, context: Context): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}
