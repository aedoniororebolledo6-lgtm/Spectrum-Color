package com.nioro.spectrumcolor

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/** IA de texto 100 % local (solo desarrollador): se descarga una vez el modelo y luego dibuja esferas sin internet. */
object OrbLlm {
    class Model(val name: String, val url: String, val file: String, val minBytes: Long, val gated: Boolean, val tpl: Int, val maxTokens: Int, val sizeLabel: String)

    val models = arrayOf(
        Model("Qwen 1.5B", "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task",
            "qwen2.5-1.5b-q8.task", 1_500_000_000L, false, 0, 1280, "~1,6 GB"),
        Model("Gemma 3n", "https://huggingface.co/google/gemma-3n-E2B-it-litert-preview/resolve/main/gemma-3n-E2B-it-int4.task",
            "gemma-3n-e2b-int4.task", 2_800_000_000L, true, 1, 2048, "~3,1 GB")
    )

    /** 0 libre, 1 descargando, 2 cargando el modelo, 3 pensando */
    @Volatile var status = 0
    @Volatile var progress = 0f
    @Volatile var note = ""
    @Volatile var result: Orb? = null
    @Volatile var noteFresh = false

    fun file(ctx: Context, m: Model): File = File(File(ctx.filesDir, "llm"), m.file)
    fun ready(ctx: Context, m: Model): Boolean = file(ctx, m).let { it.exists() && it.length() > m.minBytes }

    private fun say(s: String) { note = s; noteFresh = true }

    /** Abre la descarga siguiendo las redirecciones a mano: el token solo va al primer servidor, nunca al CDN. */
    private fun open(url: String, token: String, have: Long): HttpURLConnection {
        var u = url
        var tk = token
        for (hop in 0 until 6) {
            val con = URL(u).openConnection() as HttpURLConnection
            con.connectTimeout = 20000
            con.readTimeout = 30000
            con.instanceFollowRedirects = false
            if (tk.isNotEmpty()) con.setRequestProperty("Authorization", "Bearer $tk")
            if (have > 0) con.setRequestProperty("Range", "bytes=$have-")
            val code = con.responseCode
            if (code in 300..399) {
                val loc = con.getHeaderField("Location") ?: throw IllegalStateException("redirección sin destino")
                u = URL(URL(u), loc).toString()
                tk = ""
                con.disconnect()
                continue
            }
            if (code == 401 || code == 403) throw IllegalStateException("sin permiso ($code): revisa el token y acepta la licencia del modelo en Hugging Face")
            return con
        }
        throw IllegalStateException("demasiadas redirecciones")
    }

    fun download(ctx: Context, m: Model, token: String) {
        if (status != 0) return
        status = 1
        progress = 0f
        Thread {
            try {
                val dest = file(ctx, m)
                dest.parentFile?.mkdirs()
                for (o in models) if (o !== m) File(dest.parentFile, o.file).delete()
                File(dest.parentFile, "qwen2.5-0.5b-q8.task").delete()
                val part = File(dest.path + ".part")
                var have = if (part.exists()) part.length() else 0L
                val con = open(m.url, token, have)
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
                if (part.length() < m.minBytes) throw IllegalStateException("descarga incompleta, vuelve a intentar")
                dest.delete()
                part.renameTo(dest)
                say("Modelo listo (${dest.length() / 1000000} MB)")
            } catch (e: Exception) {
                say("Falló la descarga: " + (e.message ?: e.javaClass.simpleName))
            }
            status = 0
        }.start()
    }

    fun generate(ctx: Context, m: Model, text: String) {
        if (status != 0) return
        status = 2
        val app = ctx.applicationContext
        Thread {
            try {
                LlmBackend.load(app, file(app, m).path, m.maxTokens)
                status = 3
                val rich = m.tpl == 1
                val reply = LlmBackend.run(OrbDraw.prompt(text, m.tpl, rich))
                val art = OrbDraw.parse(reply)
                if (art != null) {
                    result = Orb(5, 0, 0, 3, 5, 3, 3, 0, art)
                    say("La IA dibujó tu esfera (" + (art.count { it == ';' } + 1) + " formas)")
                } else {
                    // el dibujo no se entendió: se prueba con los rasgos de las esferas conocidas
                    val reply2 = LlmBackend.run(OrbText.prompt(text, m.tpl, rich))
                    val (orb, fromModel) = OrbText.finish(reply2, text)
                    result = orb
                    say(if (fromModel) "El dibujo falló: usé las esferas conocidas" else "La IA respondió raro: usé reglas simples")
                }
            } catch (e: Throwable) {
                result = OrbText.rules(text)
                say("IA no disponible (" + (e.message ?: e.javaClass.simpleName).take(70) + "): usé reglas simples")
            }
            status = 0
        }.start()
    }
}
