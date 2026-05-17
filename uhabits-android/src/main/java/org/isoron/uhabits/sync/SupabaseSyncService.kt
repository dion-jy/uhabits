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

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import org.isoron.uhabits.core.AppScope
import org.isoron.platform.time.LocalDate
import org.isoron.uhabits.core.commands.Command
import org.isoron.uhabits.core.commands.CommandRunner
import org.isoron.uhabits.core.commands.CreateRepetitionCommand
import org.isoron.uhabits.core.commands.DeleteHabitsCommand
import org.isoron.uhabits.core.commands.EditHabitCommand
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList

@Inject
@AppScope
class SupabaseSyncService(
    private val commandRunner: CommandRunner,
    private val habitList: HabitList,
    private val supabaseClient: SupabaseClient
) : CommandRunner.Listener {

    companion object {
        private const val TAG = "SupabaseSyncService"
        private const val FULL_SYNC_INTERVAL_MS = 15 * 60 * 1000L // 15 minutes
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isListening = false
    private var lastFullSyncMs = 0L
    @Volatile private var isPulling = false

    fun startListening() {
        if (!supabaseClient.isConfigured) {
            Log.w(TAG, "Supabase not configured, skipping")
            return
        }
        if (isListening) return
        isListening = true
        commandRunner.addListener(this)
        Log.i(TAG, "Listening for commands")
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        commandRunner.removeListener(this)
    }

    override fun onCommandFinished(command: Command) {
        if (isPulling) return
        scope.launch {
            try {
                when (command) {
                    is CreateRepetitionCommand -> {
                        val habit = command.habit
                        if (habit.id != null) {
                            supabaseClient.upsertEntry(
                                habitId = habit.id!!,
                                timestamp = command.date.unixTime,
                                value = command.value,
                                notes = command.notes
                            )
                        }
                    }
                    is DeleteHabitsCommand -> {
                        val ids = command.selected.mapNotNull { it.id }
                        supabaseClient.deleteHabits(ids)
                    }
                    is EditHabitCommand -> {
                        val habit = habitList.getById(command.habitId)
                        if (habit != null) {
                            supabaseClient.upsertHabit(habit)
                        }
                    }
                    else -> syncAllHabits()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed: ${command::class.simpleName}", e)
            }
        }
    }

    fun fullSync() {
        if (!supabaseClient.isConfigured) return
        // Pull agent entries every time (no throttle)
        scope.launch {
            try {
                pullAgentEntries()
            } catch (e: Exception) {
                Log.w(TAG, "Pull failed", e)
            }
        }
        // Push is throttled to avoid redundant uploads
        val now = System.currentTimeMillis()
        if (now - lastFullSyncMs < FULL_SYNC_INTERVAL_MS) return
        lastFullSyncMs = now
        scope.launch {
            try {
                syncAllHabits()
                syncRecentEntries()
            } catch (e: Exception) {
                Log.w(TAG, "Full sync failed", e)
            }
        }
    }

    private suspend fun syncAllHabits() {
        val habits = habitList.toList()
        supabaseClient.upsertHabits(habits)
    }

    private suspend fun syncRecentEntries() {
        val entries = mutableListOf<Map<String, Any?>>()
        for (habit in habitList) {
            val habitId = habit.id ?: continue
            for (entry in habit.originalEntries.getKnown().take(90)) {
                entries.add(
                    mapOf(
                        "habit_id" to habitId,
                        "timestamp" to entry.date.unixTime,
                        "value" to entry.value,
                        "notes" to entry.notes
                    )
                )
            }
        }
        entries.chunked(100).forEach { supabaseClient.upsertEntries(it) }
    }

    private suspend fun pullAgentEntries() {
        val agentEntries = supabaseClient.fetchAgentEntries()
        if (agentEntries.isEmpty()) return
        isPulling = true
        try {
            for (entry in agentEntries) {
                val habit = habitList.getById(entry.habit_id) ?: continue
                val date = LocalDate.fromUnixTime(entry.timestamp)
                habit.originalEntries.add(Entry(date, entry.value, entry.notes))
                habit.recompute()
            }
            habitList.resort()
            supabaseClient.markEntriesPulled(agentEntries.map { it.id })
        } finally {
            isPulling = false
        }
    }

    private fun HabitList.toList(): List<Habit> {
        val result = mutableListOf<Habit>()
        for (h in this) result.add(h)
        return result
    }
}
