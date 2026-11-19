package com.automattic.simplenote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BaselineProfileRulesTest {
    private val baselineRules = readRules("baseline-prof.txt")
    private val startupRules = readRules("startup-prof.txt")

    // A package-wide rule AOT-compiles every method under it, which bloats install-time dexopt and the odex.
    // Generated profiles never contain one; a class-scoped `Foo;->**(**)**` hand-seed rule is allowed.
    @Test
    fun noRuleMatchesAWholePackage() {
        val wildcardClasses = (baselineRules + startupRules).filter { '*' in classDescriptor(it) }

        assertEquals("Package-wide profile rules", emptyList<String>(), wildcardClasses)
    }

    @Test
    fun everyStartupClassIsInTheBaselineProfile() {
        val baselineClasses = baselineRules.map(::classDescriptor).toSet()
        val missing = startupRules.map(::classDescriptor).filterNot { it in baselineClasses }

        assertEquals("startup-prof.txt classes missing from baseline-prof.txt", emptyList<String>(), missing)
    }

    // A rule naming a class that no longer exists matches nothing, so renames would silently shrink the profile.
    @Test
    fun everyAppAndSimperiumRuleNamesAnExistingClass() {
        val missing = (baselineRules + startupRules)
            .map(::classDescriptor)
            .distinct()
            .filter { it.startsWith("Lcom/automattic/simplenote/") || it.startsWith("Lcom/simperium/") }
            .filterNot { "ExternalSynthetic" in it }
            .map { it.removePrefix("L").removeSuffix(";").replace('/', '.') }
            .filterNot(::classExists)

        assertEquals("Profile rules for classes that do not exist", emptyList<String>(), missing)
    }

    // Rows are only bound when the account has notes, so a profile generated on a signed-out device loses these
    // and with them the cold start of every signed-in user. See BaselineProfileGenerator in :baselineprofile.
    @Test
    fun noteListRowsAreProfiled() {
        val adapter = "Lcom/automattic/simplenote/NoteListFragment\$NotesCursorAdapter;->"
        val hasRowRule = baselineRules.any {
            val method = it.substringAfter(adapter, missingDelimiterValue = "")
            method.startsWith("getView(") || method.startsWith("**(")
        }

        assertTrue("baseline-prof.txt has no NotesCursorAdapter.getView rule; was it generated signed out?", hasRowRule)
    }

    private fun readRules(name: String) = File("src/release/generated/baselineProfiles/$name")
        .readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }

    private fun classDescriptor(rule: String) = rule.trimStart('H', 'S', 'P').substringBefore("->")

    private fun classExists(name: String) = runCatching {
        Class.forName(name, false, javaClass.classLoader)
    }.isSuccess
}
