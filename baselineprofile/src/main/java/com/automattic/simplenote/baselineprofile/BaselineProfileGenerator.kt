package com.automattic.simplenote.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val ALLOW_LOGGED_OUT_ARG = "allowLoggedOutProfile"

/**
 * Regenerate with `./gradlew :Simplenote:generateReleaseBaselineProfile`. The plugin writes baseline-prof.txt and
 * startup-prof.txt to Simplenote/src/release/generated/baselineProfiles.
 *
 * The profile must come from a signed-in device, because the cold start most users run goes to the note list and
 * editor. A fresh install only reaches the landing screen, and a profile taken there would drop the note-list rules
 * (BaselineProfileRulesTest in :Simplenote checks for them). So [firstScreenJourney] fails on the landing screen
 * unless `-Pandroid.testInstrumentationRunnerArguments.allowLoggedOutProfile=true` is passed, which is for trying
 * the journeys only, never for a profile to commit. There is no fake-account seam: Simperium would send a
 * forged token to the production server and log out partway through the journey.
 *
 * A hand sign-in is wiped by the connected test task, which uninstalls the app after each run, and on unrooted
 * devices below API 34, where Macrobenchmark reinstalls the app before it collects. To keep one:
 * 1. Use an API 34+ device, and pass `-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true` to every run.
 * 2. Run `./gradlew :Simplenote:installNonMinifiedRelease`, sign in with a test account and create a few notes.
 * 3. Run the generate task, then commit the output.
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
            LaunchDestination.LANDING -> {
                check(allowLoggedOutProfile()) {
                    "The app is signed out, so this profile would miss the note list. Sign in first (see the " +
                        "BaselineProfileGenerator KDoc) or pass the $ALLOW_LOGGED_OUT_ARG=true runner argument."
                }
                openSignupAndGoBack()
            }
        }
    }

    private fun allowLoggedOutProfile() =
        InstrumentationRegistry.getArguments().getString(ALLOW_LOGGED_OUT_ARG).toBoolean()
}
