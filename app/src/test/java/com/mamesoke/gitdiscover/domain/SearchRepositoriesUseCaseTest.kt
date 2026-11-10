package com.mamesoke.gitdiscover.domain

import com.mamesoke.gitdiscover.expectFailure
import com.mamesoke.gitdiscover.queryRules
import com.mamesoke.gitdiscover.repository
import com.mamesoke.gitdiscover.domain.repository.RepositorySearchException
import com.mamesoke.gitdiscover.domain.repository.RepositorySearching
import com.mamesoke.gitdiscover.domain.repository.SearchError
import com.mamesoke.gitdiscover.domain.usecase.SearchRepositoriesUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class SearchRepositoriesUseCaseTest {
    @Test fun trimsOnlySurroundingsAndReturnsAllFields() = runTest {
        val expected = listOf(repository(9_000_000_000))
        var received = ""
        val useCase = SearchRepositoriesUseCase(RepositorySearching {
            received = it
            expected
        }, queryRules())
        assertEquals(expected, useCase("\u00a0 C++  language:C++ \n"))
        assertEquals("C++  language:C++", received)
    }

    @Test fun invalidGraphemesNeverCallRepository() = runTest {
        val useCase = SearchRepositoriesUseCase(RepositorySearching {
            fail("Invalid query reached repository")
            emptyList()
        }, queryRules())
        listOf("", " ", "\u00a0\n", "a", "😀", "👍🏽", "👨‍👩‍👧‍👦", "e\u0301", "🇪🇸")
            .forEach { assertEquals("query: $it", emptyList<Any>(), useCase(it)) }
    }

    @Test fun twoGraphemesAreValid() {
        listOf("ab", "😀a", "👍🏽x", "👨‍👩‍👧‍👦x", "e\u0301x", "🇪🇸🇫🇷")
            .forEach { assertTrue(it, queryRules().isValid(it)) }
    }

    @Test fun extendedEmojiSequencesCannotBypassMinimumLength() = runTest {
        val rules = queryRules()
        val useCase = SearchRepositoriesUseCase(RepositorySearching {
            fail("A single grapheme reached the repository")
            emptyList()
        }, rules)
        listOf("👍🏽", "👨‍👩‍👧‍👦", "👩🏽‍💻", "🏳️‍🌈", "1️⃣", "🇪🇸", "e\u0301")
            .forEach {
                assertTrue(it, useCase(it).isEmpty())
                assertTrue(it, rules.isValid(it + "x"))
                assertTrue(it, rules.isValid(it + it))
            }
    }

    @Test fun typedErrorsAndCancellationPropagate() = runTest {
        val failure = RepositorySearchException(SearchError.Server)
        assertSame(failure, expectFailure<RepositorySearchException> {
            SearchRepositoriesUseCase(RepositorySearching { throw failure }, queryRules())("valid")
        })
        expectFailure<CancellationException> {
            SearchRepositoriesUseCase(RepositorySearching { throw CancellationException() }, queryRules())("valid")
        }
    }
}
