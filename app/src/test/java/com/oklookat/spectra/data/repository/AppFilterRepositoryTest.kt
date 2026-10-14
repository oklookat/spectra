package com.oklookat.spectra.data.repository

import com.google.gson.Gson
import com.oklookat.spectra.model.PackageCategoryRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppFilterRepositoryTest {

    @Test
    fun testRussianPackageRegexMatching() {
        val jsonSample = """
        {
            "version": 5,
            "rules": [
                {
                    "package_name_regex": [
                        "^ru\\..*",
                        "^air\\.ru.*",
                        "^az\\.vtb.*",
                        "^biz\\.growapp.*"
                    ]
                }
            ]
        }
        """.trimIndent()

        val gson = Gson()
        val ruleData = gson.fromJson(jsonSample, PackageCategoryRule::class.java)
        val regexStrings = ruleData.rules?.flatMap { it.packageNameRegex ?: emptyList() } ?: emptyList()

        val compiledRegexes = regexStrings.mapNotNull {
            try { Regex(it) } catch (_: Exception) { null }
        }

        assertTrue(compiledRegexes.any { it.containsMatchIn("ru.yandex.searchplugin") })
        assertTrue(compiledRegexes.any { it.containsMatchIn("air.ru.someapp") })
        assertTrue(compiledRegexes.any { it.containsMatchIn("az.vtb.mobile") })
        assertTrue(compiledRegexes.any { it.containsMatchIn("biz.growapp.test") })

        assertFalse(compiledRegexes.any { it.containsMatchIn("com.google.android.youtube") })
        assertFalse(compiledRegexes.any { it.containsMatchIn("org.telegram.messenger") })
    }
}
