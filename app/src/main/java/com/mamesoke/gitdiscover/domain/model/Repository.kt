package com.mamesoke.gitdiscover.domain.model

data class Repository(
    val id: Long,
    val name: String,
    val fullName: String,
    val ownerLogin: String,
    val description: String?,
    val language: String?,
    val stars: Long,
    val forks: Long,
    val htmlUrl: String
)
