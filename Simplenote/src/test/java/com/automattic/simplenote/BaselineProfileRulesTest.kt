package com.automattic.simplenote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BaselineProfileRulesTest {
    private val baselineRules = readRules("$GENERATED/baseline-prof.txt") + readRules(NOTE_LIST_ROWS)
    private val startupRules = readRules("$GENERATED/startup-prof.txt")

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

    // Rows are only bound when the account has notes, and the generator runs signed out, so these come from the
    // hand-maintained file AGP merges with the generated profile.
    @Test
    fun noteListRowsAreProfiled() {
        val adapter = "Lcom/automattic/simplenote/NoteListFragment\$NotesCursorAdapter;->"
        val hasRowRule = baselineRules.any {
            val method = it.substringAfter(adapter, missingDelimiterValue = "")
            method.startsWith("getView(") || method.startsWith("**(")
        }

        assertTrue("No NotesCursorAdapter.getView rule in the generated or hand-maintained profile", hasRowRule)
    }

    private fun readRules(path: String) = File(path)
        .readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }

    private fun classDescriptor(rule: String) = rule.trimStart('H', 'S', 'P').substringBefore("->")

    private fun classExists(name: String) = runCatching {
        Class.forName(name, false, javaClass.classLoader)
    }.isSuccess

    private companion object {
        const val GENERATED = "src/release/generated/baselineProfiles"
        const val NOTE_LIST_ROWS = "src/main/baselineProfiles/note-list-rows.txt"
    }
}
