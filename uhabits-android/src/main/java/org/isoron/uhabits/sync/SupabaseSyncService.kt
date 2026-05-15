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
import org.isoron.uhabits.core.commands.ArchiveHabitsCommand
import org.isoron.uhabits.core.commands.ChangeHabitColorCommand
import org.isoron.uhabits.core.commands.Command
import org.isoron.uhabits.core.commands.CommandRunner
import org.isoron.uhabits.core.commands.CreateHabitCommand
import org.isoron.uhabits.core.commands.CreateRepetitionCommand
import org.isoron.uhabits.core.commands.DeleteHabitsCommand
import org.isoron.uhabits.core.commands.EditHabitCommand
import org.isoron.uhabits.core.commands.UnarchiveHabitsCommand
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
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isListening = false

    fun startListening() {
        if (!supabaseClient.isConfigured) {
            Log.d(TAG, "Supabase not configured, skipping sync")
            return
        }
        if (isListening) return
        isListening = true
        commandRunner.addListener(this)
        Log.d(TAG, "Started listening for commands")
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        commandRunner.removeListener(this)
        Log.d(TAG, "Stopped listening for commands")
    }

    override fun onCommandFinished(command: Command) {
        scope.launch {
            try {
                syncCommand(command)
            } catch (e: Exception) {
                Log.w(TAG, "Sync failed for command: ${command::class.simpleName}", e)
            }
        }
    }

    private suspend fun syncCommand(command: Command) {
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
                    supabaseClient.upsertHabit(habit)
                }
            }

            is CreateHabitCommand -> {
                // After run(), model may not have id yet; sync all to be safe
                syncAllHabits()
            }

            is EditHabitCommand -> {
                val habit = habitList.getById(command.habitId)
                if (habit != null) {
                    supabaseClient.upsertHabit(habit)
                }
            }

            is ArchiveHabitsCommand -> {
                syncAllHabits()
            }

            is UnarchiveHabitsCommand -> {
                syncAllHabits()
            }

            is ChangeHabitColorCommand -> {
                syncAllHabits()
            }

            is DeleteHabitsCommand -> {
                // Full sync to reflect deletions
                syncAllHabits()
            }
        }
    }

    fun fullSync() {
        if (!supabaseClient.isConfigured) return
        scope.launch {
            try {
                syncAllHabits()
                syncRecentEntries()
                Log.d(TAG, "Full sync completed")
            } catch (e: Exception) {
                Log.w(TAG, "Full sync failed", e)
            }
        }
    }

    private suspend fun syncAllHabits() {
        val habits = mutableListOf<org.isoron.uhabits.core.models.Habit>()
        for (h in habitList) {
            habits.add(h)
        }
        supabaseClient.upsertHabits(habits)
    }

    private suspend fun syncRecentEntries() {
        val entries = mutableListOf<Map<String, Any?>>()
        for (habit in habitList) {
            val habitId = habit.id ?: continue
            val known = habit.originalEntries.getKnown()
            // Sync last 90 days of entries
            val recentEntries = known.take(90)
            for (entry in recentEntries) {
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
        // Batch in chunks of 100
        entries.chunked(100).forEach { chunk ->
            supabaseClient.upsertEntries(chunk)
        }
    }
}
