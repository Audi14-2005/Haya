

package com.monicauditya.redhat1406

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RedHat1406Test {
    private val modelPath = "/data/local/tmp/redhat1406-360m-instruct-q8_0.gguf"
    private val minP = 0.05f
    private val temperature = 1.0f
    private val systemPrompt = "You are a helpful assistant"
    private val query = "How are you?"
    private val chatTemplate =
        "{% set loop_messages = messages %}{% for message in loop_messages %}{% set content = '<|start_header_id|>'  + message['role'] + '<|end_header_id|>\n\n'+ message['content'] | trim + '<|eot_id|>' %}{% if loop.index0 == 0 %}{% set content = bos_token + content %}{% endif %}{{ content }}{% endfor %}{{ '<|start_header_id|>assistant<|end_header_id|>\n\n' }}"
    private val redHat1406 = RedHat1406()

    private val BENCH_PROMPT_PROCESSING_TOKENS = 512
    private val BENCH_TOKEN_GENERATION_TOKENS = 128
    private val BENCH_SEQUENCE = 1
    private val BENCH_REPETITION = 3

    @Before
    fun setup() =
        runTest {
            redHat1406.load(
                modelPath,
                RedHat1406.InferenceParams(
                    minP,
                    temperature,
                    storeChats = true,
                    contextSize = 0,
                    chatTemplate,
                    numThreads = 4,
                    useMmap = true,
                    useMlock = false,
                ),
            )
            redHat1406.addSystemPrompt(systemPrompt)
        }

    @Test
    fun getResponse_AsFlow_works() =
        runTest {
            val responseFlow = redHat1406.getResponseAsFlow(query)
            val responseTokens = responseFlow.toList()
            assert(responseTokens.isNotEmpty())
        }

    @Test
    fun getResponseAsFlowGenerationSpeed_works() =
        runTest {
            val speedBeforePrediction = redHat1406.getResponseGenerationSpeed().toInt()
            redHat1406.getResponseAsFlow(query).toList()
            val speedAfterPrediction = redHat1406.getResponseGenerationSpeed().toInt()
            assert(speedBeforePrediction == 0)
            assert(speedAfterPrediction > 0)
        }

    @Test
    fun getContextSize_works() =
        runTest {
            val ggufReader = GGUFReader()
            ggufReader.load(modelPath)
            val contextSize = ggufReader.getContextSize()
            assert(contextSize == 8192L)
        }

    @Test
    fun benchmarkModel_works() =
        runTest {
            val result = redHat1406.benchModel(
                BENCH_PROMPT_PROCESSING_TOKENS,
                BENCH_TOKEN_GENERATION_TOKENS,
                BENCH_SEQUENCE,
                BENCH_REPETITION,
            )
            println(result)
            assert(result.trim().isNotEmpty())
        }

    @After
    fun close() {
        redHat1406.close()
    }
}
