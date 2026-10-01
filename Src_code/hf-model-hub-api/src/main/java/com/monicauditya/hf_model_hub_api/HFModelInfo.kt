

package com.monicauditya.hf_model_hub_api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

class HFModelInfo(private val client: HttpClient) {
    @Serializable
    data class ModelInfo(
        val _id: String,
        val id: String,
        val modelId: String,
        val author: String,
        val private: Boolean,
        val disabled: Boolean,
        val tags: List<String>,
        @SerialName(value = "downloads") val numDownloads: Long,
        @SerialName(value = "likes") val numLikes: Long,
        @Serializable(with = CustomDateSerializer::class) val lastModified: LocalDateTime,
        @Serializable(with = CustomDateSerializer::class) val createdAt: LocalDateTime,
    )

    suspend fun getModelInfo(modelId: String): ModelInfo {
        val response = client.get(urlString = HFEndpoints.getHFModelSpecsEndpoint(modelId))
        if (response.status.value != 200) {
            throw Exception("Invalid model ID")
        }
        return response.body()
    }
}
