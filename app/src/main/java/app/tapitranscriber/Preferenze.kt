package app.tapitranscriber

import android.content.Context

/** Chiavi API e opzioni: restano solo sul telefono (mai nel codice o nel repository). */
class Preferenze(context: Context) {
    private val p = context.getSharedPreferences("trascrivi", Context.MODE_PRIVATE)

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
        get() = p.getString("ultima", "") ?: ""
        set(v) = p.edit().putString("ultima", v).apply()
}
