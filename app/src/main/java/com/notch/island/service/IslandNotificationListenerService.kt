package com.notch.island.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.notch.island.core.IslandEventManager
import com.notch.island.model.MediaTrack

/**
 * Intercepts active system notifications and tracks real-time MediaSession
 * playback (Spotify, YouTube Music, Apple Music, Podcasts, etc.).
 */
class IslandNotificationListenerService : NotificationListenerService() {

    private var mediaSessionManager: MediaSessionManager? = null
    private var activeMediaController: MediaController? = null

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateMediaTrackFromController(activeMediaController)
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateMediaTrackFromController(activeMediaController)
        }
    }

    private val sessionsChangedListener = MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
        val newController = controllers?.firstOrNull { controller ->
            val state = controller.playbackState?.state
            state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_PAUSED
        } ?: controllers?.firstOrNull()

        bindMediaController(newController)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        initMediaSessionTracking()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        unbindMediaController()
    }

    private fun initMediaSessionTracking() {
        try {
            mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val componentName = ComponentName(this, IslandNotificationListenerService::class.java)

            mediaSessionManager?.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName)
            val controllers = mediaSessionManager?.getActiveSessions(componentName)
            bindMediaController(controllers?.firstOrNull())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun bindMediaController(controller: MediaController?) {
        if (activeMediaController?.sessionToken == controller?.sessionToken) {
            updateMediaTrackFromController(controller)
            return
        }

        activeMediaController?.unregisterCallback(mediaControllerCallback)
        activeMediaController = controller
        activeMediaController?.registerCallback(mediaControllerCallback)
        updateMediaTrackFromController(controller)
    }

    private fun unbindMediaController() {
        activeMediaController?.unregisterCallback(mediaControllerCallback)
        activeMediaController = null
        IslandEventManager.updateMediaTrack(null)
    }

    private fun updateMediaTrackFromController(controller: MediaController?) {
        if (controller == null) {
            IslandEventManager.updateMediaTrack(null)
            return
        }

        val metadata = controller.metadata
        val playbackState = controller.playbackState

        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
            ?: "Playing Audio"

        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: metadata?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: controller.packageName.substringAfterLast('.')

        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING
        val duration = metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        val position = playbackState?.position ?: 0L
        val albumArt = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_ART)

        val track = MediaTrack(
            title = title,
            artist = artist,
            packageName = controller.packageName,
            isPlaying = isPlaying,
            durationMs = duration,
            positionMs = position,
            albumArt = albumArt,
            playAction = { controller.transportControls.play() },
            pauseAction = { controller.transportControls.pause() },
            nextAction = { controller.transportControls.skipToNext() },
            prevAction = { controller.transportControls.skipToPrevious() },
            seekAction = { pos -> controller.transportControls.seekTo(pos) }
        )

        IslandEventManager.updateMediaTrack(track)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        // Ignore our own notification or ongoing background service notifications
        if (sbn.packageName == packageName || sbn.isOngoing) return

        val extras = sbn.notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        if (!title.isNullOrBlank() || !text.isNullOrBlank()) {
            val appLabel = try {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(sbn.packageName, 0)
                ).toString()
            } catch (e: Exception) {
                sbn.packageName.substringAfterLast('.')
            }

            // Extract quick reply action if present
            var quickReplyAction: Notification.Action? = null
            var remoteInput: android.app.RemoteInput? = null
            val actions = sbn.notification.actions
            if (actions != null) {
                for (action in actions) {
                    val inputs = action.remoteInputs
                    if (!inputs.isNullOrEmpty()) {
                        quickReplyAction = action
                        remoteInput = inputs.firstOrNull()
                        break
                    }
                }
            }

            val replyPayload = if (quickReplyAction != null && remoteInput != null) {
                com.notch.island.model.QuickReplyPayload(
                    notificationKey = sbn.key,
                    sender = title ?: appLabel,
                    message = text ?: "",
                    pendingIntent = quickReplyAction.actionIntent,
                    remoteInput = remoteInput
                )
            } else null

            IslandEventManager.postNotificationAlert(
                title = title ?: appLabel,
                subtitle = text,
                trailingText = appLabel,
                replyPayload = replyPayload,
                durationMs = 4500L
            )
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }

    override fun onDestroy() {
        super.onDestroy()
        unbindMediaController()
    }
}
