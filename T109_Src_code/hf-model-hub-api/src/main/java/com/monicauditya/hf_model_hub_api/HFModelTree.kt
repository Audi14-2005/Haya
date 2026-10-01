

package com.monicauditya.hf_model_hub_api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import kotlinx.serialization.Serializable

class HFModelTree(private val client: HttpClient) {
    @Serializable
    data class HFModelFile(val type: String, val oid: String, val size: Long, val path: String)

    suspend fun getModelFileTree(modelId: String): List<HFModelFile> {
        val response = client.get(urlString = HFEndpoints.getHFModelTreeEndpoint(modelId))
        if (response.status.value != 200) {
            throw Exception("Invalid model ID")
        }
        return response.body()
    }
}
