package com.automattic.simplenote.utils.locale

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Pins the `values-*` locale folders against the hand-maintained `res/xml/locales_config.xml`, without going through
 * Gradle: one folder per language, under the code Android's resource lookup actually reads, and every folder either
 * offered once in the config or deliberately left out.
 */
class LocaleFoldersTest {
    private val resDir = listOf(File("src/main/res"), File("Simplenote/src/main/res")).first { it.isDirectory }

    private val localeFolders = resDir.listFiles()!!.mapNotNull { folder ->
        LOCALE_QUALIFIER.find(folder.name)?.let { match -> folder to match }
    }

    private val folderTags = localeFolders.map { (_, match) ->
        val (language, region) = match.destructured
        if (region.isEmpty()) language else "$language-$region"
    }

    private val configTags: List<String> by lazy {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val locales = factory.newDocumentBuilder().parse(File(resDir, "xml/locales_config.xml"))
            .getElementsByTagName("locale")
        (0 until locales.length).map { locales.item(it).attributes.getNamedItemNS(ANDROID_NAMESPACE, "name").nodeValue }
    }

    @Test
    fun localeFoldersMapToTheExpectedTags() {
        assertEquals(EXPECTED_FOLDER_TAGS, folderTags.sorted())
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

    @Test
    fun localeConfigListsEachLanguageOnce() {
        assertEquals(configTags.distinct(), configTags)
        assertEquals("en-US", configTags.first())
    }

    @Test
    fun everyLocaleFolderIsOfferedOrDeliberatelyLeftOut() {
        val offeredFolderTags = configTags.drop(1).map { tag ->
            val language = tag.substringBefore('-')
            (LEGACY_CODES[language] ?: language) + tag.removePrefix(language)
        }

        assertEquals(folderTags.sorted(), (offeredFolderTags + NOT_OFFERED_FOLDER_TAGS).sorted())
    }

    // Android 6.0 matches a region folder only for that exact region, so values-zh is what keeps zh-HK/SG/MO in
    // Chinese there; 7.0+ resolves them through script matching to values-zh-rCN or values-zh-rTW instead.
    @Test
    fun unqualifiedChineseMirrorsSimplifiedChinese() {
        assertEquals(
            File(resDir, "values-zh-rCN").canonicalFile,
            File(resDir, "values-zh").canonicalFile,
        )
    }

    companion object {
        private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
        private val LOCALE_QUALIFIER = Regex("^values-(?!car\\b)([a-z]{2,3})(?:-r([A-Z]{2}))?(?:-.*)?$")
        private val LEGACY_CODES = mapOf("he" to "iw", "id" to "in", "yi" to "ji")

        // zh is the pre-7.0 fallback alias of zh-CN; pl and cy are ~19% translated and not on GlotPress.
        private val NOT_OFFERED_FOLDER_TAGS = listOf("cy", "pl", "zh")

        private val EXPECTED_FOLDER_TAGS = listOf(
            "ar", "cy", "de", "es", "fr", "in", "it", "iw", "ja", "ko", "nl", "pl", "pt-BR", "ru", "sv", "tr", "zh",
            "zh-CN", "zh-TW",
        )
    }
}
