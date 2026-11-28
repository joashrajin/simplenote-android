package com.automattic.simplenote

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Guardrail test ensuring service-locator getters (getSimperium, getNotesBucket, getTagsBucket,
 * getPreferencesBucket, getAccountBucket, isLoggedIn) are not re-added to [Simplenote].
 * Dependencies must be provided via Hilt dependency injection or EntryPoints.
 */
class SimplenoteServiceLocatorGuardrailTest {

    @Test
    fun simplenoteApplicationClassHasNoServiceLocatorGetters() {
        val methodNames = Simplenote::class.java.declaredMethods.map { it.name }.toSet()

        val forbiddenGetters = listOf(
            "getSimperium",
            "getNotesBucket",
            "getTagsBucket",
            "getPreferencesBucket",
            "getAccountBucket",
            "isLoggedIn"
        )

        for (forbidden in forbiddenGetters) {
            assertFalse(
                "Simplenote must not declare service locator method '$forbidden()'. Use Hilt DI instead.",
                methodNames.contains(forbidden)
            )
        }
    }
}
