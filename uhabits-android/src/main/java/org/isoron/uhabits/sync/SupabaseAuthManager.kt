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

    /**
     * The signed-in user's id, but ONLY when a valid session token is
     * available. Returns null if not signed in or if the token refresh
     * failed (in which case requests fall back to anon + device_id, and
     * rows must NOT be stamped with a stale user_id). This keeps the
     * "row has user_id" invariant in lockstep with "request uses Bearer JWT".
     */
    fun activeUserId(): String? {
        return if (refreshTokenIfNeeded() != null) prefs.getString("user_id", null) else null
    }

    @Synchronized
    fun refreshTokenIfNeeded(): String? {
        val token = prefs.getString("access_token", null)
        val expiresAt = prefs.getLong("expires_at", 0)
        val now = System.currentTimeMillis() / 1000
        // Valid, non-expiring access token: use it as-is.
        if (token != null && now < expiresAt - 60) return token
        // Otherwise (expired, OR access_token previously cleared after a failed
        // refresh) try to recover the session from the refresh token. This lets
        // the app self-heal on the next request/app-open instead of getting
        // stuck in anonymous device-fallback mode until a full re-sign-in.
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
            val code = conn.responseCode
            if (code in 200..299) {
                val response = BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                saveSession(response)
                prefs.getString("access_token", null)
            } else {
                Log.w(TAG, "Token refresh failed: $code")
                // Clear access_token so we fall back to anon key + device_id
                // Keep refresh_token so we can retry on next app open
                prefs.edit().remove("access_token").remove("expires_at").apply()
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Token refresh error", e)
            prefs.edit().remove("access_token").remove("expires_at").apply()
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
                val err = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } } ?: ""
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

    /**
     * Adopt this device's pre-sign-in rows (user_id IS NULL) into the
     * authenticated user's account. Called once right after sign-in so that
     * data created while offline/anonymous becomes owned by the user and is
     * reachable through the user_access / agent_access RLS policies.
     */
    fun backfillUserId(deviceId: String): Result<String> {
        val accessTk = refreshTokenIfNeeded()
            ?: return Result.failure(Exception("Not signed in"))
        return try {
            val body = mapper.writeValueAsString(mapOf("p_device_id" to deviceId))
            val url = "${BuildConfig.SUPABASE_URL}/rest/v1/rpc/backfill_user_id"
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
                Result.success(response)
            } else {
                val err = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } } ?: ""
                Log.w(TAG, "Backfill failed: $code $err")
                Result.failure(Exception("Failed ($code)"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Backfill error", e)
            Result.failure(e)
        }
    }

    private fun sha256Hex(s: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        return md.digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    fun generateAgentCode(deviceId: String): Result<String> {
        val accessTk = refreshTokenIfNeeded()
            ?: return Result.failure(Exception("Not signed in"))
        return try {
            val random = java.security.SecureRandom()
            val bytes = ByteArray(32)
            random.nextBytes(bytes)
            val agentSecret = bytes.joinToString("") { "%02x".format(it) }
            // Only the sha256 of the secret is stored server-side (migration-008).
            // The plaintext secret is shown to the user once and never persisted
            // here; the agent sends it in the header and the DB compares hashes.
            val agentSecretHash = sha256Hex(agentSecret)

            // Invalidate old secrets for this user
            val userId = prefs.getString("user_id", null)
            try {
                val patchUrl = "${BuildConfig.SUPABASE_URL}/rest/v1/device_links?user_id=eq.$userId&agent_secret_hash=not.is.null"
                val patchConn = URL(patchUrl).openConnection() as HttpURLConnection
                patchConn.requestMethod = "PATCH"
                patchConn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
                patchConn.setRequestProperty("Authorization", "Bearer $accessTk")
                patchConn.setRequestProperty("Content-Type", "application/json")
                patchConn.doOutput = true
                OutputStreamWriter(patchConn.outputStream).use {
                    it.write(mapper.writeValueAsString(mapOf("agent_secret_hash" to null)))
                }
                patchConn.responseCode
                patchConn.disconnect()
            } catch (_: Exception) {}

            val tokenBytes = ByteArray(16)
            random.nextBytes(tokenBytes)
            val token = tokenBytes.joinToString("") { "%02x".format(it) }

            val body = mapper.writeValueAsString(mapOf(
                "token" to token,
                "instance_id" to deviceId,
                "user_id" to userId,
                "agent_secret_hash" to agentSecretHash,
                "used" to true
            ))
            val url = "${BuildConfig.SUPABASE_URL}/rest/v1/device_links"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer $accessTk")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write(body) }
            val code = conn.responseCode
            if (code in 200..299) {
                Result.success(agentSecret)
            } else {
                val err = conn.errorStream?.let { BufferedReader(InputStreamReader(it)).use { r -> r.readText() } } ?: ""
                Log.w(TAG, "Generate agent code failed: $code $err")
                Result.failure(Exception("Failed ($code)"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Revoke all of this user's linked agents by clearing their secret hashes. */
    fun unlinkAgents(): Result<Unit> {
        val accessTk = refreshTokenIfNeeded()
            ?: return Result.failure(Exception("Not signed in"))
        val userId = prefs.getString("user_id", null)
            ?: return Result.failure(Exception("No user"))
        return try {
            val url = "${BuildConfig.SUPABASE_URL}/rest/v1/device_links?user_id=eq.$userId&agent_secret_hash=not.is.null"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "PATCH"
            conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer $accessTk")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use {
                it.write(mapper.writeValueAsString(mapOf("agent_secret_hash" to null)))
            }
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) Result.success(Unit)
            else Result.failure(Exception("Failed ($code)"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Delete the signed-in user's cloud account + all data, then sign out locally. */
    fun deleteAccount(): Result<Unit> {
        val accessTk = refreshTokenIfNeeded()
            ?: return Result.failure(Exception("Not signed in"))
        return try {
            val url = "${BuildConfig.SUPABASE_URL}/rest/v1/rpc/delete_account"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("apikey", BuildConfig.SUPABASE_ANON_KEY)
            conn.setRequestProperty("Authorization", "Bearer $accessTk")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            OutputStreamWriter(conn.outputStream).use { it.write("{}") }
            val code = conn.responseCode
            conn.disconnect()
            if (code in 200..299) {
                signOut()
                Result.success(Unit)
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
        val userId = tree.get("user")?.get("id")?.asText()
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putLong("expires_at", System.currentTimeMillis() / 1000 + expiresIn)
            .putString("user_email", email)
            .putString("user_id", userId)
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
