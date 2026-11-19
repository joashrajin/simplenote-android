package com.automattic.simplenote.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regenerate with a device or emulator on API 33+ attached:
 * `./gradlew :Simplenote:generateReleaseBaselineProfile`. The plugin writes baseline-prof.txt and
 * startup-prof.txt to Simplenote/src/release/generated/baselineProfiles.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(packageName = TARGET_PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        waitForLaunchDestination()
    }

    @Test
    fun firstScreenJourney() = rule.collect(packageName = TARGET_PACKAGE) {
        pressHome()
        startActivityAndWait()
        when (waitForLaunchDestination()) {
            LaunchDestination.NOTE_LIST -> openFirstNoteAndGoBack()
            LaunchDestination.LANDING -> openSignupAndGoBack()
        }
    }
}
