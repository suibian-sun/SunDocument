package com.suibian.sunfile

import com.suibian.sunfile.data.FileRepo
import com.suibian.sunfile.data.FileRepo.MatchMode
import com.suibian.sunfile.data.FileRepo.SearchOptions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 搜索面板的匹配规则：通配符、包含、正则、大小写 */
class SearchMatchTest {

    private fun accept(query: String, name: String, options: SearchOptions = SearchOptions()): Boolean =
        FileRepo.matcherOf(query, options)(name)

    @Test
    fun globMatchesWildcard() {
        val o = SearchOptions(match = MatchMode.GLOB)
        assertTrue(accept("*.kt", "MainScreen.kt", o))
        assertFalse(accept("*.kt", "MainScreen.java", o))
        assertTrue(accept("Main?creen.kt", "MainScreen.kt", o))
    }

    @Test
    fun globWithoutWildcardBehavesLikeContains() {
        assertTrue(accept("screen", "MainScreen.kt"))
        assertFalse(accept("screen", "Main.kt"))
    }

    @Test
    fun containsIsCaseInsensitiveByDefault() {
        val o = SearchOptions(match = MatchMode.CONTAINS)
        assertTrue(accept("main", "MainScreen.kt", o))
        assertFalse(accept("MAIN", "MainScreen.kt", o.copy(caseSensitive = true)))
    }

    @Test
    fun regexMatches() {
        val o = SearchOptions(match = MatchMode.REGEX)
        assertTrue(accept("^Main.*\\.kt$", "MainScreen.kt", o))
        assertFalse(accept("^Main.*\\.kt$", "ScreenMain.kt", o))
    }

    @Test
    fun invalidRegexMatchesNothing() {
        val o = SearchOptions(match = MatchMode.REGEX)
        assertFalse(accept("[unclosed", "anything.kt", o))
    }

    @Test
    fun sizeAndTimeFiltersAreReported() {
        assertFalse(SearchOptions().hasSizeFilter)
        assertTrue(SearchOptions(minSize = 10).hasSizeFilter)
        assertTrue(SearchOptions(maxSize = 100).hasSizeFilter)
        assertFalse(SearchOptions().hasTimeFilter)
        assertTrue(SearchOptions(since = 123L).hasTimeFilter)
    }
}
