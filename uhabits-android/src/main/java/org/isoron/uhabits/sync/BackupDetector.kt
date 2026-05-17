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
import android.os.Environment
import androidx.core.content.ContextCompat
import java.io.File

/**
 * Detects existing Loop Habit Tracker backup files on the device.
 * Searches both the original app's backup path and our own.
 */
object BackupDetector {

    private val backupPattern = Regex("^Loop Habits Backup .+\\.db$")
    private const val ORIGINAL_PACKAGE = "org.isoron.uhabits"

    /**
     * Finds the most recent backup .db file in known backup locations.
     * Checks: 1) original Loop app's external files, 2) our own external files.
     * Returns null if no backup found.
     */
    fun findLatestBackup(context: Context): File? {
        val candidates = mutableListOf<File>()

        // Our own backup dir
        val ownDirs = ContextCompat.getExternalFilesDirs(context, null)
        for (dir in ownDirs) {
            if (dir == null) continue
            findBackupsIn(File(dir, "Backups"), candidates)
        }

        // Original Loop app's backup dir (Android/data/org.isoron.uhabits/files/Backups)
        val extStorage = Environment.getExternalStorageDirectory()
        val originalDir = File(extStorage, "Android/data/$ORIGINAL_PACKAGE/files/Backups")
        findBackupsIn(originalDir, candidates)

        return candidates.maxByOrNull { it.lastModified() }
    }

    private fun findBackupsIn(dir: File, out: MutableList<File>) {
        if (!dir.exists()) return
        dir.listFiles()
            ?.filter { it.name.matches(backupPattern) }
            ?.let { out.addAll(it) }
    }
}
