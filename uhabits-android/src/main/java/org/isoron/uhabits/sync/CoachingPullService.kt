/*
 * Copyright (C) 2026 Junyeob Baek
 *
 * This file is part of Loop Habit Tracker.
 *
 * Loop Habit Tracker is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Loop Habit Tracker is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.isoron.uhabits.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import org.isoron.uhabits.R
import org.isoron.uhabits.core.AppScope
import org.isoron.uhabits.inject.AppContext

@Inject
@AppScope
class CoachingPullService(
    @AppContext private val context: Context,
    private val supabaseClient: SupabaseClient
) {
    companion object {
        private const val TAG = "CoachingPullService"
        private const val CHANNEL_ID = "coaching"
        private const val CHANNEL_NAME = "AI Coaching"
        private const val NOTIFICATION_BASE_ID = 90000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Coaching messages from your AI habit coach"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    fun pullAndNotify() {
        if (!supabaseClient.isConfigured) return
        scope.launch {
            try {
                val messages = supabaseClient.fetchUnreadCoaching()
                if (messages.isEmpty()) {
                    Log.d(TAG, "No unread coaching messages")
                    return@launch
                }

                Log.d(TAG, "Received ${messages.size} coaching messages")
                val readIds = mutableListOf<Long>()

                messages.forEachIndexed { index, msg ->
                    showNotification(msg, index)
                    readIds.add(msg.id)
                }

                supabaseClient.markCoachingRead(readIds)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to pull coaching messages", e)
            }
        }
    }

    private fun showNotification(message: CoachingMessage, index: Int) {
        val title = when (message.type) {
            "celebration" -> "Great job!"
            "insight" -> "Habit Insight"
            "nudge" -> "Habit Reminder"
            else -> "Coaching"
        }

        val icon = when (message.type) {
            "celebration" -> R.drawable.ic_action_check
            else -> R.drawable.ic_action_check
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(message.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.message))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_BASE_ID + index, notification)
    }
}
