package com.nioro.spectrumcolor

import android.content.Context

/** En la versión pública no hay modelo de lenguaje. */
object LlmBackend {
    const val AVAILABLE = false
    fun load(ctx: Context, path: String, maxTokens: Int) { throw UnsupportedOperationException() }
    fun run(prompt: String): String = throw UnsupportedOperationException()
    fun close() {}
}
