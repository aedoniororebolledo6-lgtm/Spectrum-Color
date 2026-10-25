package com.nioro.spectrumcolor

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference

/** Motor del modelo de lenguaje local (MediaPipe LLM Inference). Solo existe en la versión de desarrollador. */
object LlmBackend {
    const val AVAILABLE = true
    private var llm: LlmInference? = null

    @Synchronized
    fun load(ctx: Context, path: String) {
        if (llm != null) return
        val o = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(path)
            .setMaxTokens(1024)
            .build()
        llm = LlmInference.createFromOptions(ctx, o)
    }

    @Synchronized
    fun run(prompt: String): String = (llm ?: throw IllegalStateException("modelo sin cargar")).generateResponse(prompt)

    @Synchronized
    fun close() {
        llm?.close()
        llm = null
    }
}
