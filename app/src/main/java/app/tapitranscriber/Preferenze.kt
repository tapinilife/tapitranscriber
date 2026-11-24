package app.tapitranscriber

import android.content.Context

/** Chiavi API e opzioni: restano solo sul telefono (mai nel codice o nel repository). */
class Preferenze(context: Context) {
    // "trascrivi" = chiavi e opzioni: entrano nel backup di Android (passano al nuovo telefono).
    private val p = context.getSharedPreferences("trascrivi", Context.MODE_PRIVATE)
    // "locale" = cose che restano solo su questo telefono (escluse dal backup).
    private val locale = context.getSharedPreferences("locale", Context.MODE_PRIVATE)

    var chiaveGroq: String
        get() = p.getString("groq", "") ?: ""
        set(v) = p.edit().putString("groq", v.trim()).apply()

    var chiaveGemini: String
        get() = p.getString("gemini", "") ?: ""
        set(v) = p.edit().putString("gemini", v.trim()).apply()

    /** true = dice ai servizi che l'audio è in italiano (più preciso); false = lingua automatica. */
    var soloItaliano: Boolean
        get() = p.getBoolean("italiano", true)
        set(v) = p.edit().putBoolean("italiano", v).apply()

    var ultimaTrascrizione: String
        get() = locale.getString("ultima", "") ?: ""
        set(v) = locale.edit().putString("ultima", v).apply()

    /** Quando ha controllato gli aggiornamenti l'ultima volta (millisecondi). */
    var ultimoControllo: Long
        get() = locale.getLong("controllo", 0)
        set(v) = locale.edit().putLong("controllo", v).apply()

    init {
        // Dalla 1.1.0: l'ultima trascrizione stava con le chiavi, la sposto fuori dal backup.
        p.getString("ultima", null)?.let {
            locale.edit().putString("ultima", it).apply()
            p.edit().remove("ultima").apply()
        }
    }
}
