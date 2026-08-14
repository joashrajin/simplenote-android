package com.automattic.simplenote

import org.junit.Assert.assertTrue
import org.junit.Test

class SimplenoteAnalyticsBridgeTest {
    @Test
    fun analyticsDefaultsToEnabledBeforeTheApplicationInitializes() {
        // On the JVM the application statics are never set, which is exactly the pre-init state.
        assertTrue(Simplenote.analyticsIsEnabled())
    }
}
