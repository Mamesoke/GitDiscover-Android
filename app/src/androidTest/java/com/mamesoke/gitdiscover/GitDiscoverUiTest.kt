package com.mamesoke.gitdiscover

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mamesoke.gitdiscover.domain.search.QueryRules
import com.mamesoke.gitdiscover.testing.FixtureScenario
import com.mamesoke.gitdiscover.testing.SearchTestActivity
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GitDiscoverUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private var scenario: ActivityScenario<SearchTestActivity>? = null

    @After fun close() { scenario?.close() }

    private fun launch(fixture: FixtureScenario, missingBrowser: Boolean = false) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scenario = ActivityScenario.launch(
            Intent(context, SearchTestActivity::class.java)
                .putExtra("scenario", fixture.name).putExtra("missingBrowser", missingBrowser)
        )
    }

    private fun search() {
        compose.onNodeWithTag("search-field").performTextInput("kotlin")
    }

    private fun waitFor(tag: String) {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun openDetail() {
        waitFor("repository-1")
        compose.onNodeWithTag("repository-1").performClick()
        waitFor("owner-value")
    }

    @Test fun searchDetailBackAndClearPreserveStateAndAvoidDetailFetch() {
        launch(FixtureScenario.Results)
        compose.onNodeWithContentDescription("Clear search").assertIsNotEnabled()
        search()
        openDetail()
        compose.onNodeWithTag("owner-value").assertTextEquals("sample")
        compose.onNodeWithTag("language-value").assertTextEquals("Kotlin")
        compose.onNodeWithTag("stars-value").assertExists()
        compose.onNodeWithTag("forks-value").assertTextEquals("56")
        compose.onNodeWithText("Open on GitHub").performScrollTo().performClick()
        scenario!!.onActivity {
            assertEquals("https://github.com/sample/GitDiscover", it.lastOpenedUrl)
            assertEquals(1, it.searchCalls)
        }
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("repository-1")
        compose.onNodeWithTag("search-field").assertTextContains("kotlin")
        scenario!!.onActivity { assertEquals(1, it.searchCalls) }
        compose.onNodeWithContentDescription("Clear search").performClick()
        waitFor("idle-state")
        compose.onNodeWithTag("repository-1").assertDoesNotExist()
    }

    @Test fun nullFieldsHaveDetailFallbacks() {
        launch(FixtureScenario.MissingFields)
        search()
        openDetail()
        compose.onNodeWithText("No description provided").assertExists()
        compose.onNodeWithTag("language-value").assertTextEquals("Unknown language")
    }

    @Test fun emptyResponseShowsQueryAndNoRows() {
        launch(FixtureScenario.Empty)
        search()
        waitFor("empty-state")
        compose.onNodeWithText("No repositories found").assertExists()
        compose.onNodeWithTag("repository-1").assertDoesNotExist()
    }

    @Test fun connectivityFailureHasManualRetry() {
        launch(FixtureScenario.Offline)
        search()
        waitFor("error-state")
        compose.onNodeWithText("Unable to connect. Check your internet connection.").assertExists()
        compose.onNodeWithText("Retry").performScrollTo().assertIsDisplayed().assertIsEnabled().performClick()
        waitFor("repository-1")
        scenario!!.onActivity { assertEquals(2, it.searchCalls) }
    }

    @Test fun rateLimitDisablesImmediateRetry() {
        launch(FixtureScenario.RateLimit)
        search()
        waitFor("error-state")
        compose.onNodeWithText("Retry").assertIsNotEnabled()
        scenario!!.onActivity { assertEquals(1, it.searchCalls) }
    }

    @Test fun recreatePreservesResultsAndDetailWithoutDuplicateSearch() {
        launch(FixtureScenario.Results)
        search()
        waitFor("repository-1")
        scenario!!.recreate()
        waitFor("repository-1")
        scenario!!.onActivity { assertEquals(0, it.searchCalls) }
        openDetail()
        scenario!!.recreate()
        waitFor("owner-value")
        compose.onNodeWithTag("owner-value").assertTextEquals("sample")
        scenario!!.onActivity { assertEquals(0, it.searchCalls) }
        compose.onNodeWithContentDescription("Back").performClick()
        waitFor("repository-1")
    }

    @Test fun unavailableBrowserShowsVisibleError() {
        launch(FixtureScenario.Results, missingBrowser = true)
        search()
        openDetail()
        compose.onNodeWithText("Open on GitHub").performScrollTo().performClick()
        compose.onNodeWithText("No browser is available to open this link.").assertExists()
    }

    @Test fun platformGraphemePolicyMatchesDomainCorpus() {
        val rules = QueryRules()
        listOf("", " ", "a", "😀", "👍🏽", "👨‍👩‍👧‍👦", "e\u0301", "🇪🇸")
            .forEach { assertFalse(it, rules.isValid(it)) }
        listOf("ab", "😀a", "👍🏽x", "👨‍👩‍👧‍👦x", "e\u0301x", "🇪🇸🇫🇷")
            .forEach { assertTrue(it, rules.isValid(it)) }
    }

    @Test fun extendedGraphemeSequencesDoNotBypassMinimum() {
        val rules = QueryRules()
        listOf("👍🏽", "👨‍👩‍👧‍👦", "e\u0301", "🇪🇸", "👩🏽‍💻", "🏳️‍🌈", "1️⃣").forEach {
            assertFalse(it, rules.isValid(it))
            assertTrue(it, rules.isValid(it + "x"))
            assertTrue(it, rules.isValid(it + it))
        }
    }
}
