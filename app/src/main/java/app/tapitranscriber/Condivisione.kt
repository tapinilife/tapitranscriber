package app.tapitranscriber

import android.app.Activity
import android.content.ClipData
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.widget.Toast
import java.io.File

/** Condivide l'APK installato (con il link all'ultima versione) tramite il menu Condividi di Android. */
object CondividiApp {
    const val AUTORITA = "app.tapitranscriber.condividi"

    fun cartella(ctx: Context) = File(ctx.cacheDir, "condividi")

    fun condividi(a: Activity) {
        val esito = runCatching {
            val dir = cartella(a)
            dir.mkdirs()
            dir.listFiles()?.forEach { it.delete() } // via le copie vecchie
            val versione = a.packageManager.getPackageInfo(a.packageName, 0).versionName
            val apk = File(dir, "TapiTranscriber-$versione.apk")
            File(a.applicationInfo.sourceDir).copyTo(apk, overwrite = true)
            apk
        }
        val apk = esito.getOrElse {
            Toast.makeText(a, "Non riesco a preparare l'APK", Toast.LENGTH_LONG).show()
            return
        }
        val uri = Uri.parse("content://$AUTORITA/${Uri.encode(apk.name)}")
        val invio = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(
                Intent.EXTRA_TEXT,
                "TapiTranscriber: trascrive i vocali. Installa l'APK allegato; ultima versione sempre qui: ${Aggiornamenti.PAGINA}",
            )
            clipData = ClipData.newRawUri(apk.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        a.startActivity(Intent.createChooser(invio, "Condividi TapiTranscriber"))
    }
}

/** Fornisce in sola lettura i file della cartella "condividi" (al posto di FileProvider, che è AndroidX). */
class ProviderApk : ContentProvider() {
    override fun onCreate() = true

    private fun file(uri: Uri): File? {
        val ctx = context ?: return null
        val dir = CondividiApp.cartella(ctx).canonicalFile
        val nome = uri.lastPathSegment ?: return null
        val f = File(dir, nome).canonicalFile
        return f.takeIf { it.parentFile == dir && it.isFile }
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        if (mode != "r") throw SecurityException("solo lettura")
        val f = file(uri) ?: throw java.io.FileNotFoundException(uri.toString())
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "application/vnd.android.package-archive"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
        val f = file(uri) ?: return null
        val colonne = projection ?: arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
        val valori = colonne.map {
            when (it) {
                OpenableColumns.DISPLAY_NAME -> f.name
                OpenableColumns.SIZE -> f.length()
                else -> null
            }
        }
        return MatrixCursor(colonne, 1).apply { addRow(valori) }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
}
