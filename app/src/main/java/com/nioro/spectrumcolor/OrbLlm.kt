package com.nioro.spectrumcolor

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/** IA de texto 100 % local (solo desarrollador): descarga una vez Qwen2.5-1.5B y luego genera esferas sin internet. */
object OrbLlm {
    const val URL = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task"
    const val MIN_BYTES = 1_500_000_000L

    /** 0 libre, 1 descargando, 2 cargando el modelo, 3 pensando */
    @Volatile var status = 0
    @Volatile var progress = 0f
    @Volatile var note = ""
    @Volatile var result: Orb? = null
    @Volatile var noteFresh = false

    fun file(ctx: Context): File = File(File(ctx.filesDir, "llm"), "qwen2.5-1.5b-q8.task")
    fun ready(ctx: Context): Boolean = file(ctx).let { it.exists() && it.length() > MIN_BYTES }

    private fun say(s: String) { note = s; noteFresh = true }

    fun download(ctx: Context) {
        if (status != 0) return
        status = 1
        progress = 0f
        Thread {
            try {
                val dest = file(ctx)
                dest.parentFile?.mkdirs()
                File(dest.parentFile, "qwen2.5-0.5b-q8.task").delete()
                val part = File(dest.path + ".part")
                var have = if (part.exists()) part.length() else 0L
                val con = URL(URL).openConnection() as HttpURLConnection
                con.connectTimeout = 20000
                con.readTimeout = 30000
                con.instanceFollowRedirects = true
                if (have > 0) con.setRequestProperty("Range", "bytes=$have-")
                val code = con.responseCode
                if (code != 200 && code != 206) throw IllegalStateException("el servidor respondió $code")
                if (code == 200) have = 0L
                val total = have + con.contentLengthLong
                RandomAccessFile(part, "rw").use { raf ->
                    if (code == 200) raf.setLength(0)
                    raf.seek(have)
                    con.inputStream.use { ins ->
                        val buf = ByteArray(1 shl 16)
                        var done = have
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            raf.write(buf, 0, n)
                            done += n
                            if (total > 0) progress = done.toFloat() / total
                        }
                    }
                }
                if (part.length() < MIN_BYTES) throw IllegalStateException("descarga incompleta, vuelve a intentar")
                dest.delete()
                part.renameTo(dest)
                say("Modelo listo (${dest.length() / 1000000} MB)")
            } catch (e: Exception) {
                say("Falló la descarga: " + (e.message ?: e.javaClass.simpleName))
            }
            status = 0
        }.start()
    }

    fun generate(ctx: Context, text: String) {
        if (status != 0) return
        status = 2
        val app = ctx.applicationContext
        Thread {
            try {
                LlmBackend.load(app, file(app).path)
                status = 3
                val reply = LlmBackend.run(OrbText.prompt(text))
                val (orb, fromModel) = OrbText.finish(reply, text)
                result = orb
                say(if (fromModel) "La IA creó tu esfera" else "La IA respondió raro: usé reglas simples")
            } catch (e: Throwable) {
                result = OrbText.rules(text)
                say("IA no disponible (" + (e.message ?: e.javaClass.simpleName).take(60) + "): usé reglas simples")
            }
            status = 0
        }.start()
    }
}
