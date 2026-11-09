package com.automattic.simplenote

import androidx.test.runner.AndroidJUnitRunner

/**
 * Runner for the fastlane screenshots lane (fastlane/Fastfile): ScreenshotTest signs in to a real
 * account and drives the full app, so it must run on the production [Simplenote] application from
 * the manifest rather than the Hilt test application that [SimplenoteAppRunner] installs.
 */
class ScreenshotAppRunner : AndroidJUnitRunner()
