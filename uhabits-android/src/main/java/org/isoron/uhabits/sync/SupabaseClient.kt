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
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import me.tatarka.inject.annotations.Inject
import org.isoron.uhabits.BuildConfig
import org.isoron.uhabits.core.AppScope
import org.isoron.uhabits.core.models.Habit
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant

@Inject
@AppScope
class SupabaseClient(
    private val deviceIdManager: DeviceIdManager,
    private val authManager: SupabaseAuthManager
) {
    companion object {
        private const val TAG = "SupabaseClient"
        val SUPABASE_URL: String = BuildConfig.SUPABASE_URL
        val SUPABASE_ANON_KEY: String = BuildConfig.SUPABASE_ANON_KEY
    }

    private val mapper = ObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    val isConfigured: Boolean
        get() = SUPABASE_URL != "https://YOUR_PROJECT.supabase.co"

    val currentDeviceId: String
        get() = deviceIdManager.deviceId

    val hasAuthToken: Boolean
        get() = authManager.isSignedIn

    private val deviceId: String
        get() = deviceIdManager.deviceId

    private val restUrl: String
        get() = "$SUPABASE_URL/rest/v1"

    private fun openConnection(url: String, method: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.setRequestProperty("apikey", SUPABASE_ANON_KEY)
        val token = authManager.refreshTokenIfNeeded()
        if (token != null) {
            conn.setRequestProperty("Authorization", "Bearer $token")
        } else {
            conn.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            conn.setRequestProperty("x-device-id", deviceId)
        }
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.requestMethod = method
        return conn
    }

    private fun restPost(table: String, body: Any, upsert: Boolean = false): String {
        val conn = openConnection("$restUrl/$table", "POST")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        if (upsert) conn.setRequestProperty("Prefer", "resolution=merge-duplicates")
        val json = mapper.writeValueAsString(body)
        OutputStreamWriter(conn.outputStream).use { it.write(json) }
        val code = conn.responseCode
        val response = if (code in 200..299) {
            BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        } else {
            val err = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } } ?: ""
            Log.w(TAG, "POST $table FAILED $code: $err")
            err
        }
        conn.disconnect()
        return response
    }

    private fun restPatch(path: String, body: Any): String {
        val conn = openConnection("$restUrl/$path", "PATCH")
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        OutputStreamWriter(conn.outputStream).use { it.write(mapper.writeValueAsString(body)) }
        val code = conn.responseCode
        val response = if (code in 200..299) {
            BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        } else ""
        conn.disconnect()
        return response
    }

    private fun restDelete(path: String) {
        val conn = openConnection("$restUrl/$path", "DELETE")
        conn.responseCode
        conn.disconnect()
    }

    private fun restGet(path: String): String {
        val conn = openConnection("$restUrl/$path", "GET")
        val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        conn.disconnect()
        return response
    }

    private fun habitToMap(habit: Habit): Map<String, Any?> = mapOf(
        "id" to habit.id,
        "uuid" to habit.uuid,
        "device_id" to deviceId,
        "name" to habit.name,
        "description" to habit.description,
        "question" to habit.question,
        "freq_num" to habit.frequency.numerator,
        "freq_den" to habit.frequency.denominator,
        "color" to habit.color.paletteIndex,
        "position" to habit.position,
        "type" to habit.type.value,
        "target_value" to habit.targetValue,
        "target_type" to habit.targetType.value,
        "unit" to habit.unit,
        "archived" to if (habit.isArchived) 1 else 0
    )

    suspend fun upsertHabit(habit: Habit) = upsertHabits(listOf(habit))

    suspend fun upsertHabits(habits: List<Habit>) {
        if (habits.isEmpty()) return
        try {
            restPost("habits", habits.map(::habitToMap), upsert = true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to upsert habits", e)
        }
    }

    suspend fun upsertEntry(habitId: Long, timestamp: Long, value: Int, notes: String) {
        try {
            restPost("entries", mapOf(
                "device_id" to deviceId,
                "habit_id" to habitId,
                "timestamp" to timestamp,
                "value" to value,
                "notes" to notes
            ), upsert = true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to upsert entry", e)
        }
    }

    suspend fun upsertEntries(entries: List<Map<String, Any?>>) {
        if (entries.isEmpty()) return
        try {
            restPost("entries", entries.map { it + ("device_id" to deviceId) }, upsert = true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to upsert entries batch", e)
        }
    }

    suspend fun fetchUnreadCoaching(): List<CoachingMessage> {
        return try {
            val response = restGet("coaching?read_at=is.null&order=created_at.desc&limit=20")
            mapper.readValue(
                response,
                mapper.typeFactory.constructCollectionType(
                    List::class.java,
                    CoachingMessage::class.java
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch coaching", e)
            emptyList()
        }
    }

    suspend fun deleteHabits(habitIds: List<Long>) {
        if (habitIds.isEmpty()) return
        try {
            val idFilter = habitIds.joinToString(",")
            restDelete("habits?id=in.($idFilter)")
            restDelete("entries?habit_id=in.($idFilter)")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete habits", e)
        }
    }

    fun writeSyncLog(message: String) {
        try {
            // Use anon key directly (bypass auth) to ensure log always writes
            val conn = URL("$restUrl/coaching").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer $SUPABASE_ANON_KEY")
            conn.setRequestProperty("x-device-id", deviceId)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            val body = mapper.writeValueAsString(mapOf(
                "device_id" to deviceId,
                "message" to message,
                "type" to "sync_log"
            ))
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            conn.responseCode
            conn.disconnect()
        } catch (_: Exception) {}
    }

    suspend fun sendHeartbeat(habitCount: Int, entryCount: Int, lastEntryMs: Long) {
        try {
            restPost("device_heartbeat", mapOf(
                "device_id" to deviceId,
                "habit_count" to habitCount,
                "entry_count" to entryCount,
                "last_entry_ms" to lastEntryMs,
                "app_version" to org.isoron.uhabits.BuildConfig.VERSION_NAME,
                "last_seen_at" to Instant.now().toString()
            ), upsert = true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send heartbeat", e)
        }
    }

    suspend fun fetchAllHabits(): List<Map<String, Any>> {
        return try {
            val response = restGet("habits?device_id=eq.$deviceId&order=position.asc")
            mapper.readValue(
                response,
                mapper.typeFactory.constructCollectionType(
                    List::class.java,
                    Map::class.java
                )
            ) as List<Map<String, Any>>
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch habits", e)
            emptyList()
        }
    }

    suspend fun fetchAllEntries(): List<AgentEntry> {
        return try {
            val response = restGet("entries?device_id=eq.$deviceId&order=timestamp.desc&limit=5000")
            mapper.readValue(
                response,
                mapper.typeFactory.constructCollectionType(
                    List::class.java,
                    AgentEntry::class.java
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch all entries", e)
            emptyList()
        }
    }

    suspend fun fetchAgentEntries(): List<AgentEntry> {
        return try {
            val response = restGet("entries?source=eq.agent&order=synced_at.desc&limit=100")
            mapper.readValue(
                response,
                mapper.typeFactory.constructCollectionType(
                    List::class.java,
                    AgentEntry::class.java
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch agent entries", e)
            emptyList()
        }
    }

    suspend fun markEntriesPulled(ids: List<Long>) {
        if (ids.isEmpty()) return
        try {
            val idFilter = ids.joinToString(",")
            restPatch("entries?id=in.($idFilter)", mapOf("source" to "pulled"))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to mark entries pulled", e)
        }
    }

    suspend fun markCoachingRead(ids: List<Long>) {
        if (ids.isEmpty()) return
        try {
            val idFilter = ids.joinToString(",")
            restPatch(
                "coaching?id=in.($idFilter)",
                mapOf("read_at" to Instant.now().toString())
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to mark coaching read", e)
        }
    }
}

data class AgentEntry(
    val id: Long = 0,
    val habit_id: Long = 0,
    val timestamp: Long = 0,
    val value: Int = 0,
    val notes: String = ""
)

data class CoachingMessage(
    val id: Long = 0,
    val habit_uuid: String? = null,
    val message: String = "",
    val type: String = "nudge",
    val created_at: String = "",
    val metadata: Map<String, Any>? = null
)
