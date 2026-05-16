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
import android.util.Log
import androidx.annotation.AnyRes
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicReference

object AudioUtils {

    private const val TAG = "DMP"
    private const val UNKNOWN_TITLE = "<Unknown Title>"
    private const val UNKNOWN_ARTIST = "<Unknown Artist>"

    fun getMetaData(context: Context, duration: String, uri: Uri): Audio {
        try {
            retrieveMetadata(context, duration, uri)?.let { return it }

            fetchMetadata(context, duration, uri)?.let { return it }

            val contentUri = AtomicReference<Uri>()
            MediaScannerConnection.scanFile(
                context,
                arrayOf(uri.path),
                null,
            ) { _, resultUri -> contentUri.set(resultUri) }

            fetchMetadata(context, duration, contentUri.get())?.let { return it }
        } catch (e: Exception) {
            Log.w(TAG, "getMetaData() failed for $uri", e)
        }

        val name = extractName(uri)

        return Audio(
            id = -1,
            mediaMetadata = MediaMetadataCompat.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "-1")
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, name)
                .putString(MediaMetadata.METADATA_KEY_TITLE, name)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, UNKNOWN_ARTIST)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration.toLong())
                .putString(
                    MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                    getUriToDrawable(context, R.drawable.icon_fg),
                )
                .build(),
            duration = duration.toLong(),
            uri = uri,
        )
    }

    private fun retrieveMetadata(context: Context, duration: String, uri: Uri): Audio? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)

            val title = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: extractName(uri)

            val artist = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
                ?: UNKNOWN_ARTIST

            val id = extractId(context, duration, uri)

            val builder = MediaMetadataCompat.Builder()
                .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, id.toString())
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, duration.toLong())

            val picture = retriever.embeddedPicture
            if (picture != null) {
                builder.putBitmap(
                    MediaMetadata.METADATA_KEY_ALBUM_ART,
                    BitmapFactory.decodeByteArray(picture, 0, picture.size),
                )
            } else if (id != -1L) {
                builder.putString(
                    MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                    "content://media/external/audio/media/$id/albumart",
                )
            }

            return Audio(id, builder.build(), duration.toLong(), uri)
        } catch (e: Exception) {
            Log.w(TAG, "retrieveMetadata() failed for $uri", e)
            return null
        } finally {
            retriever.release()
        }
    }

    private fun extractId(context: Context, duration: String, uri: Uri): Long {
        context.applicationContext.contentResolver.query(
            audioCollectionUri(),
            arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.DURATION} = ?",
            arrayOf(duration),
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            if (cursor.moveToNext()) return cursor.getLong(idColumn)
        }
        return -1
    }

    private fun fetchMetadata(context: Context, duration: String, uri: Uri?): Audio? {
        if (uri == null) return null

        context.applicationContext.contentResolver.query(
            audioCollectionUri(),
            arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.DURATION,
            ),
            "${MediaStore.Audio.Media.DURATION} = ?",
            arrayOf(duration),
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)

            if (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val contentUri =
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

                var name = cursor.getString(nameColumn)
                if (name != null) {
                    val index = name.lastIndexOf(".")
                    if (index > -1) name = name.substring(0, index)
                }
                if (name.isNullOrBlank() || name == "<unknown>") name = extractName(uri)

                var artist = cursor.getString(artistColumn)
                if (artist.isNullOrBlank() || artist == "<unknown>") artist = UNKNOWN_ARTIST

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
                    cursor.getInt(durationColumn).toLong(),
                    contentUri,
                )
            }
        }

        return null
    }

    private fun audioCollectionUri(): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }
    }

    private fun extractName(uri: Uri): String {
        try {
            val lastPathSegment = uri.lastPathSegment ?: return UNKNOWN_TITLE
            val split = URLDecoder.decode(lastPathSegment, "UTF-8").split("/")

            if (split.isEmpty()) return UNKNOWN_TITLE

            val name = split.last().replace("%20", " ")
            val index = name.lastIndexOf(".")

            return if (index > -1) name.substring(0, index) else name
        } catch (e: Exception) {
            Log.w(TAG, "extractName() failed for $uri", e)
        }

        return UNKNOWN_TITLE
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
