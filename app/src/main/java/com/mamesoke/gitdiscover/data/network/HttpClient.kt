package com.mamesoke.gitdiscover.data.network

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

data class HttpResponse(val status: Int, val headers: Headers, val body: String)

fun interface HttpClient {
    suspend fun execute(request: Request): HttpResponse
}

class OkHttpTransport(
    private val client: OkHttpClient,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : HttpClient {
    override suspend fun execute(request: Request): HttpResponse = withContext(dispatcher) {
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            val result = HttpResponse(
                                it.code,
                                it.headers,
                                it.body?.string().orEmpty()
                            )
                            continuation.resume(result)
                        }
                    } catch (e: IOException) {
                        continuation.resumeWithException(e)
                    }
                }
            })
        }
    }
}
