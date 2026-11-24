package app.tapitranscriber

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

/** Schermata che si apre dall'icona: chiavi API con guida, lingua, esporta/importa, ultima trascrizione. */
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

        colonna.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(context).apply {
                text = "Esporta chiavi"
                setOnClickListener { chiediEsporta() }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(context).apply {
                text = "Importa chiavi"
                setOnClickListener { avviaImporta() }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }, margine(dp(4)))

        colonna.addView(Button(this).apply {
            text = "Ultima trascrizione"
            setOnClickListener { mostraUltima() }
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

    // ---------- esporta / importa ----------

    private fun chiediEsporta() {
        salva()
        if (pref.chiaveGroq.isEmpty() && pref.chiaveGemini.isEmpty()) {
            Toast.makeText(this, "Non ci sono chiavi da esportare", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Esporta chiavi")
            .setMessage(
                "Crea un file con le tue chiavi, da aprire con \"Importa chiavi\" sull'altro telefono. " +
                    "Puoi salvarlo in Download o su Drive.\n\nIl file non è protetto: dopo l'importazione cancellalo.",
            )
            .setPositiveButton("Salva file") { _, _ ->
                startActivityForResult(
                    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                        putExtra(Intent.EXTRA_TITLE, "tapitranscriber-chiavi.json")
                    },
                    RICHIESTA_ESPORTA,
                )
            }
            .setNegativeButton("Annulla", null)
            .show()
    }

    private fun avviaImporta() {
        startActivityForResult(
            Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            },
            RICHIESTA_IMPORTA,
        )
    }

    @Deprecated("API di Activity semplice, senza AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        when (requestCode) {
            RICHIESTA_ESPORTA -> esporta(uri)
            RICHIESTA_IMPORTA -> importa(uri)
        }
    }

    private fun esporta(uri: Uri) {
        val json = JSONObject()
            .put("app", "TapiTranscriber")
            .put("versione", 1)
            .put("groq", pref.chiaveGroq)
            .put("gemini", pref.chiaveGemini)
            .put("italiano", pref.soloItaliano)
        val ok = runCatching {
            contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toString(2).toByteArray()) }
        }.isSuccess
        Toast.makeText(this, if (ok) "Chiavi esportate" else "Esportazione non riuscita", Toast.LENGTH_LONG).show()
    }

    private fun importa(uri: Uri) {
        val json = runCatching {
            val testo = contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            JSONObject(testo).takeIf { it.optString("app") == "TapiTranscriber" }
        }.getOrNull()
        if (json == null) {
            AlertDialog.Builder(this)
                .setTitle("Importazione non riuscita")
                .setMessage("Il file scelto non è un'esportazione di TapiTranscriber.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val groq = json.optString("groq")
        val gemini = json.optString("gemini")
        if (groq.isNotEmpty()) campoGroq.setText(groq)
        if (gemini.isNotEmpty()) campoGemini.setText(gemini)
        if (json.has("italiano")) italiano.isChecked = json.optBoolean("italiano", true)
        salva()
        val quali = listOfNotNull("Groq".takeIf { groq.isNotEmpty() }, "Gemini".takeIf { gemini.isNotEmpty() })
            .joinToString(" e ")
        AlertDialog.Builder(this)
            .setTitle("Chiavi importate")
            .setMessage("Importate e salvate: $quali.\n\nOra puoi cancellare il file di esportazione.")
            .setPositiveButton("OK", null)
            .show()
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

    companion object {
        private const val RICHIESTA_ESPORTA = 1
        private const val RICHIESTA_IMPORTA = 2
    }
}
