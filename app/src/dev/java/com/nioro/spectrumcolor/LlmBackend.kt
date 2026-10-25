package com.nioro.spectrumcolor

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference

/** Motor del modelo de lenguaje local (MediaPipe LLM Inference). Solo existe en la versión de desarrollador. */
object LlmBackend {
    const val AVAILABLE = true
    private var llm: LlmInference? = null
    private var loadedPath = ""

    @Synchronized
    fun load(ctx: Context, path: String, maxTokens: Int) {
        if (llm != null && loadedPath == path) return
        llm?.close()
        llm = null
        val o = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(maxTokens)
            .build()
        llm = LlmInference.createFromOptions(ctx, o)
        loadedPath = path
    }

    @Synchronized
    fun run(prompt: String): String = (llm ?: throw IllegalStateException("modelo sin cargar")).generateResponse(prompt)

    @Synchronized
    fun close() {
        llm?.close()
        llm = null
        loadedPath = ""
    }
}
