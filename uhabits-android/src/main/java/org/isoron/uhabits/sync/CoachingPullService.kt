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
        private const val NOTIFICATION_BASE_ID = 90000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager =
        context.getSystemService(NotificationManager::class.java)

    init {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "AI Coaching",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    fun pullAndNotify() {
        if (!supabaseClient.isConfigured) return
        scope.launch {
            try {
                val messages = supabaseClient.fetchUnreadCoaching()
                if (messages.isEmpty()) return@launch

                val readIds = mutableListOf<Long>()
                messages.forEachIndexed { index, msg ->
                    showNotification(msg, index)
                    readIds.add(msg.id)
                }
                supabaseClient.markCoachingRead(readIds)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to pull coaching", e)
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
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_action_check)
            .setContentTitle(title)
            .setContentText(message.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.message))
            .setAutoCancel(true)
            .build()
        notificationManager.notify(NOTIFICATION_BASE_ID + index, notification)
    }
}
