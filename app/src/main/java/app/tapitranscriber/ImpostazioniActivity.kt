package app.tapitranscriber

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Schermata che si apre dall'icona: chiavi API, lingua, ultima trascrizione. */
class ImpostazioniActivity : Activity() {

    private lateinit var pref: Preferenze

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

        colonna.addView(testo("Chiave Groq (principale)", 16f, dp(24)))
        val groq = campo(pref.chiaveGroq, "gsk_…")
        colonna.addView(groq)
        colonna.addView(link("Crea la chiave gratuita su console.groq.com", "https://console.groq.com/keys"))

        colonna.addView(testo("Chiave Gemini (riserva, facoltativa)", 16f, dp(20)))
        val gemini = campo(pref.chiaveGemini, "AIza…")
        colonna.addView(gemini)
        colonna.addView(link("Crea la chiave gratuita su aistudio.google.com", "https://aistudio.google.com/apikey"))
        colonna.addView(testo(
            "Gemini si usa solo se Groq non risponde o ha finito la quota del giorno. " +
                "Nel piano gratuito Google può usare gli audio inviati per migliorare i suoi servizi.",
            12f, dp(4), 0.65f,
        ))

        val italiano = CheckBox(this).apply {
            text = "Audio in italiano (più preciso; togli la spunta per riconoscere la lingua da solo)"
            isChecked = pref.soloItaliano
        }
        colonna.addView(italiano, margine(dp(16)))

        colonna.addView(Button(this).apply {
            text = "Salva"
            setOnClickListener {
                pref.chiaveGroq = groq.text.toString()
                pref.chiaveGemini = gemini.text.toString()
                pref.soloItaliano = italiano.isChecked
                Toast.makeText(context, "Impostazioni salvate", Toast.LENGTH_SHORT).show()
            }
        }, margine(dp(16)))

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

    private fun link(s: String, url: String) = TextView(this).apply {
        text = "$s ↗"
        textSize = 13f
        setTextColor(0xFF7E57C2.toInt())
        setPadding(0, dp(4), 0, dp(4))
        setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

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
                getSystemService(android.content.ClipboardManager::class.java)
                    .setPrimaryClip(android.content.ClipData.newPlainText("Trascrizione", t))
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
