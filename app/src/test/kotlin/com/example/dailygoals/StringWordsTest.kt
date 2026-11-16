package com.example.dailygoals

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** The words the app agreed to drop from what users read, so they cannot creep back in. */
class StringWordsTest {
    private val banned = Regex("\b(tick|ticks|ticked|ticking|unticked|untick|window|windows|punishment|punishments|habit|habits)\b", RegexOption.IGNORE_CASE)

    @Test
    fun userFacingTextAvoidsTheDroppedWords() {
        val xml = File("src/main/res/values/strings.xml").readText()
        val entry = Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL)
        val offenders = entry.findAll(xml)
            .filterNot { it.groupValues[1].startsWith("poc_") || it.groupValues[1].startsWith("settings_time_travel") || it.groupValues[1].startsWith("settings_block_test") }
            .filter { banned.containsMatchIn(it.groupValues[2]) }
            .map { it.groupValues[1] }
            .toList()
        assertTrue("Strings with dropped words: $offenders", offenders.isEmpty())
    }
}
