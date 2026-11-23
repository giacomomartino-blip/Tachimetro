package it.giacomo.tachimetro.media

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import it.giacomo.tachimetro.Hub
import it.giacomo.tachimetro.NowPlaying
import it.giacomo.tachimetro.notify.NotificationReader

/**
 * Legge cosa stanno suonando Spotify, YouTube Music, Amazon Music e le app video, e ne comanda la riproduzione.
 * Funziona solo dopo aver concesso a Tachimetro l'accesso alle notifiche.
 */
object MediaSessions {
    private var manager: MediaSessionManager? = null
    private var controller: MediaController? = null
    private val main = Handler(Looper.getMainLooper())

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onSessionDestroyed() {
            controller = null
            publish()
        }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        pick(list ?: emptyList())
    }

    fun start(context: Context) {
        if (manager != null) return
        val m = context.getSystemService(MediaSessionManager::class.java) ?: return
        val cn = ComponentName(context, NotificationReader::class.java)
        try {
            m.addOnActiveSessionsChangedListener(sessionsListener, cn, main)
            manager = m
            pick(m.getActiveSessions(cn))
        } catch (e: SecurityException) {
            // accesso alle notifiche non ancora concesso
        }
    }

    fun stop() {
        manager?.removeOnActiveSessionsChangedListener(sessionsListener)
        manager = null
        controller?.unregisterCallback(callback)
        controller = null
    }

    private fun pick(list: List<MediaController>) {
        val own = "it.giacomo.tachimetro"
        val candidates = list.filter { it.packageName != own }
        val playing = candidates.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val chosen = playing ?: candidates.firstOrNull()
        if (chosen?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(callback)
            controller = chosen
            chosen?.registerCallback(callback, main)
        }
        publish()
    }

    private fun publish() {
        val c = controller
        if (c == null) {
            Hub.nowPlaying.value = null
            return
        }
        val md = c.metadata
        val title = md?.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty()
        val artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST).orEmpty()
        val label = Apps.MUSIC.values.firstOrNull { it.second == c.packageName }?.first ?: c.packageName.substringAfterLast('.')
        Hub.nowPlaying.value = NowPlaying(
            title = title.ifBlank { "In riproduzione" },
            artist = artist,
            sourceLabel = label,
            packageName = c.packageName,
            isPlaying = c.playbackState?.state == PlaybackState.STATE_PLAYING,
        )
    }

    fun hasSession(): Boolean = controller != null

    fun playPause(context: Context) {
        val c = controller
        if (c != null) {
            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
        } else {
            mediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
    }

    fun play(context: Context) {
        controller?.transportControls?.play() ?: mediaKey(context, KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    fun pause(context: Context) {
        controller?.transportControls?.pause() ?: mediaKey(context, KeyEvent.KEYCODE_MEDIA_PAUSE)
    }

    fun next(context: Context) {
        controller?.transportControls?.skipToNext() ?: mediaKey(context, KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previous(context: Context) {
        controller?.transportControls?.skipToPrevious() ?: mediaKey(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    private fun mediaKey(context: Context, code: Int) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    fun volume(context: Context, up: Boolean) {
        val am = context.getSystemService(AudioManager::class.java) ?: return
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI,
        )
    }
}
