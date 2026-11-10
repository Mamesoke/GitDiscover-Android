package com.mamesoke.gitdiscover.domain.time

fun interface TimeProvider {
    fun nowMillis(): Long
}
