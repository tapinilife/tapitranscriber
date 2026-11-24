package app.tapitranscriber

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ErroreServizio(messaggio: String, val codice: Int = 0) : Exception(messaggio)

data class Risultato(val testo: String, val servizio: String, val nota: String = "")

/**
 * Trascrive con Groq (Whisper large-v3-turbo); se Groq non va (limite raggiunto,
 * rete, file troppo grande) prova Gemini.
 */
class Trascrittore(private val pref: Preferenze) {

    fun trascrivi(audio: ByteArray, mime: String): Risultato {
        val groq = pref.chiaveGroq
        val gemini = pref.chiaveGemini
        if (groq.isEmpty() && gemini.isEmpty()) {
            throw ErroreServizio("Nessuna chiave API impostata. Apri l'app TapiTranscriber e inserisci la chiave Groq.")
        }
        var motivoGroq = ""
        if (groq.isNotEmpty()) {
            if (audio.size > LIMITE_GROQ) {
                motivoGroq = "file oltre 25 MB"
            } else {
                try {
                    return Risultato(groq(groq, audio, mime), "Groq")
                } catch (e: Exception) {
                    motivoGroq = spiega(e)
                }
            }
        }
        if (gemini.isEmpty()) {
            throw ErroreServizio("Groq: $motivoGroq.\nPer avere una riserva, imposta anche la chiave Gemini.")
        }
        if (audio.size > LIMITE_GEMINI) throw ErroreServizio("Audio troppo grande (oltre 60 MB).")
        try {
            val nota = if (motivoGroq.isNotEmpty()) "Groq non disponibile: $motivoGroq" else ""
            return Risultato(gemini(gemini, audio, mime), "Gemini", nota)
        } catch (e: Exception) {
            val prima = if (motivoGroq.isNotEmpty()) "Groq: $motivoGroq\n" else ""
            throw ErroreServizio("${prima}Gemini: ${spiega(e)}")
        }
    }

    // ---------- Groq ----------

    private fun groq(chiave: String, audio: ByteArray, mime: String): String {
        val confine = "----TapiTranscriber" + System.nanoTime()
        val corpo = ByteArrayOutputStream(audio.size + 1024)
        fun campo(nome: String, valore: String) {
            corpo.write("--$confine\r\nContent-Disposition: form-data; name=\"$nome\"\r\n\r\n$valore\r\n".toByteArray())
        }
        campo("model", "whisper-large-v3-turbo")
        campo("response_format", "text")
        campo("temperature", "0")
        if (pref.soloItaliano) campo("language", "it")
        corpo.write(
            ("--$confine\r\nContent-Disposition: form-data; name=\"file\"; filename=\"audio.${estensione(mime)}\"\r\n" +
                "Content-Type: $mime\r\n\r\n").toByteArray()
        )
        corpo.write(audio)
        corpo.write("\r\n--$confine--\r\n".toByteArray())

        val c = apri("https://api.groq.com/openai/v1/audio/transcriptions")
        c.setRequestProperty("Authorization", "Bearer $chiave")
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=$confine")
        return invia(c, corpo.toByteArray()).trim()
            .ifEmpty { throw ErroreServizio("nessun parlato riconosciuto") }
    }

    // ---------- Gemini ----------

    private fun gemini(chiave: String, audio: ByteArray, mime: String): String {
        // 1) modello dedicato alla trascrizione; 2) riserva: Flash generico con istruzioni.
        return try {
            geminiModello(chiave, "gemini-3.5-transcribe", audio, mime, null)
        } catch (e: ErroreServizio) {
            if (e.codice !in setOf(400, 403, 404)) throw e
            val lingua = if (pref.soloItaliano) " L'audio è in italiano." else ""
            geminiModello(
                chiave, "gemini-flash-latest", audio, mime,
                "Trascrivi fedelmente e per intero il parlato di questo audio.$lingua " +
                    "Usa la punteggiatura corretta. Rispondi SOLO con il testo trascritto, senza commenti né intestazioni.",
            )
        }
    }

    private fun geminiModello(chiave: String, modello: String, audio: ByteArray, mime: String, istruzioni: String?): String {
        val parti = JSONArray()
        if (istruzioni != null) parti.put(JSONObject().put("text", istruzioni))
        parti.put(
            JSONObject().put(
                "inline_data",
                JSONObject().put("mime_type", mime).put("data", Base64.encodeToString(audio, Base64.NO_WRAP)),
            )
        )
        val richiesta = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", parti)))

        val c = apri("https://generativelanguage.googleapis.com/v1beta/models/$modello:generateContent")
        c.setRequestProperty("x-goog-api-key", chiave)
        c.setRequestProperty("Content-Type", "application/json")
        val json = JSONObject(invia(c, richiesta.toString().toByteArray()))
        val candidati = json.optJSONArray("candidates")
        if (candidati == null || candidati.length() == 0) throw ErroreServizio("risposta vuota")
        val partiRisposta = candidati.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
            ?: throw ErroreServizio("risposta vuota")
        val testo = StringBuilder()
        for (i in 0 until partiRisposta.length()) {
            val p = partiRisposta.getJSONObject(i)
            if (p.optBoolean("thought")) continue
            testo.append(p.optString("text"))
        }
        return testo.toString().trim().ifEmpty { throw ErroreServizio("nessun parlato riconosciuto") }
    }

    // ---------- comuni ----------

    private fun apri(indirizzo: String): HttpURLConnection =
        (URL(indirizzo).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 20_000
            readTimeout = 180_000
        }

    private fun invia(c: HttpURLConnection, corpo: ByteArray): String {
        try {
            c.setFixedLengthStreamingMode(corpo.size)
            c.outputStream.use { it.write(corpo) }
            val codice = c.responseCode
            val testo = (if (codice in 200..299) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            if (codice !in 200..299) throw ErroreServizio(messaggioErrore(codice, testo), codice)
            return testo
        } finally {
            c.disconnect()
        }
    }

    private fun messaggioErrore(codice: Int, corpo: String): String {
        val dettaglio = try {
            JSONObject(corpo).optJSONObject("error")?.optString("message").orEmpty().take(200)
        } catch (_: Exception) {
            ""
        }
        val base = when (codice) {
            401, 403 -> "chiave API non valida"
            413 -> "file troppo grande"
            429 -> "limite gratuito raggiunto, riprova più tardi"
            in 500..599 -> "servizio non disponibile"
            else -> "errore $codice"
        }
        return if (dettaglio.isNotEmpty()) "$base ($dettaglio)" else base
    }

    private fun spiega(e: Exception): String = when (e) {
        is ErroreServizio -> e.message ?: "errore"
        is java.net.UnknownHostException -> "nessuna connessione a Internet"
        is java.net.SocketTimeoutException -> "il servizio non ha risposto in tempo"
        else -> e.message ?: e.javaClass.simpleName
    }

    companion object {
        private const val LIMITE_GROQ = 25 * 1024 * 1024
        private const val LIMITE_GEMINI = 60 * 1024 * 1024

        fun estensione(mime: String): String = when {
            "ogg" in mime || "opus" in mime -> "ogg"
            "mpeg" in mime || "mp3" in mime -> "mp3"
            "wav" in mime -> "wav"
            "flac" in mime -> "flac"
            "webm" in mime -> "webm"
            "video/mp4" in mime -> "mp4"
            "mp4" in mime || "m4a" in mime || "aac" in mime -> "m4a"
            else -> "ogg"
        }
    }
}
