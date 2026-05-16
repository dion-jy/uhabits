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
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Detects existing Loop Habit Tracker backup files on the device.
 * Used on first launch to prompt the user to restore existing data.
 */
object BackupDetector {

    private val backupPattern = Regex("^Loop Habits Backup .+\\.db$")

    /**
     * Finds the most recent backup .db file in known backup locations.
     * Returns null if no backup found.
     */
    fun findLatestBackup(context: Context): File? {
        val dirs = ContextCompat.getExternalFilesDirs(context, null)
        for (baseDir in dirs) {
            if (baseDir == null) continue
            val backupDir = File(baseDir, "Backups")
            if (!backupDir.exists()) continue
            val latest = backupDir.listFiles()
                ?.filter { it.name.matches(backupPattern) }
                ?.maxByOrNull { it.lastModified() }
            if (latest != null) return latest
        }
        return null
    }
}
