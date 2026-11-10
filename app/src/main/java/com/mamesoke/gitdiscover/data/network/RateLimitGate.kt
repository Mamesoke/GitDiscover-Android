package com.mamesoke.gitdiscover.data.network

import com.mamesoke.gitdiscover.domain.time.TimeProvider
import com.mamesoke.gitdiscover.domain.repository.RepositorySearchException
import com.mamesoke.gitdiscover.domain.repository.SearchError
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Headers

class RateLimitGate(private val time: TimeProvider) {
    private val mutex = Mutex()
    private var deadline: Long? = null

    suspend fun check() = mutex.withLock {
        currentCoroutineContext().ensureActive()
        deadline?.let {
            if (time.nowMillis() < it) throw RepositorySearchException(SearchError.RateLimited, it)
        }
    }

    suspend fun record(headers: Headers): Long = mutex.withLock {
        currentCoroutineContext().ensureActive()
        val now = time.nowMillis()
        val retry = headers["Retry-After"]?.trim()?.let { value ->
            secondsMillis(value)?.let { delta -> safeAdd(now, delta) } ?: httpDate(value)
        }
        val reset = headers["X-RateLimit-Reset"]?.trim()?.let(::secondsMillis)
        val incoming = retry?.takeIf { it >= now }
            ?: reset?.takeIf { it >= now }
            ?: safeAdd(now, 60_000)
        maxOf(deadline ?: Long.MIN_VALUE, incoming).also { deadline = it }
    }

    private fun secondsMillis(value: String): Long? {
        val seconds = value.toDoubleOrNull() ?: return null
        val milliseconds = seconds * 1_000
        if (!milliseconds.isFinite() || milliseconds < 0 || milliseconds >= Long.MAX_VALUE.toDouble()) {
            return null
        }
        return ceil(milliseconds).toLong()
    }

    private fun safeAdd(now: Long, delta: Long): Long =
        if (now > Long.MAX_VALUE - delta) Long.MAX_VALUE else now + delta

    private fun httpDate(value: String): Long? {
        val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
            isLenient = false
        }
        val position = ParsePosition(0)
        val date = format.parse(value, position)
        return date?.time?.takeIf { position.index == value.length }
    }
}
