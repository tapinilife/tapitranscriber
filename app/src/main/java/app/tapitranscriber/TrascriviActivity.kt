package app.tapitranscriber

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Riceve un audio da "Condividi" / "Apri con" e mostra la trascrizione in un popup. */
class TrascriviActivity : Activity() {

    private var dialogo: AlertDialog? = null
    private var chiusa = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = audioDaIntent(intent)
        if (uri == null) {
            Toast.makeText(this, "Nessun audio ricevuto", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        mostraAttesa()
        Thread { lavora(uri) }.start()
    }

    private fun audioDaIntent(i: Intent): Uri? {
        val diretto: Uri? = when (i.action) {
            Intent.ACTION_SEND ->
                if (Build.VERSION.SDK_INT >= 33) i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") (i.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)
            Intent.ACTION_VIEW -> i.data
            else -> null
        }
        return diretto ?: i.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    }

    /** Legge l'audio in RAM (nessuna copia su disco), lo trascrive e poi lo lascia liberare. */
    private fun lavora(uri: Uri) {
        val pref = Preferenze(this)
        val esito: Result<Risultato> = try {
            val mime = (intent.type?.takeIf { it != "*/*" } ?: contentResolver.getType(uri) ?: "audio/ogg")
                .substringBefore(';').trim().lowercase()
            val audio = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw ErroreServizio("Impossibile leggere l'audio")
            if (audio.isEmpty()) throw ErroreServizio("L'audio è vuoto")
            val t = Trascrittore(pref)
            val r = t.trascrivi(audio, mime)
            // Whisper punteggia poco i vocali parlati: se possibile sistemo punteggiatura e paragrafi
            val sistemato = if (r.servizio == "Groq") t.sistemaPunteggiatura(r.testo) else null
            Result.success(
                if (sistemato != null) r.copy(testo = sistemato, nota = listOf(r.nota, "punteggiatura sistemata").filter { it.isNotEmpty() }.joinToString(" · "))
                else r,
            )
        } catch (e: SecurityException) {
            Result.failure(ErroreServizio("L'app da cui hai condiviso non permette di leggere l'audio. Prova a condividerlo come file."))
        } catch (e: java.io.FileNotFoundException) {
            Result.failure(ErroreServizio("Audio non trovato: forse non è ancora scaricato. Aprilo una volta e riprova."))
        } catch (e: OutOfMemoryError) {
            Result.failure(ErroreServizio("Audio troppo grande per il telefono"))
        } catch (e: Exception) {
            Result.failure(e)
        }
        runOnUiThread {
            if (chiusa || isFinishing) return@runOnUiThread
            dialogo?.setOnDismissListener(null)
            dialogo?.dismiss()
            esito.onSuccess {
                pref.ultimaTrascrizione = it.testo
                mostraTesto(it)
            }.onFailure { mostraErrore(it.message ?: it.javaClass.simpleName) }
        }
    }

    // ---------- popup ----------

    private fun dp(v: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private fun mostraAttesa() {
        val riga = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(12))
            addView(ProgressBar(context))
            addView(TextView(context).apply {
                text = "Trascrizione in corso…"
                textSize = 16f
                setPadding(dp(16), 0, 0, 0)
            })
        }
        dialogo = AlertDialog.Builder(this)
            .setTitle("TapiTranscriber")
            .setView(riga)
            .setNegativeButton("Annulla", null)
            .setOnDismissListener { chiudi() }
            .show()
    }

    private fun mostraTesto(r: Risultato) {
        val pref = Preferenze(this)
        var riassunto: String? = null

        val boxRiassunto = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(0x247E57C2)
            }
        }
        val titoloRiassunto = TextView(this).apply {
            text = "Riassunto"
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(0xFF7E57C2.toInt())
        }
        val testoRiassunto = TextView(this).apply {
            textSize = 15f
            setTextIsSelectable(true)
            setLineSpacing(0f, 1.15f)
            setPadding(0, dp(4), 0, 0)
        }
        boxRiassunto.addView(titoloRiassunto)
        boxRiassunto.addView(testoRiassunto)

        val contenuto = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(boxRiassunto, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(12) })
            addView(TextView(context).apply {
                text = r.testo
                textSize = 17f
                setTextIsSelectable(true)
                setLineSpacing(0f, 1.15f)
            })
            addView(TextView(context).apply {
                text = buildString {
                    append("Trascritto con ${r.servizio}")
                    if (r.nota.isNotEmpty()) append(" · ${r.nota}")
                }
                textSize = 12f
                alpha = 0.6f
                setPadding(0, dp(12), 0, dp(4))
            })
        }
        val scorri = ScrollView(this).apply { addView(contenuto) }

        fun pulsante(etichetta: String) = Button(this, null, android.R.attr.borderlessButtonStyle).apply {
            text = etichetta
            isAllCaps = false
            textSize = 14f
            minWidth = 0
            setPadding(dp(8), 0, dp(8), 0)
        }
        val condividi = pulsante("Condividi")
        val riassumi = pulsante("Riassumi")
        val chiudi = pulsante("Chiudi")
        val copia = pulsante("Copia")
        val barra = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(12), dp(4), dp(12), dp(8))
            for (b in listOf(condividi, riassumi, chiudi, copia)) {
                addView(b, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            }
        }

        val radice = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            // Il testo scorre, i pulsanti restano sempre visibili in basso
            addView(scorri, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(barra)
        }
        val d = AlertDialog.Builder(this)
            .setTitle("Trascrizione")
            .setView(radice)
            .setOnDismissListener { chiudi() }
            .create()

        /** Con il riassunto pronto, Copia e Condividi chiedono cosa usare. */
        fun scegli(azione: (String) -> Unit) {
            val rs = riassunto ?: return azione(r.testo)
            AlertDialog.Builder(this)
                .setItems(arrayOf("Testo intero", "Riassunto")) { _, i -> azione(if (i == 0) r.testo else rs) }
                .show()
        }

        copia.setOnClickListener { scegli { copia(it) } }
        condividi.setOnClickListener { scegli { condividi(it) } }
        chiudi.setOnClickListener { d.dismiss() }
        riassumi.setOnClickListener {
            if (riassunto != null) {
                scorri.smoothScrollTo(0, 0)
                return@setOnClickListener
            }
            riassumi.isEnabled = false
            riassumi.text = "Riassumo…"
            Thread {
                val esito = runCatching { Trascrittore(pref).riassumi(r.testo) }
                runOnUiThread {
                    if (chiusa || isFinishing) return@runOnUiThread
                    riassumi.isEnabled = true
                    riassumi.text = "Riassumi"
                    esito.onSuccess {
                        riassunto = it.testo
                        testoRiassunto.text = it.testo
                        titoloRiassunto.text = "Riassunto · ${it.servizio}"
                        boxRiassunto.visibility = View.VISIBLE
                        scorri.smoothScrollTo(0, 0)
                    }.onFailure {
                        Toast.makeText(this, "Riassunto non riuscito: ${it.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }

        dialogo = d
        d.show()
    }

    private fun mostraErrore(messaggio: String) {
        dialogo = AlertDialog.Builder(this)
            .setTitle("Trascrizione non riuscita")
            .setMessage(messaggio)
            .setPositiveButton("Impostazioni") { _, _ ->
                startActivity(Intent(this, ImpostazioniActivity::class.java))
            }
            .setNegativeButton("Chiudi", null)
            .setOnDismissListener { chiudi() }
            .show()
    }

    private fun copia(testo: String) {
        val cm = getSystemService(ClipboardManager::class.java)
        cm.setPrimaryClip(ClipData.newPlainText("Trascrizione", testo))
        // Da Android 13 il sistema mostra già la conferma
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, "Testo copiato", Toast.LENGTH_SHORT).show()
    }

    private fun condividi(testo: String) {
        val invio = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, testo)
        }
        startActivity(Intent.createChooser(invio, "Condividi la trascrizione"))
    }

    private fun chiudi() {
        chiusa = true
        finish()
    }

    override fun onDestroy() {
        dialogo?.setOnDismissListener(null)
        dialogo?.dismiss()
        // L'audio non viene mai scritto su disco (resta solo in RAM durante l'invio).
        // Per sicurezza svuoto comunque le cartelle temporanee dell'app.
        runCatching { cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
        runCatching { codeCacheDir.listFiles()?.forEach { it.deleteRecursively() } }
        super.onDestroy()
    }
}
