/*
 * Copyright (C) 2021 - 2024 Vishnu Sanal. T
 *
 * This file is part of DialogMusicPlayer.
 *
 * DialogMusicPlayer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package phone.vishnu.dialogmusicplayer

import android.content.Context
import android.util.Log
import java.util.concurrent.Executors

object FileUtils {

    private const val TAG = "DMP"
    private const val PREFS_NAME = "dmp_prefs"
    private const val KEY_LEGACY_FILES_CLEARED = "legacy_files_cleared"

    private val ioExecutor = Executors.newSingleThreadExecutor()

    /**
     * One-time cleanup of files an older version of the app mistakenly wrote to
     * internal storage. Runs once per install and is a no-op on every launch
     * after that — there is no reason to walk the filesystem on every start.
     *
     * The previous implementation shelled out to `rm -rf` via [Runtime.exec] on
     * every `onCreate`, which is slow, fragile and unnecessary.
     */
    fun clearApplicationData(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_LEGACY_FILES_CLEARED, false)) return

        ioExecutor.execute {
            runCatching {
                context.filesDir.listFiles()?.forEach { it.deleteRecursively() }
            }.onFailure { Log.w(TAG, "clearApplicationData() failed", it) }

            prefs.edit().putBoolean(KEY_LEGACY_FILES_CLEARED, true).apply()
        }
    }
}
