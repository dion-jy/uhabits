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

import android.content.Context
import android.util.Base64
import android.util.Log
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import me.tatarka.inject.annotations.Inject
import org.isoron.uhabits.BuildConfig
import org.isoron.uhabits.core.AppScope
import org.isoron.uhabits.inject.AppContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

@Inject
@AppScope
class SupabaseAuthManager(
    @AppContext private val context: Context
) {
    companion object {
        private const val TAG = "SupabaseAuthManager"
        private const val PREFS_NAME = "supabase_auth"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val mapper = ObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    }

    val isSignedIn: Boolean
        get() = prefs.getString("access_token", null) != null

    val userEmail: String?
        get() = prefs.getString("user_email", null)

    val accessToken: String?
        get() = prefs.getString("access_token", null)

    @Synchronized
    fun refreshTokenIfNeeded(): String? {
        val token = prefs.getString("access_token", null) ?: return null
        val expiresAt = prefs.getLong("expires_at", 0)
        val now = System.currentTimeMillis() / 1000
        if (now < expiresAt - 60) return token
        // Token expired or about to expire, refresh
        val refreshToken = prefs.getString("refresh_token", null) ?: return null
        return try {
            val body = mapper.writeValueAsString(mapOf("refresh_token" to refreshToken))
            val url = "${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=refresh_token"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            if (conn.responseCode in 200..299) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                saveSession(response)
                prefs.getString("access_token", null)
            } else {
                Log.w(TAG, "Token refresh failed: ${conn.responseCode}")
                signOut()
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Token refresh error", e)
            null
        }
    }

    fun signInWithGoogle(googleIdToken: String): Result<String> {
        return try {
            val body = mapper.writeValueAsString(
                mapOf("provider" to "google", "id_token" to googleIdToken)
            )
            val url = "${BuildConfig.SUPABASE_URL}/auth/v1/token?grant_type=id_token"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            val code = conn.responseCode
            if (code in 200..299) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                saveSession(response)
                Result.success(userEmail ?: "signed in")
            } else {
                val err = BufferedReader(InputStreamReader(conn.errorStream)).use { it.readText() }
                Log.w(TAG, "Sign-in failed: $code $err")
                Result.failure(Exception("Sign-in failed ($code)"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Sign-in error", e)
            Result.failure(e)
        }
    }

    fun claimDeviceLink(token: String): Result<String> {
        val accessTk = refreshTokenIfNeeded()
            ?: return Result.failure(Exception("Not signed in"))
        return try {
            val body = mapper.writeValueAsString(mapOf("p_token" to token))
            val url = "${BuildConfig.SUPABASE_URL}/rest/v1/rpc/claim_device_link"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer $accessTk")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            val code = conn.responseCode
            if (code in 200..299) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                val result = mapper.readTree(response)
                if (result.get("ok")?.asBoolean() == true) {
                    Result.success("Linked: ${result.get("instance_id")?.asText()}")
                } else {
                    Result.failure(Exception(result.get("error")?.asText() ?: "unknown"))
                }
            } else {
                Result.failure(Exception("Failed ($code)"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun signOut() {
        prefs.edit().clear().apply()
    }

    private fun saveSession(json: String) {
        val tree = mapper.readTree(json)
        val accessToken = tree.get("access_token")?.asText() ?: return
        val refreshToken = tree.get("refresh_token")?.asText() ?: return
        val expiresIn = tree.get("expires_in")?.asLong() ?: 3600
        val email = tree.get("user")?.get("email")?.asText()
            ?: decodeEmailFromJwt(accessToken)
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putLong("expires_at", System.currentTimeMillis() / 1000 + expiresIn)
            .putString("user_email", email)
            .apply()
    }

    private fun decodeEmailFromJwt(jwt: String): String? {
        return try {
            val parts = jwt.split(".")
            if (parts.size < 2) return null
            val payload = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING))
            mapper.readTree(payload).get("email")?.asText()
        } catch (e: Exception) {
            null
        }
    }
}
