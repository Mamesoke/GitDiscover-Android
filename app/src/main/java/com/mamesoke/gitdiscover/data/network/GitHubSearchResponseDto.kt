package com.mamesoke.gitdiscover.data.network

import com.mamesoke.gitdiscover.domain.model.Repository
import com.mamesoke.gitdiscover.domain.repository.RepositorySearchException
import com.mamesoke.gitdiscover.domain.repository.SearchError
import java.net.URI
import java.net.URISyntaxException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GitHubSearchResponseDto(val items: List<RepositoryDto>)

@Serializable
internal data class OwnerDto(val login: String)

@Serializable
internal data class RepositoryDto(
    val id: Long,
    val name: String,
    @SerialName("full_name") val fullName: String,
    val owner: OwnerDto,
    val description: String? = null,
    val language: String? = null,
    @SerialName("stargazers_count") val stars: Long,
    @SerialName("forks_count") val forks: Long,
    @SerialName("html_url") val htmlUrl: String
) {
    fun toDomain(): Repository {
        val uri = try {
            URI(htmlUrl)
        } catch (error: URISyntaxException) {
            throw RepositorySearchException(SearchError.InvalidResponse, cause = error)
        }
        if (uri.scheme != "https" || uri.host != "github.com" || uri.userInfo != null) {
            throw RepositorySearchException(SearchError.InvalidResponse)
        }
        return Repository(id, name, fullName, owner.login, description, language, stars, forks, htmlUrl)
    }
}
