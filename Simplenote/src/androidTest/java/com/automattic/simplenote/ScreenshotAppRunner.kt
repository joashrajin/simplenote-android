package com.automattic.simplenote

import androidx.test.runner.AndroidJUnitRunner

/**
 * Runner for the fastlane screenshots lane (fastlane/Fastfile): ScreenshotTest signs in to a real
 * account and drives the full app, so it must run on the production [Simplenote] application from
 * the manifest rather than the Hilt test application that [SimplenoteAppRunner] installs. The lane
 * assembles the test APK with -PscreenshotInstrumentation, which makes build.gradle declare this
 * runner as the APK's instrumentation (the manifest merger permits only one declaration).
 */
class ScreenshotAppRunner : AndroidJUnitRunner()
