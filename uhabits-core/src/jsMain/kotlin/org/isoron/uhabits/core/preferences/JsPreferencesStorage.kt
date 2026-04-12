package org.isoron.uhabits.core.preferences

import kotlinx.browser.window
import kotlin.js.JsExport

class JsPreferencesStorage : Preferences.Storage {
    private val storage get() = window.localStorage

    override fun clear() {
        storage.clear()
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        storage.getItem(key)?.toBooleanStrictOrNull() ?: defValue

    override fun getInt(key: String, defValue: Int): Int =
        storage.getItem(key)?.toIntOrNull() ?: defValue

    override fun getLong(key: String, defValue: Long): Long =
        storage.getItem(key)?.toLongOrNull() ?: defValue

    override fun getString(key: String, defValue: String): String =
        storage.getItem(key) ?: defValue

    override fun putBoolean(key: String, value: Boolean) {
        storage.setItem(key, value.toString())
    }

    override fun putInt(key: String, value: Int) {
        storage.setItem(key, value.toString())
    }

    override fun putLong(key: String, value: Long) {
        storage.setItem(key, value.toString())
    }

    override fun putString(key: String, value: String) {
        storage.setItem(key, value)
    }

    override fun remove(key: String) {
        storage.removeItem(key)
    }
}

@JsExport
fun createPreferences(): Preferences {
    return Preferences(JsPreferencesStorage())
}
