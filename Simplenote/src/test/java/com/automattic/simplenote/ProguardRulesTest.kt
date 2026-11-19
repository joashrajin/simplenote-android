package com.automattic.simplenote

import com.automattic.simplenote.analytics.AnalyticsTracker
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ProguardRulesTest {
    private val rules = File("proguard-rules.pro").readLines().map { it.trim() }

    // R8 ignores a keep rule that matches nothing, so moving or renaming Stat would silently let
    // its name be obfuscated again and break widget taps that predate an update.
    @Test
    fun widgetClickExtraClassKeepsItsName() {
        val rule = "-keepnames class ${AnalyticsTracker.Stat::class.java.name}"

        assertTrue("proguard-rules.pro is missing `$rule`", rule in rules)
    }
}
