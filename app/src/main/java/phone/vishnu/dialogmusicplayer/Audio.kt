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

import android.media.MediaMetadata
import android.net.Uri
import android.support.v4.media.MediaMetadataCompat

data class Audio(
    val id: Long = 0,
    val mediaMetadata: MediaMetadataCompat? = null,
    val duration: Long = 0,
    val uri: Uri? = null,
) {
    override fun toString(): String {
        return "Audio[" +
            "\nid: $id" +
            "\nMETADATA_KEY_MEDIA_ID: ${mediaMetadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)}" +
            "\nMETADATA_KEY_DISPLAY_TITLE: ${mediaMetadata?.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)}" +
            "\nMETADATA_KEY_TITLE: ${mediaMetadata?.getString(MediaMetadataCompat.METADATA_KEY_TITLE)}" +
            "\nMETADATA_KEY_ARTIST: ${mediaMetadata?.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)}" +
            "\nMETADATA_KEY_DURATION: ${mediaMetadata?.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)}" +
            "\nMETADATA_KEY_ALBUM_ART_URI: ${mediaMetadata?.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)}" +
            "\nMETADATA_KEY_ALBUM_ART: ${mediaMetadata?.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)}" +
            "\nduration: $duration" +
            "\nuri: $uri" +
            "\n]"
    }
}
