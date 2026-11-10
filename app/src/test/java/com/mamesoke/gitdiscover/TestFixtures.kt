package com.mamesoke.gitdiscover

import com.mamesoke.gitdiscover.domain.time.TimeProvider
import com.mamesoke.gitdiscover.domain.model.Repository
import com.mamesoke.gitdiscover.domain.search.QueryRules
import org.junit.Assert.assertTrue

fun repository(id: Long = 1) = Repository(
    id, "repo$id", "owner/repo$id", "owner", "Description", "Kotlin",
    1200, 30, "https://github.com/owner/repo$id"
)

fun queryRules() = QueryRules()

class FakeTime(var now: Long = 1_800_000_000_000) : TimeProvider {
    override fun nowMillis(): Long = now
}

suspend inline fun <reified T : Throwable> expectFailure(block: () -> Unit): T {
    try {
        block()
    } catch (error: Throwable) {
        assertTrue("Expected ${T::class}, got $error", error is T)
        return error as T
    }
    throw AssertionError("Expected ${T::class}")
}

const val RESPONSE = """{"items":[{"id":9000000000,"name":"repo","full_name":"owner/repo","owner":{"login":"owner"},"description":"Description","language":"Kotlin","stargazers_count":1234,"forks_count":12,"html_url":"https://github.com/owner/repo","ignored":true}],"total_count":1}"""
