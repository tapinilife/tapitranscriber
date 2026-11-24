package app.tapitranscriber

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** Colori dell'app (gli stessi dell'icona). */
const val VIOLA = 0xFF5E35B1.toInt()
const val VIOLA_CHIARO = 0xFF7E57C2.toInt()

/** Converte dp in pixel. */
fun Context.dp(v: Int) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

/** Colore del testo principale del tema (nero in chiaro, bianco in scuro): per colorare le icone. */
fun Context.coloreTesto(): ColorStateList {
    val tv = TypedValue()
    theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)
    return if (tv.resourceId != 0) getColorStateList(tv.resourceId) else ColorStateList.valueOf(tv.data)
}

/** Effetto al tocco "senza bordo" del tema, per le icone cliccabili. */
fun Context.toccoIcona(): Drawable? {
    val tv = TypedValue()
    theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, tv, true)
    return if (tv.resourceId != 0) getDrawable(tv.resourceId) else null
}

/** Mostra l'ultima trascrizione salvata, con Copia e Condividi. */
fun mostraUltimaTrascrizione(a: Activity) {
    val t = Preferenze(a).ultimaTrascrizione
    if (t.isEmpty()) {
        Toast.makeText(a, "Nessuna trascrizione ancora", Toast.LENGTH_SHORT).show()
        return
    }
    val vista = TextView(a).apply {
        text = t
        textSize = 17f
        setTextIsSelectable(true)
        setPadding(a.dp(24), a.dp(8), a.dp(24), a.dp(8))
    }
    AlertDialog.Builder(a)
        .setTitle("Ultima trascrizione")
        .setView(ScrollView(a).apply { addView(vista) })
        .setPositiveButton("Copia") { _, _ ->
            a.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("Trascrizione", t))
        }
        .setNeutralButton("Condividi") { _, _ ->
            a.startActivity(Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t),
                "Condividi la trascrizione",
            ))
        }
        .setNegativeButton("Chiudi", null)
        .show()
}
