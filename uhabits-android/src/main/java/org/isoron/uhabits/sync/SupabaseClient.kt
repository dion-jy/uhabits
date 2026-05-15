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
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.features.json.JacksonSerializer
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.contentType
import me.tatarka.inject.annotations.Inject
import org.isoron.uhabits.core.AppScope
import org.isoron.uhabits.core.models.Habit
import java.time.Instant

@Inject
@AppScope
class SupabaseClient(
    private val deviceIdManager: DeviceIdManager
) {
    companion object {
        private const val TAG = "SupabaseClient"
        const val SUPABASE_URL = "https://YOUR_PROJECT.supabase.co"
        const val SUPABASE_ANON_KEY = "YOUR_ANON_KEY"
    }

    private val mapper = ObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    private val client = HttpClient(Android) {
        install(JsonFeature) {
            serializer = JacksonSerializer()
        }
        engine {
            connectTimeout = 10_000
            socketTimeout = 15_000
        }
    }

    val isConfigured: Boolean
        get() = SUPABASE_URL != "https://YOUR_PROJECT.supabase.co"

    private val deviceId: String
        get() = deviceIdManager.deviceId

    private val restUrl: String
        get() = "$SUPABASE_URL/rest/v1"

    private suspend fun restPost(
        table: String,
        body: Any,
        upsert: Boolean = false
    ): HttpResponse {
        return client.post("$restUrl/$table") {
            header("apikey", SUPABASE_ANON_KEY)
            header("Authorization", "Bearer $SUPABASE_ANON_KEY")
            header("x-device-id", deviceId)
            if (upsert) header("Prefer", "resolution=merge-duplicates")
            contentType(ContentType.Application.Json)
            this.body = body
        }
    }

    private suspend fun restPatch(
        path: String,
        body: Any
    ): HttpResponse {
        return client.post("$restUrl/$path") {
            header("apikey", SUPABASE_ANON_KEY)
            header("Authorization", "Bearer $SUPABASE_ANON_KEY")
            header("x-device-id", deviceId)
            header("X-HTTP-Method-Override", "PATCH")
            contentType(ContentType.Application.Json)
            this.body = body
        }
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

    suspend fun upsertHabits(habits: List<Habit>) {
        if (habits.isEmpty()) return
        try {
            restPost("habits", habits.map(::habitToMap), upsert = true)
            Log.d(TAG, "Upserted ${habits.size} habits")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to upsert habits", e)
        }
    }

    suspend fun upsertEntry(habitId: Long, timestamp: Long, value: Int, notes: String) {
        try {
            val data = mapOf(
                "device_id" to deviceId,
                "habit_id" to habitId,
                "timestamp" to timestamp,
                "value" to value,
                "notes" to notes
            )
            restPost("entries", data, upsert = true)
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
            val response: String = client.get("$restUrl/coaching") {
                header("apikey", SUPABASE_ANON_KEY)
                header("Authorization", "Bearer $SUPABASE_ANON_KEY")
                header("x-device-id", deviceId)
                url {
                    parameters.append("read_at", "is.null")
                    parameters.append("order", "created_at.desc")
                    parameters.append("limit", "20")
                }
            }
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

data class CoachingMessage(
    val id: Long = 0,
    val habit_uuid: String? = null,
    val message: String = "",
    val type: String = "nudge",
    val created_at: String = ""
)
