package app.tapitranscriber

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Impostazioni: chiavi API (inserite da un popup), guida per crearle, lingua, aggiornamenti. */
class ImpostazioniActivity : Activity() {

    private lateinit var pref: Preferenze
    private lateinit var aggiornamenti: ControlloAggiornamenti

    /** Le due chiavi: come si leggono/scrivono e cosa mostrare. */
    private inner class Chiave(
        val nome: String,
        val prefisso: String,
        val sito: String,
        val leggi: () -> String,
        val scrivi: (String) -> Unit,
    ) {
        lateinit var stato: TextView
        lateinit var pulsante: Button

        fun aggiorna() {
            val k = leggi()
            if (k.isEmpty()) {
                stato.text = "Non impostata"
                stato.setTextColor(0xFF9E9E9E.toInt())
                pulsante.text = "Inserisci"
            } else {
                stato.text = "✓ Impostata · ${k.take(4)}••••${k.takeLast(3)}"
                stato.setTextColor(0xFF2E7D32.toInt())
                pulsante.text = "Cambia"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pref = Preferenze(this)
        aggiornamenti = ControlloAggiornamenti(this)

        val colonna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(24))
        }

        // ---- intestazione con freccia indietro ----
        colonna.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = "←"
                textSize = 24f
                background = toccoIcona()
                gravity = Gravity.CENTER
                contentDescription = "Indietro"
                setOnClickListener { finish() }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            addView(TextView(context).apply {
                text = "Impostazioni"
                textSize = 22f
                setPadding(dp(8), 0, 0, 0)
            })
        })

        // ---- Groq ----
        val groq = Chiave("Groq", "gsk_", "console.groq.com", { pref.chiaveGroq }, { pref.chiaveGroq = it })
        colonna.addView(testo("Chiave Groq (principale)", 16f, dp(16)))
        colonna.addView(rigaChiave(groq))
        colonna.addView(guida(
            "https://console.groq.com/keys", "console.groq.com/keys",
            listOf(
                "Accedi con il tuo account Google (\"Continue with Google\"). È gratis, non serve la carta di credito.",
                "Premi \"Create API Key\".",
                "Come nome scrivi TapiTranscriber e lascia la scadenza su \"No expiration\".",
                "Spunta la verifica \"non sono un robot\" e premi \"Submit\".",
                "Premi \"Copy\": la chiave (inizia con gsk_) si vede una volta sola.",
                "Torna qui, premi \"Inserisci\" (o \"Cambia\"), poi \"Incolla\" e \"Salva\".",
            ),
            apertaAllInizio = pref.chiaveGroq.isEmpty(),
        ))

        // ---- Gemini ----
        val gemini = Chiave("Gemini", "AIza", "aistudio.google.com", { pref.chiaveGemini }, { pref.chiaveGemini = it })
        colonna.addView(testo("Chiave Gemini (riserva, facoltativa)", 16f, dp(20)))
        colonna.addView(rigaChiave(gemini))
        colonna.addView(guida(
            "https://aistudio.google.com/apikey", "aistudio.google.com/apikey",
            listOf(
                "Accedi con il tuo account Google e, se lo chiede, accetta le condizioni di Google AI Studio.",
                "Premi \"Create API key\" (o \"Crea chiave API\").",
                "Se chiede un progetto, scegli quello proposto o creane uno nuovo: va bene qualsiasi nome.",
                "Copia la chiave (inizia con AIza).",
                "Torna qui, premi \"Inserisci\" (o \"Cambia\"), poi \"Incolla\" e \"Salva\".",
            ),
            nota = "Gemini si usa solo se Groq non risponde o ha finito la quota del giorno. " +
                "Nel piano gratuito Google può usare gli audio inviati per migliorare i suoi servizi.",
            apertaAllInizio = false,
        ))

        // ---- lingua: si salva da sola ----
        colonna.addView(CheckBox(this).apply {
            text = "Audio in italiano (più preciso; togli la spunta per riconoscere la lingua da solo)"
            isChecked = pref.soloItaliano
            setOnCheckedChangeListener { _, si -> pref.soloItaliano = si }
        }, margine(dp(16)))

        colonna.addView(testo(
            "Le chiavi restano solo nelle impostazioni dell'app, nessun file in giro. " +
                "Se cambi telefono passano da sole con il backup di Google o con Smart Switch.",
            12f, dp(12), 0.65f,
        ))

        colonna.addView(Button(this).apply {
            text = "Controlla aggiornamenti"
            setOnClickListener { aggiornamenti.controlla(silenzioso = false) }
        }, margine(dp(12)))

        val versione = packageManager.getPackageInfo(packageName, 0).versionName
        colonna.addView(testo("Versione $versione", 12f, dp(20), 0.5f))

        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(colonna)
        })
    }

    // ---------- chiavi ----------

    /** Stato della chiave (impostata o no) e pulsante Inserisci/Cambia. */
    private fun rigaChiave(c: Chiave) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(4), 0, 0)
        c.stato = TextView(context).apply { textSize = 14f }
        c.pulsante = Button(context).apply { setOnClickListener { popupChiave(c) } }
        addView(c.stato, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(c.pulsante)
        c.aggiorna()
    }

    /** Popup con il campo (nascosto), Incolla, Salva/Annulla ed Elimina. */
    private fun popupChiave(c: Chiave) {
        val esistente = c.leggi()
        val campo = EditText(this).apply {
            setText(esistente)
            hint = "${c.prefisso}…"
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            // Tocco lungo sul campo = mostra/nasconde la chiave
            setOnLongClickListener {
                val nascosta = inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD != 0
                inputType = InputType.TYPE_CLASS_TEXT or
                    if (nascosta) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
                setSelection(text.length)
                true
            }
        }
        val incolla = Button(this).apply {
            text = "Incolla"
            setOnClickListener {
                val clip = getSystemService(ClipboardManager::class.java).primaryClip
                val t = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
                if (t.isNullOrEmpty()) Toast.makeText(context, "Non c'è niente di copiato", Toast.LENGTH_SHORT).show()
                else campo.setText(t)
            }
        }
        val vista = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(4), dp(24), 0)
            addView(TextView(context).apply {
                text = "Incolla la chiave copiata da ${c.sito} (inizia con ${c.prefisso})."
                textSize = 13f
                alpha = 0.75f
                setPadding(0, 0, 0, dp(8))
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(campo, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(incolla)
            })
            addView(TextView(context).apply {
                text = "Tieni premuto il campo per vederla"
                textSize = 12f
                alpha = 0.5f
                setPadding(0, dp(4), 0, 0)
            })
        }
        val b = AlertDialog.Builder(this)
            .setTitle("Chiave ${c.nome}")
            .setView(vista)
            .setPositiveButton("Salva") { _, _ ->
                val nuova = campo.text.toString().trim()
                c.scrivi(nuova)
                c.aggiorna()
                Toast.makeText(this, if (nuova.isEmpty()) "Chiave tolta" else "Chiave salvata", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Annulla", null)
        if (esistente.isNotEmpty()) b.setNeutralButton("Elimina") { _, _ ->
            AlertDialog.Builder(this)
                .setTitle("Eliminare la chiave ${c.nome}?")
                .setMessage("Potrai inserirla di nuovo quando vuoi.")
                .setPositiveButton("Elimina") { _, _ ->
                    c.scrivi("")
                    c.aggiorna()
                }
                .setNegativeButton("Annulla", null)
                .show()
        }
        b.show()
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

    /** Riquadro "Come si crea" apribile, con link alla pagina e passi numerati. */
    private fun guida(url: String, etichetta: String, passi: List<String>, nota: String? = null, apertaAllInizio: Boolean): View {
        val corpo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (apertaAllInizio) View.VISIBLE else View.GONE
            addView(TextView(context).apply {
                text = "1. Apri $etichetta ↗"
                textSize = 14f
                setTextColor(VIOLA_CHIARO)
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
            layoutParams = margine(dp(8))
        }
    }
}
