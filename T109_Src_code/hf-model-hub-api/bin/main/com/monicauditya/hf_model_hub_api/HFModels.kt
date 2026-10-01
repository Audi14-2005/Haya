

package com.monicauditya.hf_model_hub_api

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class HFModels {
    companion object {
        private val client: HttpClient =
            HttpClient(OkHttp) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }

        fun getInfo(): HFModelInfo = HFModelInfo(client)

        fun getTree(): HFModelTree = HFModelTree(client)

        fun getSearch(): HFModelSearch = HFModelSearch(client)
    }
}
