package app.tapitranscriber

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Passa l'audio registrato a TrascriviActivity restando in RAM
 * (troppo grande per gli extra di un Intent, e su disco non deve finire).
 */
object AudioInMemoria {
    @Volatile private var dati: ByteArray? = null
    @Volatile var mime: String = "audio/wav"
        private set

    fun metti(d: ByteArray, m: String) {
        dati = d
        mime = m
    }

    /** Restituisce l'audio e lo toglie subito da qui. */
    fun prendi(): ByteArray? = dati.also { dati = null }
}

/** Registra dal microfono in RAM (PCM 16 kHz mono) e alla fine produce un WAV in memoria. */
class Registratore {
    private val frequenza = 16_000
    private val buffer = ByteArrayOutputStream()
    @Volatile private var attivo = false
    private var filo: Thread? = null

    /** Byte al secondo del PCM: 16.000 campioni × 2 byte. */
    val byteAlSecondo = frequenza * 2

    val secondi: Int get() = buffer.size() / byteAlSecondo

    @SuppressLint("MissingPermission") // il permesso lo chiede PrincipaleActivity prima di arrivare qui
    fun avvia(): Boolean {
        val minimo = AudioRecord.getMinBufferSize(frequenza, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimo <= 0) return false
        val rec = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, frequenza, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minimo * 4)
        } catch (e: Exception) {
            return false
        }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            return false
        }
        attivo = true
        rec.startRecording()
        filo = Thread {
            val pezzo = ByteArray(minimo)
            try {
                while (attivo) {
                    val n = rec.read(pezzo, 0, pezzo.size)
                    if (n > 0) synchronized(buffer) { buffer.write(pezzo, 0, n) }
                }
            } finally {
                runCatching { rec.stop() }
                rec.release()
            }
        }.apply { start() }
        return true
    }

    /** Ferma e restituisce il WAV (in RAM). */
    fun ferma(): ByteArray {
        attivo = false
        filo?.join(2_000)
        val pcm = synchronized(buffer) { buffer.toByteArray() }
        buffer.reset()
        return wav(pcm)
    }

    /** Ferma e butta via tutto. */
    fun scarta() {
        attivo = false
        filo?.join(2_000)
        buffer.reset()
    }

    private fun wav(pcm: ByteArray): ByteArray {
        val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        h.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray())
        h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
            .putInt(frequenza).putInt(byteAlSecondo).putShort(2).putShort(16)
        h.put("data".toByteArray()).putInt(pcm.size)
        return h.array() + pcm
    }
}

/** Popup di registrazione: tempo che scorre, Annulla e "Stop e trascrivi". Massimo 12 minuti. */
fun mostraRegistrazione(a: Activity) {
    val reg = Registratore()
    if (!reg.avvia()) {
        Toast.makeText(a, "Non riesco ad accendere il microfono", Toast.LENGTH_LONG).show()
        return
    }
    val massimo = 12 * 60
    val tempo = TextView(a).apply {
        text = "0:00"
        textSize = 32f
        gravity = Gravity.CENTER
        setPadding(0, a.dp(14), 0, 0)
    }
    val cerchio = FrameLayout(a).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x33E24B4A) }
        addView(ImageView(a).apply {
            setImageResource(R.drawable.ic_microfono)
            setColorFilter(0xFFFFFFFF.toInt())
            setPadding(a.dp(14), a.dp(14), a.dp(14), a.dp(14))
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xFFE24B4A.toInt()) }
        }, FrameLayout.LayoutParams(a.dp(60), a.dp(60), Gravity.CENTER))
    }
    val vista = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(a.dp(24), a.dp(12), a.dp(24), a.dp(4))
        addView(cerchio, LinearLayout.LayoutParams(a.dp(88), a.dp(88)))
        addView(tempo)
        addView(TextView(a).apply {
            text = "Massimo 12 minuti · l'audio resta solo in memoria"
            textSize = 12f
            alpha = 0.65f
            gravity = Gravity.CENTER
            setPadding(0, a.dp(4), 0, 0)
        })
    }

    val orologio = Handler(Looper.getMainLooper())
    var finito = false
    lateinit var d: AlertDialog

    fun stopETrascrivi() {
        if (finito) return
        finito = true
        orologio.removeCallbacksAndMessages(null)
        val audio = reg.ferma()
        d.dismiss()
        if (audio.size <= 44 + reg.byteAlSecondo / 2) {
            Toast.makeText(a, "Registrazione troppo corta", Toast.LENGTH_SHORT).show()
            return
        }
        AudioInMemoria.metti(audio, "audio/wav")
        a.startActivity(Intent(a, TrascriviActivity::class.java).putExtra(TrascriviActivity.EXTRA_REGISTRAZIONE, true))
    }

    d = AlertDialog.Builder(a)
        .setTitle("Registrazione")
        .setView(vista)
        .setCancelable(false)
        .setPositiveButton("Stop e trascrivi", null)
        .setNegativeButton("Annulla") { _, _ ->
            finito = true
            orologio.removeCallbacksAndMessages(null)
            reg.scarta()
        }
        .create()
    d.setOnShowListener { d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { stopETrascrivi() } }
    d.setOnDismissListener {
        // Chiuso in altro modo (schermata distrutta): non resta nulla in memoria
        if (!finito) {
            finito = true
            orologio.removeCallbacksAndMessages(null)
            reg.scarta()
        }
    }
    d.show()

    orologio.post(object : Runnable {
        override fun run() {
            if (finito) return
            val s = reg.secondi
            tempo.text = "%d:%02d".format(s / 60, s % 60)
            if (s >= massimo) stopETrascrivi() else orologio.postDelayed(this, 250)
        }
    })
}
