package app.tapitranscriber

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Schermata che si apre dall'icona: chiavi API con guida, lingua, ultima trascrizione, aggiornamenti. */
class ImpostazioniActivity : Activity() {

    private lateinit var pref: Preferenze
    private lateinit var campoGroq: EditText
    private lateinit var campoGemini: EditText
    private lateinit var italiano: CheckBox

    private fun dp(v: Int) =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pref = Preferenze(this)

        val colonna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        colonna.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(context).apply { setImageResource(R.drawable.ic_launcher) }, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(TextView(context).apply {
                text = "TapiTranscriber"
                textSize = 22f
                setPadding(dp(12), 0, 0, 0)
            })
        })
        colonna.addView(testo(
            "Da WhatsApp, Telegram o da un'app di registrazione: tieni premuto un audio, " +
                "tocca Condividi e scegli TapiTranscriber. La trascrizione compare in un popup.",
            14f, dp(12),
        ))

        // ---- Groq ----
        colonna.addView(testo("Chiave Groq (principale)", 16f, dp(24)))
        campoGroq = campo(pref.chiaveGroq, "gsk_…")
        colonna.addView(rigaCampo(campoGroq))
        colonna.addView(guida(
            "https://console.groq.com/keys", "console.groq.com/keys",
            listOf(
                "Accedi con il tuo account Google (\"Continue with Google\"). È gratis, non serve la carta di credito.",
                "Premi \"Create API Key\".",
                "Come nome scrivi TapiTranscriber e lascia la scadenza su \"No expiration\".",
                "Spunta la verifica \"non sono un robot\" e premi \"Submit\".",
                "Premi \"Copy\": la chiave (inizia con gsk_) si vede una volta sola.",
                "Torna qui, premi \"Incolla\" accanto al campo e poi \"Salva\".",
            ),
            apertaAllInizio = pref.chiaveGroq.isEmpty(),
        ))

        // ---- Gemini ----
        colonna.addView(testo("Chiave Gemini (riserva, facoltativa)", 16f, dp(20)))
        campoGemini = campo(pref.chiaveGemini, "AIza…")
        colonna.addView(rigaCampo(campoGemini))
        colonna.addView(guida(
            "https://aistudio.google.com/apikey", "aistudio.google.com/apikey",
            listOf(
                "Accedi con il tuo account Google e, se lo chiede, accetta le condizioni di Google AI Studio.",
                "Premi \"Create API key\" (o \"Crea chiave API\").",
                "Se chiede un progetto, scegli quello proposto o creane uno nuovo: va bene qualsiasi nome.",
                "Copia la chiave (inizia con AIza).",
                "Torna qui, premi \"Incolla\" accanto al campo e poi \"Salva\".",
            ),
            nota = "Gemini si usa solo se Groq non risponde o ha finito la quota del giorno. " +
                "Nel piano gratuito Google può usare gli audio inviati per migliorare i suoi servizi.",
            apertaAllInizio = false,
        ))

        italiano = CheckBox(this).apply {
            text = "Audio in italiano (più preciso; togli la spunta per riconoscere la lingua da solo)"
            isChecked = pref.soloItaliano
        }
        colonna.addView(italiano, margine(dp(16)))

        colonna.addView(Button(this).apply {
            text = "Salva"
            setOnClickListener { salva(); Toast.makeText(context, "Impostazioni salvate", Toast.LENGTH_SHORT).show() }
        }, margine(dp(16)))

        colonna.addView(testo(
            "Le chiavi restano solo nelle impostazioni dell'app, nessun file in giro. " +
                "Se cambi telefono passano da sole con il backup di Google o con Smart Switch.",
            12f, dp(8), 0.65f,
        ))

        colonna.addView(Button(this).apply {
            text = "Ultima trascrizione"
            setOnClickListener { mostraUltima() }
        }, margine(dp(4)))

        colonna.addView(Button(this).apply {
            text = "Controlla aggiornamenti"
            setOnClickListener { controllaAggiornamenti(silenzioso = false) }
        }, margine(dp(4)))

        val versione = packageManager.getPackageInfo(packageName, 0).versionName
        colonna.addView(testo("Versione $versione", 12f, dp(20), 0.5f))

        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(colonna)
        })
    }

    private fun salva() {
        pref.chiaveGroq = campoGroq.text.toString()
        pref.chiaveGemini = campoGemini.text.toString()
        pref.soloItaliano = italiano.isChecked
    }

    // ---------- pezzi della schermata ----------

    private fun margine(sopra: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = sopra }

    private fun testo(s: String, size: Float, sopra: Int, trasparenza: Float = 1f) = TextView(this).apply {
        text = s
        textSize = size
        alpha = trasparenza
        setPadding(0, sopra, 0, 0)
    }

    private fun campo(valore: String, suggerimento: String) = EditText(this).apply {
        setText(valore)
        hint = suggerimento
        isSingleLine = true
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        // Tocco lungo sul campo = mostra/nasconde la chiave
        setOnLongClickListener {
            val nascosta = inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD != 0
            inputType = InputType.TYPE_CLASS_TEXT or
                if (nascosta) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSelection(text.length)
            false
        }
    }

    /** Campo + pulsante "Incolla" (prende il testo copiato). */
    private fun rigaCampo(campo: EditText) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(campo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(Button(context).apply {
            text = "Incolla"
            setOnClickListener {
                val clip = getSystemService(ClipboardManager::class.java).primaryClip
                val t = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                if (t.isNullOrEmpty()) {
                    Toast.makeText(context, "Non c'è niente di copiato", Toast.LENGTH_SHORT).show()
                } else {
                    campo.setText(t)
                    salva()
                    Toast.makeText(context, "Chiave incollata e salvata", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    /** Riquadro "Come si crea" apribile, con link alla pagina e passi numerati. */
    private fun guida(url: String, etichetta: String, passi: List<String>, nota: String? = null, apertaAllInizio: Boolean): View {
        val corpo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (apertaAllInizio) View.VISIBLE else View.GONE
            addView(TextView(context).apply {
                text = "1. Apri $etichetta ↗"
                textSize = 14f
                setTextColor(0xFF7E57C2.toInt())
                setPadding(0, dp(6), 0, dp(4))
                setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            })
            passi.forEachIndexed { i, p ->
                addView(TextView(context).apply {
                    text = "${i + 2}. $p"
                    textSize = 14f
                    setPadding(0, dp(2), 0, dp(2))
                })
            }
            if (nota != null) addView(TextView(context).apply {
                text = nota
                textSize = 12f
                alpha = 0.65f
                setPadding(0, dp(8), 0, 0)
            })
        }
        val titolo = TextView(this).apply {
            textSize = 14f
            setPadding(0, 0, 0, 0)
            fun aggiorna() { text = if (corpo.visibility == View.VISIBLE) "Come si crea (gratis) ▴" else "Come si crea (gratis) ▾" }
            aggiorna()
            setOnClickListener {
                corpo.visibility = if (corpo.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                aggiorna()
            }
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(10).toFloat()
                setColor(0x147E57C2)
            }
            addView(titolo)
            addView(corpo)
            layoutParams = margine(dp(6))
        }
    }

    // ---------- aggiornamenti ----------

    override fun onResume() {
        super.onResume()
        // Controllo automatico al massimo ogni 6 ore, senza disturbare se non c'è niente di nuovo
        if (System.currentTimeMillis() - pref.ultimoControllo > 6 * 3_600_000L) controllaAggiornamenti(silenzioso = true)
    }

    private var controlloInCorso = false

    private fun controllaAggiornamenti(silenzioso: Boolean) {
        if (controlloInCorso) return
        controlloInCorso = true
        if (!silenzioso) Toast.makeText(this, "Controllo aggiornamenti…", Toast.LENGTH_SHORT).show()
        Thread {
            val esito = runCatching { Aggiornamenti.ultima() }
            runOnUiThread {
                controlloInCorso = false
                if (isFinishing || isDestroyed) return@runOnUiThread
                val v = esito.getOrNull()
                if (esito.isSuccess) pref.ultimoControllo = System.currentTimeMillis()
                when {
                    v != null && v.codice > Aggiornamenti.versioneInstallata(this) -> proponi(v)
                    silenzioso -> {}
                    esito.isFailure -> Toast.makeText(this, "Non riesco a controllare: sei connesso a Internet?", Toast.LENGTH_LONG).show()
                    else -> Toast.makeText(this, "Hai già l'ultima versione", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun proponi(v: Versione) {
        AlertDialog.Builder(this)
            .setTitle("Nuova versione ${v.nome}")
            .setMessage(v.novita.ifBlank { "È disponibile una versione nuova di TapiTranscriber." })
            .setPositiveButton("Aggiorna") { _, _ -> aggiorna(v) }
            .setNegativeButton("Più tardi", null)
            .show()
    }

    private fun aggiorna(v: Versione) {
        if (!packageManager.canRequestPackageInstalls()) {
            // Serve una volta sola: Android chiede di permettere a TapiTranscriber di installare i suoi aggiornamenti
            AlertDialog.Builder(this)
                .setTitle("Permesso da dare una volta")
                .setMessage(
                    "Per aggiornarsi da sola TapiTranscriber deve poter installare app.\n\n" +
                        "Nella schermata che si apre attiva \"Consenti da questa fonte\", poi torna qui e premi di nuovo Aggiorna.",
                )
                .setPositiveButton("Apri") { _, _ ->
                    runCatching {
                        startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                    }
                }
                .setNegativeButton("Annulla", null)
                .show()
            return
        }
        val barra = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val attesa = AlertDialog.Builder(this)
            .setTitle("Scarico la versione ${v.nome}…")
            .setView(LinearLayout(this).apply {
                setPadding(dp(24), dp(12), dp(24), dp(8))
                addView(barra, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            })
            .setCancelable(false)
            .show()
        Thread {
            val esito = runCatching {
                val apk = Aggiornamenti.scarica(this, v) { p -> runOnUiThread { barra.progress = p } }
                Aggiornamenti.installa(this, apk)
            }
            runOnUiThread {
                if (isDestroyed) return@runOnUiThread
                attesa.dismiss()
                esito.onFailure {
                    AlertDialog.Builder(this)
                        .setTitle("Aggiornamento non riuscito")
                        .setMessage("Download non riuscito. Controlla la connessione e riprova.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }

    // ---------- ultima trascrizione ----------

    private fun mostraUltima() {
        val t = pref.ultimaTrascrizione
        if (t.isEmpty()) {
            Toast.makeText(this, "Nessuna trascrizione ancora", Toast.LENGTH_SHORT).show()
            return
        }
        val vista = TextView(this).apply {
            text = t
            textSize = 17f
            setTextIsSelectable(true)
            setPadding(dp(24), dp(8), dp(24), dp(8))
        }
        AlertDialog.Builder(this)
            .setTitle("Ultima trascrizione")
            .setView(ScrollView(this).apply { addView(vista) })
            .setPositiveButton("Copia") { _, _ ->
                getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Trascrizione", t))
            }
            .setNeutralButton("Condividi") { _, _ ->
                startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t),
                    "Condividi la trascrizione",
                ))
            }
            .setNegativeButton("Chiudi", null)
            .show()
    }

}
