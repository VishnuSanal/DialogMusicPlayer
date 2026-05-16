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

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.support.v4.media.MediaMetadataCompat
import androidx.annotation.AnyRes
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicReference

object AudioUtils {

    @JvmStatic
    fun getMetaData(context: Context, duration: String, uri: Uri): Audio {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val audio = retrieveMetadata(context, duration, uri)
                if (audio != null) return audio
            }

            val audio = fetchMetadata(context, duration, uri)
            if (audio != null) return audio

            val contentUri = AtomicReference<Uri>()

            MediaScannerConnection.scanFile(
                context,
                arrayOf(uri.path),
                null,
            ) { _, resultUri -> contentUri.set(resultUri) }

            val scannedAudio = fetchMetadata(context, duration, contentUri.get())
            if (scannedAudio != null) return scannedAudio
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val name = extractName(uri)

        return Audio(
            -1,
            MediaMetadataCompat.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "-1")
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, name)
                .putString(MediaMetadata.METADATA_KEY_TITLE, name)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "<Unknown Artist>")
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration.toLong())
                .putString(
                    MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                    getUriToDrawable(context, R.drawable.icon_fg),
                )
                .build(),
            duration.toLong(),
            uri,
        )
    }

    private fun retrieveMetadata(context: Context, duration: String, uri: Uri): Audio? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        try {
            val mediaMetadataRetriever = MediaMetadataRetriever()
            mediaMetadataRetriever.setDataSource(context, uri)

            val picture = mediaMetadataRetriever.embeddedPicture

            val id = extractId(context, duration, uri)

            val audio = Audio(
                id,
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, id.toString())
                    .putString(
                        MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
                        mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    )
                    .putString(
                        MediaMetadata.METADATA_KEY_TITLE,
                        mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                    )
                    .putString(
                        MediaMetadata.METADATA_KEY_ARTIST,
                        mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST),
                    )
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, duration.toLong())
                    .putBitmap(
                        MediaMetadata.METADATA_KEY_ALBUM_ART,
                        BitmapFactory.decodeByteArray(picture, 0, picture!!.size),
                    )
                    .build(),
                duration.toLong(),
                uri,
            )

            mediaMetadataRetriever.close()

            return audio
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    private fun extractId(context: Context, duration: String, uri: Uri): Long {
        val cursor = context.applicationContext
            .contentResolver
            .query(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                },
                arrayOf(MediaStore.Audio.Media._ID),
                MediaStore.Audio.Media.DURATION + " = ?",
                arrayOf(duration),
                null,
            ) ?: return -1

        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            cursor.close()
            return id
        }

        return -1
    }

    private fun fetchMetadata(context: Context, duration: String, uri: Uri?): Audio? {
        if (uri == null) return null

        val cursor = context.applicationContext
            .contentResolver
            .query(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                },
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.DURATION,
                ),
                MediaStore.Audio.Media.DURATION + " = ?",
                arrayOf(duration),
                null,
            ) ?: return null

        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
        val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
        val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
        val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.ARTIST)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)

            val contentUri = ContentUris.withAppendedId(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                id,
            )

            val d = cursor.getInt(durationColumn)

            var name = cursor.getString(nameColumn)
            if (name != null) {
                val index = name.lastIndexOf(".")
                if (index > -1) name = name.substring(0, index)
            }
            if (name == null || name == "<unknown>") name = extractName(uri)

            var artist = cursor.getString(artistColumn)
            if (artist == null || artist == "<unknown>") artist = "<Unknown Artist>"

            cursor.close()

            return Audio(
                id,
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, id.toString())
                    .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, name)
                    .putString(MediaMetadata.METADATA_KEY_TITLE, name)
                    .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                    .putLong(MediaMetadata.METADATA_KEY_DURATION, duration.toLong())
                    .putString(
                        MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                        "content://media/external/audio/media/$id/albumart",
                    )
                    .build(),
                d.toLong(),
                contentUri,
            )
        }

        return null
    }

    private fun extractName(uri: Uri): String {
        try {
            val lastPathSegment = uri.lastPathSegment
            val split = URLDecoder.decode(lastPathSegment, "UTF-8").split("/")

            if (split.isEmpty()) return "<Unknown Title>"

            val name = split[split.size - 1].replace("%20", " ")
            val index = name.lastIndexOf(".")

            return if (index > -1) name.substring(0, index) else name
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return "<Unknown Title>"
    }

    private fun getUriToDrawable(context: Context, @AnyRes drawableId: Int): String {
        return ContentResolver.SCHEME_ANDROID_RESOURCE +
            "://" +
            context.resources.getResourcePackageName(drawableId) +
            '/' +
            context.resources.getResourceTypeName(drawableId) +
            '/' +
            context.resources.getResourceEntryName(drawableId)
    }
}
