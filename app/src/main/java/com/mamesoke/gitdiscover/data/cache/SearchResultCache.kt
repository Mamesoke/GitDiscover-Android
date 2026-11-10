package com.mamesoke.gitdiscover.data.cache

import com.mamesoke.gitdiscover.domain.model.Repository
import com.mamesoke.gitdiscover.domain.time.TimeProvider
import java.util.Locale
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal fun cacheKey(query: String): String {
    val result = StringBuilder()
    var quoted = false
    var escaped = false
    var pendingSpace = false
    for (character in query.trim()) {
        if (!quoted && character.isWhitespace()) {
            pendingSpace = true
            escaped = false
            continue
        }
        if (pendingSpace && result.isNotEmpty()) result.append(' ')
        pendingSpace = false
        result.append(character)
        if (character == '"' && !escaped) quoted = !quoted
        escaped = character == '\\' && !escaped
    }
    return result.toString().lowercase(Locale.ROOT)
}

class SearchResultCache(
    private val time: TimeProvider,
    private val ttlMillis: Long = 180_000
) {
    private data class Entry(val results: List<Repository>, val expiresAt: Long)
    private val entries = mutableMapOf<String, Entry>()
    private val mutex = Mutex()

    suspend fun get(query: String): List<Repository>? = mutex.withLock {
        currentCoroutineContext().ensureActive()
        val key = cacheKey(query)
        val entry = entries[key] ?: return@withLock null
        val now = time.nowMillis()
        currentCoroutineContext().ensureActive()
        if (now >= entry.expiresAt) {
            entries.remove(key)
            null
        } else {
            entry.results
        }
    }

    suspend fun put(query: String, results: List<Repository>) = mutex.withLock {
        val expiresAt = time.nowMillis() + ttlMillis
        currentCoroutineContext().ensureActive()
        entries[cacheKey(query)] = Entry(results.toList(), expiresAt)
    }
}
