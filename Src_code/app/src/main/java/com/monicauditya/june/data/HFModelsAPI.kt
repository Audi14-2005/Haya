

package com.monicauditya.june.data

import com.monicauditya.june.modelhub.UserUseCase
import com.monicauditya.hf_model_hub_api.HFModelInfo
import com.monicauditya.hf_model_hub_api.HFModelSearch
import com.monicauditya.hf_model_hub_api.HFModelTree
import com.monicauditya.hf_model_hub_api.HFModels
import org.koin.core.annotation.Single

@Single
class HFModelsAPI {
    suspend fun getModelInfo(modelId: String): HFModelInfo.ModelInfo =
        HFModels.getInfo().getModelInfo(modelId)

    suspend fun getModelTree(modelId: String): List<HFModelTree.HFModelFile> =
        HFModels.getTree().getModelFileTree(modelId)

    suspend fun searchModels(
        query: String = "",
        limit: Int = 20,
        filter: String = "gguf,conversational",
        sort: HFModelSearch.ModelSortParam = HFModelSearch.ModelSortParam.DOWNLOADS,
    ): List<HFModelSearch.ModelSearchResult> =
        HFModels.getSearch()
            .searchModels(query, "", limit = limit, filter = filter, sort = sort)

    suspend fun getTopModels(
        query: String = "",
        limit: Int = 20,
        filter: String = "gguf,conversational",
        sort: HFModelSearch.ModelSortParam = HFModelSearch.ModelSortParam.DOWNLOADS,
    ): List<HFModelSearch.ModelSearchResult> =
        searchModels(query = query, limit = limit, filter = filter, sort = sort)

    suspend fun getRecommendationSeedModels(
        useCase: UserUseCase,
        limit: Int = 40,
    ): List<HFModelSearch.ModelSearchResult> {
        val feedLimit = (limit / 2).coerceAtLeast(12)
        val feeds = mutableListOf<Pair<Float, List<HFModelSearch.ModelSearchResult>>>()

        when (useCase) {
            UserUseCase.CODING -> {
                feeds += 1.00f to searchModels(query = "coder", limit = feedLimit, filter = "gguf")
                feeds += 0.90f to searchModels(query = "code", limit = (feedLimit / 2).coerceAtLeast(8), filter = "gguf")
                feeds += 0.20f to searchModels(query = "", limit = (feedLimit / 2).coerceAtLeast(8), filter = "gguf,conversational")
            }
            UserUseCase.GENERAL -> {
                feeds += 1.00f to searchModels(query = "instruct", limit = feedLimit, filter = "gguf")
                feeds += 0.90f to searchModels(query = "", limit = feedLimit, filter = "gguf,conversational")
                feeds += 0.25f to searchModels(query = "", limit = (feedLimit / 2).coerceAtLeast(8), filter = "gguf")
            }
            UserUseCase.MIXED -> {
                feeds += 0.95f to searchModels(query = "", limit = feedLimit, filter = "gguf,conversational")
                feeds += 0.85f to searchModels(query = "instruct", limit = (feedLimit / 2).coerceAtLeast(8), filter = "gguf")
                feeds += 0.85f to searchModels(query = "coder", limit = (feedLimit / 2).coerceAtLeast(8), filter = "gguf")
                feeds += 0.70f to searchModels(query = "", limit = feedLimit, filter = "gguf")
            }
        }

        return feeds
            .flatMap { (sourceBoost, items) ->
                items.map { item ->
                    val popularity = kotlin.math.log10(kotlin.math.max(item.numDownloads.toDouble(), 1.0)) / 6.0
                    val blendedScore = (sourceBoost * 0.65f) + (popularity.toFloat().coerceIn(0f, 1f) * 0.35f)
                    item to blendedScore
                }
            }
            .groupBy({ it.first.id }, { it })
            .map { (_, scoredItems) ->
                scoredItems.maxWithOrNull(
                    compareBy<Pair<HFModelSearch.ModelSearchResult, Float>> { it.second }
                        .thenBy { it.first.numDownloads }
                )!!
            }
            .sortedWith(
                compareByDescending<Pair<HFModelSearch.ModelSearchResult, Float>> { it.second }
                    .thenByDescending { it.first.numDownloads }
                    .thenBy { it.first.id }
            )
            .map { it.first }
            .take(limit)
    }

}
