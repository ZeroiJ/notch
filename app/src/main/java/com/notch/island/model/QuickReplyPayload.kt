package com.notch.island.model

import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Encapsulates inline quick-reply capabilities extracted from messaging notifications.
 */
data class QuickReplyPayload(
    val notificationKey: String,
    val sender: String,
    val message: String,
    val pendingIntent: PendingIntent?,
    val remoteInput: RemoteInput?
) {
    fun sendReply(context: Context, replyText: String): Boolean {
        if (pendingIntent == null || remoteInput == null) return false

        try {
            val intent = Intent().addFlags(Intent.FLAG_RECEIVER_FOREGROUND)
            val bundle = Bundle().apply {
                putCharSequence(remoteInput.resultKey, replyText)
            }
            RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)
            pendingIntent.send(context, 0, intent)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
