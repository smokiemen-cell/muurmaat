package nl.muurmaat.ar

import android.content.Context
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ErrorLog {
    private const val fileName = "voegmaatje-errors.log"

    fun write(context: Context, event: String, error: Throwable? = null) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
        val detail = error?.let { " ${it::class.java.simpleName}: ${it.message.orEmpty()}" }.orEmpty()
        val line = "$timestamp | $event$detail\n"
        try {
            context.openFileOutput(fileName, Context.MODE_APPEND).use { it.write(line.toByteArray()) }
        } catch (writeError: Exception) {
            Log.e("Voegmaatje", "Kon foutlog niet schrijven", writeError)
        }
        Log.e("Voegmaatje", line.trim(), error)
    }
}
