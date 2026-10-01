
package com.monicauditya.hf_model_hub_api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

class HFModelSearch(private val client: HttpClient) {
    @Serializable
    data class ModelSearchResult(
        val _id: String,
        val id: String,
        @SerialName("likes") val numLikes: Int,
        @SerialName("downloads") val numDownloads: Int,
        @SerialName("private") val isPrivate: Boolean,
        val tags: List<String>,
        @SerialName("pipeline_tag") val pipelineTag: String? = null,
        @Serializable(with = CustomDateSerializer::class) val createdAt: LocalDateTime,
        val modelId: String,
    )

    enum class ModelSortParam(val value: String) {
        NONE(""),
        DOWNLOADS("downloads"),
        AUTHOR("author"),
        CREATED_AT("createdAt"),
    }

    enum class ModelSearchDirection(val value: Int) {
        ASCENDING(1),
        DESCENDING(-1),
    }

    suspend fun searchModels(
        query: String,
        author: String,
        filter: String,
        sort: ModelSortParam = ModelSortParam.DOWNLOADS,
        direction: ModelSearchDirection = ModelSearchDirection.DESCENDING,
        limit: Int,
        full: Boolean = true,
        config: Boolean = true,
    ): List<ModelSearchResult> {
        val response = client.get(HFEndpoints.getHFModelsListEndpoint()) {
            url {
                parameters.append("search", query)
                if (author.isNotBlank()) {
                    parameters.append("author", author)
                }
                parameters.append("filter", filter)
                parameters.append("sort", sort.value)
                parameters.append("direction", direction.value.toString())
                parameters.append("limit", limit.toString())
                parameters.append("full", full.toString())
                parameters.append("config", config.toString())
            }
        }
        return response.body()
    }
}
