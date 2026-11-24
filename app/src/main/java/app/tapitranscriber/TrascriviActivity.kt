package app.tapitranscriber

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
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

    private fun lavora(uri: Uri) {
        val pref = Preferenze(this)
        val esito: Result<Risultato> = try {
            val mime = (intent.type?.takeIf { it != "*/*" } ?: contentResolver.getType(uri) ?: "audio/ogg")
                .substringBefore(';').trim().lowercase()
            val audio = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw ErroreServizio("Impossibile leggere l'audio")
            if (audio.isEmpty()) throw ErroreServizio("L'audio è vuoto")
            Result.success(Trascrittore(pref).trascrivi(audio, mime))
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
        val contenuto = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(8), dp(24), 0)
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
        val d = AlertDialog.Builder(this)
            .setTitle("Trascrizione")
            .setView(scorri)
            .setPositiveButton("Copia", null)
            .setNeutralButton("Condividi", null)
            .setNegativeButton("Chiudi", null)
            .setOnDismissListener { chiudi() }
            .create()
        d.setOnShowListener {
            // "Copia" e "Condividi" non chiudono il popup
            d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { copia(r.testo) }
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { condividi(r.testo) }
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
        super.onDestroy()
    }
}
