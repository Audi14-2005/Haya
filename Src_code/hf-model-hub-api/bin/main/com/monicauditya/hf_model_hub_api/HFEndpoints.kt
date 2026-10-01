package com.monicauditya.hf_model_hub_api

class HFEndpoints {
    companion object {
        private const val HF_BASE_URL = "https://huggingface.co"
        private const val HF_BASE_ENDPOINT = "${HF_BASE_URL}/api/models"

        val getHFBaseURL: (() -> String) = { HF_BASE_URL }

        val getHFModelsListEndpoint: (() -> String) = { HF_BASE_ENDPOINT }

        val getHFModelTreeEndpoint: ((String) -> String) = { "$HF_BASE_ENDPOINT/$it/tree/main" }

        val getHFModelSpecsEndpoint: ((String) -> String) = { "$HF_BASE_ENDPOINT/$it" }
    }
}
