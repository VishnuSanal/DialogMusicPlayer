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

import android.graphics.Bitmap
import android.support.v4.media.session.PlaybackStateCompat

/**
 * Immutable snapshot of everything the player screen renders. The Activity maps
 * `MediaControllerCompat` callbacks into this; the Composable just reads it.
 */
data class PlayerUiState(
    val title: String = "",
    val artist: String = "",
    val albumArt: Bitmap? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackState: Int = PlaybackStateCompat.STATE_NONE,
    val repeatMode: Int = PlaybackStateCompat.REPEAT_MODE_NONE,
    val playbackSpeed: Float = 1f,
) {
    val isPlaying: Boolean get() = playbackState == PlaybackStateCompat.STATE_PLAYING
}
