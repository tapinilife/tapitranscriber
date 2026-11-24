package app.tapitranscriber

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Schermata che si apre dall'icona: registra, scegli un audio, condividi l'app. */
class PrincipaleActivity : Activity() {

    private lateinit var aggiornamenti: ControlloAggiornamenti

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        aggiornamenti = ControlloAggiornamenti(this)

        val colonna = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(20))
        }

        // ---- intestazione con ingranaggio ----
        colonna.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(context).apply { setImageResource(R.drawable.ic_launcher) }, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(TextView(context).apply {
                text = "TapiTranscriber"
                textSize = 22f
                setPadding(dp(12), 0, 0, 0)
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_impostazioni)
                imageTintList = coloreTesto()
                alpha = 0.75f
                background = toccoIcona()
                setPadding(dp(10), dp(10), dp(10), dp(10))
                contentDescription = "Impostazioni"
                setOnClickListener { startActivity(Intent(context, ImpostazioniActivity::class.java)) }
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
        })
        colonna.addView(TextView(this).apply {
            text = "Trascrivi un audio: registralo qui, sceglilo dal telefono o condividilo da WhatsApp e Telegram."
            textSize = 14f
            alpha = 0.75f
            setPadding(0, dp(14), 0, dp(18))
        })

        // ---- le tre schede ----
        colonna.addView(scheda(R.drawable.ic_microfono, "Registra un audio", "Parla, poi premi Stop", piena = true) { registra() })
        colonna.addView(scheda(R.drawable.ic_cartella, "Scegli un audio", "Dall'archivio del telefono", piena = false) { scegli() })
        colonna.addView(scheda(R.drawable.ic_condividi, "Condividi l'app", "APK dell'ultima versione + link", piena = false) {
            CondividiApp.condividi(this)
        })

        // ---- in fondo: ultima trascrizione e versione ----
        val versione = packageManager.getPackageInfo(packageName, 0).versionName
        colonna.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(20), 0, 0)
            addView(TextView(context).apply {
                text = "Ultima trascrizione"
                textSize = 14f
                setTextColor(VIOLA_CHIARO)
                setPadding(0, dp(10), dp(10), dp(10))
                setOnClickListener { mostraUltimaTrascrizione(this@PrincipaleActivity) }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(TextView(context).apply {
                text = "Versione $versione"
                textSize = 12f
                alpha = 0.5f
            })
        })

        setContentView(ScrollView(this).apply {
            fitsSystemWindows = true
            addView(colonna)
        })
    }

    override fun onResume() {
        super.onResume()
        aggiornamenti.seServe()
    }

    /** Scheda grande con icona, titolo e sottotitolo. */
    private fun scheda(icona: Int, titolo: String, sotto: String, piena: Boolean, azione: () -> Unit): View {
        val testo = if (piena) 0xFFFFFFFF.toInt() else 0xFF3C3489.toInt()
        val testoSotto = if (piena) 0xDDFFFFFF.toInt() else VIOLA
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (piena) VIOLA else 0xFFEDE7F6.toInt())
            }
            isClickable = true
            setOnClickListener { azione() }
            addView(ImageView(context).apply {
                setImageResource(icona)
                setColorFilter(testo)
            }, LinearLayout.LayoutParams(dp(28), dp(28)))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), 0, 0, 0)
                addView(TextView(context).apply {
                    text = titolo
                    textSize = 17f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(testo)
                })
                addView(TextView(context).apply {
                    text = sotto
                    textSize = 13f
                    setTextColor(testoSotto)
                })
            })
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(12) }
        }
    }

    // ---------- azioni ----------

    private fun registra() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            mostraRegistrazione(this)
        } else {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), RICHIESTA_MICROFONO)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != RICHIESTA_MICROFONO) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) mostraRegistrazione(this)
        else Toast.makeText(this, "Per registrare serve il permesso del microfono", Toast.LENGTH_LONG).show()
    }

    private fun scegli() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/*", "video/mp4", "application/ogg"))
        }
        runCatching { startActivityForResult(i, RICHIESTA_AUDIO) }
            .onFailure { Toast.makeText(this, "Nessuna app per scegliere i file", Toast.LENGTH_LONG).show() }
    }

    @Deprecated("API di Activity semplice, senza AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (requestCode != RICHIESTA_AUDIO || resultCode != RESULT_OK || uri == null) return
        startActivity(Intent(this, TrascriviActivity::class.java).apply {
            action = Intent.ACTION_SEND
            type = contentResolver.getType(uri) ?: "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        })
    }

    companion object {
        private const val RICHIESTA_MICROFONO = 10
        private const val RICHIESTA_AUDIO = 11
    }
}
