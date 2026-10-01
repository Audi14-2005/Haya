package com.monicauditya.june.modelhub

import com.monicauditya.hf_model_hub_api.HFEndpoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koin.core.annotation.Single
import java.net.URLEncoder
import java.util.Locale

@Serializable
data class HuggingFaceFile(
    val name: String,
    val sizeBytes: Long,
)

@Serializable
data class HuggingFaceModel(
    val id: String,
    val name: String,
    val description: String,
    val downloads: Long,
    val files: List<HuggingFaceFile>,
)

@Single
class HuggingFaceService {
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheMutex = Mutex()
    private val excludedTokens = listOf("mmproj", "vision", "clip", "encoder", "embedding")
    private val preferredQuantTokens = listOf("q2", "q3", "q4", "q5", "q6", "q8")

    @Volatile
    private var cachedModelsByQuery: Map<String, List<HuggingFaceModel>> = emptyMap()

    suspend fun searchModels(query: String = "gguf"): List<HuggingFaceModel> {
        cachedModelsByQuery[query]?.let { return it }

        return cacheMutex.withLock {
            cachedModelsByQuery[query]?.let { return@withLock it }
            val freshModels = fetchModels(query)
            cachedModelsByQuery = cachedModelsByQuery + (query to freshModels)
            freshModels
        }
    }

    fun toModelInfo(model: HuggingFaceModel): List<ModelInfo> {
        return mapRepoFilesToModelInfo(
            modelId = model.id,
            repoName = model.name,
            description = model.description,
            capabilityTags = emptyList(),
            pipelineTag = null,
            downloads = model.downloads,
            files = model.files
        )
    }

    fun mapRepoFilesToModelInfo(
        modelId: String,
        repoName: String,
        description: String,
        capabilityTags: List<String> = emptyList(),
        pipelineTag: String? = null,
        downloads: Long,
        files: List<HuggingFaceFile>,
        lastUpdatedEpochMs: Long? = null,
    ): List<ModelInfo> {
        return files
            .filter { file ->
                isRunnableModelFile(file.name) && file.sizeBytes >= MIN_MODEL_BYTES
            }
            .map { file ->
            val sizeGb = file.sizeBytes / (1024f * 1024f * 1024f)
            val quantization = extractQuantization(file.name)
            ModelInfo(
                name = cleanVariantName(repoName, file.name, quantization),
                groupName = cleanGroupName(repoName, file.name),
                description = description,
                capabilityTags = capabilityTags,
                pipelineTag = pipelineTag,
                sizeGb = sizeGb,
                ramRequiredMb = (sizeGb * 1024f * 1.2f).toInt().coerceAtLeast(1024),
                quantization = quantization,
                supportsImage = false,
                quality = inferQualityLevel(sizeGb),
                downloadUrl = HFEndpoints.getHFModelResolveEndpoint(
                    modelId,
                    encodePathSegment(file.name)
                ),
                fileName = file.name,
                downloads = downloads,
                expectedSizeBytes = file.sizeBytes,
                lastUpdatedEpochMs = lastUpdatedEpochMs,
            )
        }
    }

    private suspend fun fetchModels(query: String): List<HuggingFaceModel> = withContext(Dispatchers.IO) {
        val mergedItems = fetchSearchItems(query)
            .filter(::isUsefulRepo)

        coroutineScope {
            mergedItems.map { item ->
                async {
                    val filesDeferred = async { fetchModelFiles(item.id) }
                    val readmeDeferred = async { fetchReadmeSummary(item.id) }
                    val files = filesDeferred.await()
                    if (files.isEmpty()) {
                        null
                    } else {
                        HuggingFaceModel(
                            id = item.id,
                            name = item.id.substringAfterLast('/'),
                            description = buildDescription(item, readmeDeferred.await()),
                            downloads = item.downloads,
                            files = files
                        )
                    }
                }
            }.awaitAll()
                .filterNotNull()
                .sortedByDescending { it.downloads }
                .take(40)
        }
    }

    private suspend fun fetchSearchItems(query: String): List<HfSearchItem> = withContext(Dispatchers.IO) {
        val collected = mutableListOf<HfSearchItem>()
        var nextUrl: String? = buildSearchUrl(query)
        var pageCount = 0

        while (nextUrl != null && pageCount < 3) {
            val request = Request.Builder()
                .url(nextUrl)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    nextUrl = null
                    return@use
                }

                val body = response.body?.string()
                if (body == null) {
                    nextUrl = null
                    return@use
                }
                collected += json.decodeFromString<List<HfSearchItem>>(body)
                nextUrl = parseNextLink(response.header("Link"))
                pageCount += 1
            }
        }

        collected
            .associateBy { it.id }
            .values
            .toList()
    }

    private fun buildSearchUrl(query: String): String {
        val normalized = query.trim()
        val base = StringBuilder(HFEndpoints.getHFModelsListEndpoint())
            .append("?filter=gguf&limit=40&full=true&config=true&sort=downloads&direction=-1")
        if (normalized.isNotBlank()) {
            base.append("&search=").append(encodePathSegment(normalized))
        }
        return base.toString()
    }

    private suspend fun fetchModelFiles(modelId: String): List<HuggingFaceFile> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(HFEndpoints.getHFModelTreeEndpoint(modelId))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return@withContext emptyList()
            }

            val body = response.body?.string() ?: return@withContext emptyList()
            json.decodeFromString<List<HfTreeEntry>>(body)
                .filter { entry ->
                    entry.type == "file" &&
                        entry.path.lowercase(Locale.US).endsWith(".gguf") &&
                        isRunnableModelFile(entry.path) &&
                        entry.size >= MIN_MODEL_BYTES
                }
                .sortedWith(
                    compareByDescending<HfTreeEntry> { preferredFileScore(it.path) }
                        .thenBy { it.size }
                )
                .map { entry ->
                    HuggingFaceFile(
                        name = entry.path.substringAfterLast('/'),
                        sizeBytes = entry.size
                    )
                }
                .take(10)
        }
    }

    private suspend fun fetchReadmeSummary(modelId: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(HFEndpoints.getHFModelReadmeEndpoint(modelId))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext null
            val markdown = response.body?.string() ?: return@withContext null
            extractReadmeSummary(markdown)
        }
    }

    private fun buildDescription(item: HfSearchItem, readmeSummary: String?): String {
        if (!readmeSummary.isNullOrBlank()) {
            return readmeSummary
        }

        val baseModel = item.cardData?.baseModel
        val tagSummary = item.cardData?.tags
            ?.filterNot { it.equals("gguf", ignoreCase = true) }
            ?.take(3)
            ?.joinToString(", ")

        return when {
            !baseModel.isNullOrBlank() && !tagSummary.isNullOrBlank() ->
                "Based on $baseModel. Tagged for $tagSummary."
            !baseModel.isNullOrBlank() ->
                "Based on $baseModel."
            !tagSummary.isNullOrBlank() ->
                "GGUF model tagged for $tagSummary."
            !item.author.isNullOrBlank() ->
                "GGUF model published by ${item.author}."
            else ->
                "GGUF model from Hugging Face."
        }
    }

    private fun extractReadmeSummary(markdown: String): String? {
        val cleaned = markdown
            .lineSequence()
            .dropWhile { it.trim().startsWith("---") || it.isBlank() }
            .filterNot { line ->
                val trimmed = line.trim()
                trimmed.startsWith("#") ||
                    trimmed.startsWith("![]") ||
                    trimmed.startsWith("<img") ||
                    trimmed.startsWith("base_model:") ||
                    trimmed.startsWith("license:")
            }
            .joinToString("\n")
            .trim()

        val paragraph = cleaned
            .split(Regex("\\n\\s*\\n"))
            .firstOrNull { chunk ->
                val normalized = chunk.trim()
                normalized.isNotBlank() && normalized.length > 40
            }
            ?.replace(Regex("\\[(.*?)\\]\\((.*?)\\)"), "$1")
            ?.replace(Regex("`{1,3}"), "")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()

        return paragraph?.take(180)
    }

    private fun extractQuantization(fileName: String): String {
        val match = Regex("(Q\\d(?:_[A-Z0-9]+)+|Q\\d_\\d|BF16|F16)", RegexOption.IGNORE_CASE)
            .find(fileName)
            ?.value
            ?: return "Unknown"
        return match.uppercase(Locale.US)
    }

    private fun inferQualityLevel(sizeGb: Float): QualityLevel = when {
        sizeGb >= 4f -> QualityLevel.HIGH
        sizeGb >= 1.5f -> QualityLevel.BALANCED
        else -> QualityLevel.LOW
    }

    private fun isRunnableModelFile(path: String): Boolean {
        val normalized = path.lowercase(Locale.US)
        if (excludedTokens.any { normalized.contains(it) }) return false
        return preferredQuantTokens.any { normalized.contains(it) } || normalized.contains("bf16")
    }

    private fun isUsefulRepo(item: HfSearchItem): Boolean {
        val id = item.id.lowercase(Locale.US)
        val tags = (item.tags + item.cardData?.tags.orEmpty())
            .map { it.lowercase(Locale.US) }
        val pipeline = item.pipelineTag?.lowercase(Locale.US).orEmpty()

        val hasIntentSignal =
            id.contains("instruct") ||
                id.contains("chat") ||
                id.contains("coder") ||
                id.contains("code") ||
                tags.any {
                    it.contains("instruct") ||
                        it.contains("chat") ||
                        it.contains("conversational") ||
                        it.contains("assistant") ||
                        it.contains("coder") ||
                        it.contains("code")
                } ||
                pipeline == "text-generation"

        return hasIntentSignal
    }

    private fun preferredFileScore(path: String): Int {
        val normalized = path.lowercase(Locale.US)
        var score = 0
        if ("instruct" in normalized) score += 4
        if ("chat" in normalized) score += 4
        if ("coder" in normalized || "code" in normalized) score += 3
        if ("q4" in normalized) score += 3
        if ("q5" in normalized) score += 2
        if ("q3" in normalized) score += 2
        if ("q2" in normalized) score += 1
        if ("q8" in normalized) score += 1
        if ("q4_k_m" in normalized) score += 2
        return score
    }

    private fun cleanVariantName(repoName: String, fileName: String, quantization: String): String {
        return "${cleanGroupName(repoName, fileName)} (${quantization.substringBefore('_')})"
    }

    private fun cleanGroupName(repoName: String, fileName: String): String {
        val source = fileName.removeSuffix(".gguf")
            .replace('_', ' ')
            .replace('-', ' ')
        val family = when {
            source.contains("qwen", ignoreCase = true) -> "Qwen"
            source.contains("llama", ignoreCase = true) -> "LLaMA"
            source.contains("mistral", ignoreCase = true) -> "Mistral"
            source.contains("gemma", ignoreCase = true) -> "Gemma"
            source.contains("phi", ignoreCase = true) -> "Phi"
            else -> repoName.substringAfterLast('/').replace('-', ' ')
        }
        val version = Regex("""(\d+(?:\.\d+)?)""").find(source)?.value
        val size = Regex("""(\d+(?:\.\d+)?B)""", RegexOption.IGNORE_CASE).find(source)?.value?.uppercase(Locale.US)

        return buildList {
            add(family)
            version?.takeIf { family == "Qwen" || family == "Phi" }?.let { add(it) }
            size?.let { add(it) }
        }.joinToString(" ")
    }

    private fun encodePathSegment(value: String): String {
        return URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
    }

    private fun parseNextLink(linkHeader: String?): String? {
        if (linkHeader.isNullOrBlank()) return null
        val regex = Regex("""<([^>]+)>;\s*rel="next"""")
        return regex.find(linkHeader)?.groupValues?.getOrNull(1)
    }

    private companion object {
        const val MIN_MODEL_BYTES = 512L * 1024L * 1024L
    }

    @Serializable
    private data class HfSearchItem(
        val id: String,
        val author: String? = null,
        @SerialName("downloads") val downloads: Long = 0,
        val tags: List<String> = emptyList(),
        @SerialName("pipeline_tag") val pipelineTag: String? = null,
        val siblings: List<HfSibling> = emptyList(),
        val cardData: HfCardData? = null,
    )

    @Serializable
    private data class HfSibling(
        val rfilename: String,
    )

    @Serializable
    private data class HfCardData(
        @SerialName("base_model") val baseModel: String? = null,
        val tags: List<String> = emptyList(),
    )

    @Serializable
    private data class HfTreeEntry(
        val type: String,
        val size: Long = 0,
        val path: String,
    )
}
