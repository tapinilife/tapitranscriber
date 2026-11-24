package app.tapitranscriber

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Una versione pubblicata nelle Releases di GitHub (tag "v<versionName>-<versionCode>", APK allegato). */
data class Versione(val codice: Long, val nome: String, val urlApk: String, val dimensione: Long, val novita: String)

/**
 * Aggiornamenti dal repository PUBBLICO github.com/tapinilife/tapitranscriber: nessuna chiave nell'APK.
 * Android installa l'aggiornamento solo se è firmato con la stessa chiave dell'app installata.
 */
object Aggiornamenti {
    const val REPO = "tapinilife/tapitranscriber"
    const val PAGINA = "https://github.com/$REPO/releases/latest"

    fun versioneInstallata(ctx: Context): Long {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }

    /** Ultima versione pubblicata, o null se non ce n'è. Da chiamare fuori dal thread principale. */
    fun ultima(): Versione? {
        val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "TapiTranscriber")
        try {
            if (c.responseCode == 404) return null
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            val json = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = json.getString("tag_name")
            val codice = tag.substringAfterLast('-').toLongOrNull() ?: return null
            val nome = tag.removePrefix("v").substringBeforeLast('-')
            val allegati = json.getJSONArray("assets")
            for (i in 0 until allegati.length()) {
                val a = allegati.getJSONObject(i)
                if (a.getString("name").endsWith(".apk")) {
                    return Versione(codice, nome, a.getString("browser_download_url"), a.getLong("size"), json.optString("body"))
                }
            }
            return null
        } finally {
            c.disconnect()
        }
    }

    /** File temporaneo dell'aggiornamento, nella memoria privata dell'app; si cancella dopo l'installazione. */
    fun fileTemporaneo(ctx: Context) = File(ctx.noBackupFilesDir, "aggiornamento.apk")

    /** Scarica l'APK; [avanzamento] riceve la percentuale. Da chiamare fuori dal thread principale. */
    fun scarica(ctx: Context, v: Versione, avanzamento: (Int) -> Unit): File {
        val dest = fileTemporaneo(ctx)
        val c = URL(v.urlApk).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.setRequestProperty("User-Agent", "TapiTranscriber")
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode}")
            val totale = c.contentLengthLong.takeIf { it > 0 } ?: v.dimensione
            c.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    var fatto = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        fatto += n
                        if (totale > 0) avanzamento((fatto * 100 / totale).toInt())
                    }
                }
            }
            if (v.dimensione > 0 && dest.length() != v.dimensione) throw IOException("dimensione errata")
            // Controllo che sia davvero TapiTranscriber, nella versione annunciata
            val info = ctx.packageManager.getPackageArchiveInfo(dest.path, 0)
            val codice = if (Build.VERSION.SDK_INT >= 28) info?.longVersionCode else @Suppress("DEPRECATION") info?.versionCode?.toLong()
            if (info == null || info.packageName != ctx.packageName || codice != v.codice) {
                throw IOException("il file scaricato non è una versione valida di TapiTranscriber")
            }
            return dest
        } catch (e: Exception) {
            dest.delete()
            throw e
        } finally {
            c.disconnect()
        }
    }

    /** Passa l'APK all'installatore di sistema, poi cancella il file. */
    fun installa(ctx: Context, apk: File) {
        try {
            val installer = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(ctx.packageName)
                setSize(apk.length())
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { s ->
                s.openWrite("tapitranscriber.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    s.fsync(out)
                }
                val esito = PendingIntent.getBroadcast(
                    ctx, id, Intent(ctx, EsitoInstallazione::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                s.commit(esito.intentSender)
            }
        } finally {
            apk.delete()
        }
    }
}

/** Riceve l'esito dall'installatore di sistema (e gli fa chiedere conferma all'utente). */
class EsitoInstallazione : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val stato = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val conferma = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_INTENT)
                conferma?.let { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            PackageInstaller.STATUS_FAILURE_ABORTED -> avviso(context, "Aggiornamento annullato")
            PackageInstaller.STATUS_FAILURE_STORAGE -> avviso(context, "Spazio insufficiente per l'aggiornamento")
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                avviso(context, "Aggiornamento non compatibile (firma diversa)")
            else -> avviso(context, "Aggiornamento non riuscito (codice $stato)")
        }
    }

    private fun avviso(ctx: Context, s: String) = Toast.makeText(ctx, s, Toast.LENGTH_LONG).show()
}
