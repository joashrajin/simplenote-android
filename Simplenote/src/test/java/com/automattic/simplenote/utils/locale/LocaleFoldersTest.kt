package com.automattic.simplenote.utils.locale

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Pins the `values-*` locale folders that AGP's `generateLocaleConfig` turns into the per-app language list, without
 * going through Gradle: one folder per language, under the code Android's resource lookup actually reads.
 */
class LocaleFoldersTest {
    private val resDir = listOf(File("src/main/res"), File("Simplenote/src/main/res")).first { it.isDirectory }

    private val localeFolders = resDir.listFiles()!!.mapNotNull { folder ->
        LOCALE_QUALIFIER.find(folder.name)?.let { match -> folder to match }
    }

    @Test
    fun localeFoldersMapToTheExpectedTags() {
        val tags = localeFolders.map { (_, match) ->
            val (language, region) = match.destructured
            if (region.isEmpty()) language else "$language-$region"
        }

        assertEquals(EXPECTED_FOLDER_TAGS, tags.sorted())
    }

    @Test
    fun eachLanguageAndRegionHasOneFolder() {
        val keys = localeFolders.map { (_, match) ->
            val (language, region) = match.destructured
            (LEGACY_CODES[language] ?: language) to region
        }

        assertEquals(keys.distinct(), keys)
    }

    // ResourcesImpl rewrites he/id/yi to iw/in/ji before matching, so the modern-code folders are never read.
    @Test
    fun legacyLanguagesUseTheCodesResourceLookupReads() {
        val modernCodeFolders = localeFolders.filter { (_, match) -> match.groupValues[1] in LEGACY_CODES }

        assertEquals(emptyList<String>(), modernCodeFolders.map { (folder, _) -> folder.name })
    }

    companion object {
        private val LOCALE_QUALIFIER = Regex("^values-(?!car\\b)([a-z]{2,3})(?:-r([A-Z]{2}))?(?:-.*)?$")
        private val LEGACY_CODES = mapOf("he" to "iw", "id" to "in", "yi" to "ji")

        val EXPECTED_FOLDER_TAGS = listOf(
            "ar", "cy", "de", "es", "fr", "in", "it", "iw", "ja", "ko", "nl", "pl", "pt-BR", "ru", "sv", "tr",
            "zh-CN", "zh-TW",
        )
    }
}
