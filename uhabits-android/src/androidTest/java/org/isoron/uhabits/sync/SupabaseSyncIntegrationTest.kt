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

import androidx.test.filters.LargeTest
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.isoron.uhabits.BaseAndroidTest
import org.junit.After
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * Integration test that verifies the full Supabase sync pipeline:
 * 1. Creates a habit locally
 * 2. Triggers sync
 * 3. Verifies data appears in Supabase via HTTP
 * 4. Cleans up test data
 */
@LargeTest
class SupabaseSyncIntegrationTest : BaseAndroidTest() {

    private val serviceRoleKey =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InZ6aGtreHF3cXRxYWpudXRwam10Iiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc3ODg1MjYyNSwiZXhwIjoyMDk0NDI4NjI1fQ.S09LTGtR9DQV7sJZg9jhDb5zwTRBNts9xpv6olF1bwY"
    private val supabaseUrl = SupabaseClient.SUPABASE_URL
    private lateinit var testDeviceId: String

    @Test
    fun testFullSyncPipeline() = runBlocking {
        // Get the device ID that the sync service will use
        val deviceIdManager = DeviceIdManager(targetContext)
        testDeviceId = deviceIdManager.deviceId

        // Create a habit
        val habit = modelFactory.buildHabit()
        habit.name = "SyncTest_${System.currentTimeMillis()}"
        habit.description = "Integration test habit"
        habitList.add(habit)
        habit.recompute()

        // Get sync service and trigger full sync
        val syncService = appComponent.supabaseSyncService
        syncService.startListening()
        syncService.fullSync()

        // Wait for async sync to complete
        delay(5000)

        // Verify data in Supabase using service_role key (bypasses RLS)
        val habits = supabaseGet("habits?name=eq.${habit.name}&device_id=eq.$testDeviceId")
        assertTrue("Habit should appear in Supabase", habits.contains(habit.name))

        // Cleanup
        syncService.stopListening()
    }

    @After
    fun cleanupSupabase() {
        if (!::testDeviceId.isInitialized) return
        try {
            supabaseDelete("habits?device_id=eq.$testDeviceId")
            supabaseDelete("entries?device_id=eq.$testDeviceId")
        } catch (e: Exception) {
            // Best effort cleanup
        }
    }

    private fun supabaseGet(path: String): String {
        val url = URL("$supabaseUrl/rest/v1/$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("apikey", serviceRoleKey)
        conn.setRequestProperty("Authorization", "Bearer $serviceRoleKey")
        conn.requestMethod = "GET"
        val reader = BufferedReader(InputStreamReader(conn.inputStream))
        val response = reader.readText()
        reader.close()
        return response
    }

    private fun supabaseDelete(path: String) {
        val url = URL("$supabaseUrl/rest/v1/$path")
        val conn = url.openConnection() as HttpURLConnection
        conn.setRequestProperty("apikey", serviceRoleKey)
        conn.setRequestProperty("Authorization", "Bearer $serviceRoleKey")
        conn.requestMethod = "DELETE"
        conn.responseCode // execute
        conn.disconnect()
    }
}
