package it.giacomo.tachimetro.media

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore

data class InstalledApp(val label: String, val packageName: String)

object Apps {
    val MUSIC = mapOf(
        "spotify" to ("Spotify" to "com.spotify.music"),
        "ytmusic" to ("YouTube Music" to "com.google.android.apps.youtube.music"),
        "amazon" to ("Amazon Music" to "com.amazon.mp3"),
    )

    val VIDEO = listOf("DAZN", "Netflix", "Prime Video", "HBO Max", "NOW", "RaiPlay")

    fun installed(context: Context): List<InstalledApp> {
        val pm = context.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .map { InstalledApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun label(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        pkg.substringAfterLast('.')
    }

    fun isInstalled(context: Context, pkg: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(pkg) != null

    /** Cerca un'app per nome come la si pronuncia: «apri WhatsApp», «apri prime video». */
    fun findByName(context: Context, spoken: String): InstalledApp? {
        val s = norm(spoken)
        if (s.isBlank()) return null
        val all = installed(context)
        return all.firstOrNull { norm(it.label) == s }
            ?: all.firstOrNull { norm(it.label).startsWith(s) }
            ?: all.firstOrNull { norm(it.label).contains(s) }
            ?: all.firstOrNull { s.contains(norm(it.label)) && norm(it.label).length >= 3 }
    }

    private fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9àèéìòù ]"), "").replace(" ", "")

    fun launch(context: Context, pkg: String): Boolean {
        val i = context.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
        return true
    }

    /** Fa partire un brano, un artista o una playlist nell'app di musica scelta. */
    fun playFromSearch(context: Context, pkg: String, query: String): Boolean {
        val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .setPackage(pkg)
            .putExtra(SearchManager.QUERY, query)
            .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(i)
            true
        } catch (e: Exception) {
            launch(context, pkg)
        }
    }

    fun openUrl(context: Context, url: String): Boolean = try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        false
    }

    /** Ricerca di un titolo nelle app video che la consentono. */
    fun searchVideo(context: Context, appName: String, title: String): Boolean {
        val n = appName.lowercase()
        return when {
            n.contains("netflix") -> openUrl(context, "https://www.netflix.com/search?q=" + Uri.encode(title))
            n.contains("prime") -> openUrl(context, "https://www.primevideo.com/search/ref=atv_sr_sug?phrase=" + Uri.encode(title))
            else -> findByName(context, appName)?.let { launch(context, it.packageName) } ?: false
        }
    }
}
