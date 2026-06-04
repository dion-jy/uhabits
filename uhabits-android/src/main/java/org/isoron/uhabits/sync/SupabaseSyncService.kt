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
import org.isoron.uhabits.core.commands.CreateHabitCommand
import org.isoron.uhabits.core.commands.CreateRepetitionCommand
import org.isoron.uhabits.core.commands.DeleteHabitsCommand
import org.isoron.uhabits.core.commands.EditHabitCommand
import org.isoron.uhabits.core.models.Entry
import org.isoron.uhabits.core.models.Frequency
import org.isoron.uhabits.core.models.Habit
import org.isoron.uhabits.core.models.HabitList
import org.isoron.uhabits.core.models.HabitType
import org.isoron.uhabits.core.models.ModelFactory
import org.isoron.uhabits.core.models.NumericalHabitType
import org.isoron.uhabits.core.models.PaletteColor

@Inject
@AppScope
class SupabaseSyncService(
    private val commandRunner: CommandRunner,
    private val habitList: HabitList,
    private val modelFactory: ModelFactory,
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

    fun forceFullSync() {
        if (!supabaseClient.isConfigured) return
        lastFullSyncMs = 0
        fullSync()
    }

    data class RestoreResult(
        val habitsCreated: Int,
        val entriesAdded: Int,
        val error: String?
    )

    /**
     * Pull the signed-in user's habits and entries back from the cloud into the
     * local database. Habits are deduplicated by uuid (the only identity that
     * survives reinstalls — the local id and device_id both change), so the
     * device_id-fragmented cloud rows collapse into one local habit each.
     * Existing local habits (matched by uuid) are left untouched; only missing
     * ones are created. Entries are remapped from their (device_id, old habit
     * id) back to the local habit via uuid and merged by date.
     */
    suspend fun restoreFromCloud(): RestoreResult {
        if (!supabaseClient.isConfigured) return RestoreResult(0, 0, "Not configured")
        val habitRows = supabaseClient.fetchUserHabits()
        if (habitRows.isEmpty()) {
            return RestoreResult(0, 0, "No cloud habits found (sign in first?)")
        }

        isPulling = true
        try {
            // (device_id|oldId) -> uuid, to remap entries back to habits.
            val keyToUuid = HashMap<String, String>()
            // One representative row per uuid (prefer non-archived).
            val repByUuid = LinkedHashMap<String, Map<String, Any?>>()
            for (row in habitRows) {
                val uuid = row["uuid"] as? String ?: continue
                val dev = row["device_id"]?.toString() ?: ""
                val oldId = row["id"]?.toString() ?: ""
                keyToUuid["$dev|$oldId"] = uuid
                val existing = repByUuid[uuid]
                if (existing == null || isBetterRep(row, existing)) repByUuid[uuid] = row
            }

            var created = 0
            for ((uuid, row) in repByUuid) {
                if (habitList.getByUUID(uuid) != null) continue
                val habit = modelFactory.buildHabit()
                habit.uuid = uuid
                habit.name = row["name"] as? String ?: ""
                habit.description = row["description"] as? String ?: ""
                habit.question = row["question"] as? String ?: ""
                habit.frequency = Frequency(intOf(row["freq_num"], 1), intOf(row["freq_den"], 1))
                habit.color = PaletteColor(intOf(row["color"], 8))
                habit.type = HabitType.fromInt(intOf(row["type"], 0))
                habit.targetValue = doubleOf(row["target_value"])
                habit.targetType = NumericalHabitType.fromInt(intOf(row["target_type"], 0))
                habit.unit = row["unit"] as? String ?: ""
                habit.position = intOf(row["position"], 0)
                habit.isArchived = intOf(row["archived"], 0) != 0
                CreateHabitCommand(modelFactory, habitList, habit).run()
                created++
            }

            // Entries: remap (device_id, old habit id) -> uuid -> local habit.
            val entryRows = supabaseClient.fetchUserEntries()
            var addedEntries = 0
            val touched = HashSet<Long>()
            for (er in entryRows) {
                val dev = er["device_id"]?.toString() ?: ""
                val oldHabitId = er["habit_id"]?.toString() ?: continue
                val uuid = keyToUuid["$dev|$oldHabitId"] ?: continue
                val habit = habitList.getByUUID(uuid) ?: continue
                val ts = longOf(er["timestamp"]) ?: continue
                val date = LocalDate.fromUnixTime(ts)
                val value = intOf(er["value"], 0)
                val notes = er["notes"] as? String ?: ""
                // Only write when the cloud copy differs from what's already
                // local — keeps repeated restores cheap, avoids clobbering
                // matching local entries, and makes the count net-of-changes.
                val existing = habit.originalEntries.get(date)
                if (existing.value != value || existing.notes != notes) {
                    habit.originalEntries.add(Entry(date, value, notes))
                    habit.id?.let { touched.add(it) }
                    addedEntries++
                }
            }
            for (id in touched) habitList.getById(id)?.recompute()
            habitList.resort()
            return RestoreResult(created, addedEntries, null)
        } catch (e: Exception) {
            Log.w(TAG, "Restore failed", e)
            return RestoreResult(0, 0, e.message ?: "unknown error")
        } finally {
            isPulling = false
        }
    }

    // Prefer a non-archived row; otherwise keep whichever we saw first.
    private fun isBetterRep(row: Map<String, Any?>, current: Map<String, Any?>): Boolean {
        val rowArchived = intOf(row["archived"], 0) != 0
        val curArchived = intOf(current["archived"], 0) != 0
        return curArchived && !rowArchived
    }

    private fun intOf(v: Any?, default: Int): Int = when (v) {
        is Number -> v.toInt()
        is String -> v.toIntOrNull() ?: default
        else -> default
    }

    private fun doubleOf(v: Any?): Double = when (v) {
        is Number -> v.toDouble()
        is String -> v.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }

    private fun longOf(v: Any?): Long? = when (v) {
        is Number -> v.toLong()
        is String -> v.toLongOrNull()
        else -> null
    }

    fun fullSync() {
        if (!supabaseClient.isConfigured) return
        // Pull + heartbeat every time (no throttle)
        scope.launch {
            try {
                pullAgentEntries()
                sendHeartbeat()
            } catch (e: Exception) {
                Log.w(TAG, "Pull/heartbeat failed", e)
            }
        }
        // Push is throttled
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

    private suspend fun sendHeartbeat() {
        var entryCount = 0
        var maxTs = 0L
        for (habit in habitList) {
            val known = habit.originalEntries.getKnown()
            entryCount += known.size
            for (e in known) {
                if (e.date.unixTime > maxTs) maxTs = e.date.unixTime
            }
        }
        supabaseClient.sendHeartbeat(habitList.size(), entryCount, maxTs)
    }

    private fun HabitList.toList(): List<Habit> {
        val result = mutableListOf<Habit>()
        for (h in this) result.add(h)
        return result
    }
}
