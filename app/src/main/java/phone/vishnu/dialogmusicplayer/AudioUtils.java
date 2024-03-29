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

package phone.vishnu.dialogmusicplayer;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.support.v4.media.MediaMetadataCompat;
import androidx.annotation.AnyRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class AudioUtils {

    public static Audio getMetaData(Context context, String duration, Uri uri) {

        try {

            Audio audio;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {

                audio = retrieveMetadata(context, duration, uri);

                if (audio != null) return audio;
            }

            audio = fetchMetadata(context, duration, uri);

            if (audio != null) return audio;

            AtomicReference<Uri> contentUri = new AtomicReference<>();

            MediaScannerConnection.scanFile(
                    context,
                    new String[] {uri.getPath()},
                    null,
                    (s, resultUri) -> contentUri.set(resultUri));

            audio = fetchMetadata(context, duration, contentUri.get());

            if (audio != null) return audio;

        } catch (Exception e) {
            e.printStackTrace();
        }

        String name = extractName(uri);

        return new Audio(
                -1,
                new MediaMetadataCompat.Builder()
                        .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, "-1")
                        .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, name)
                        .putString(MediaMetadata.METADATA_KEY_TITLE, name)
                        .putString(MediaMetadata.METADATA_KEY_ARTIST, "<Unknown Artist>")
                        .putLong(MediaMetadata.METADATA_KEY_DURATION, Long.parseLong(duration))
                        .putString(
                                MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                                getUriToDrawable(context, R.drawable.icon_fg))
                        .build(),
                Long.parseLong(duration),
                uri);
    }

    @Nullable
    @RequiresApi(api = Build.VERSION_CODES.Q)
    private static Audio retrieveMetadata(Context context, String duration, Uri uri) {

        try {
            MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();

            mediaMetadataRetriever.setDataSource(context, uri);

            byte[] picture = mediaMetadataRetriever.getEmbeddedPicture();

            long id = extractId(context, duration, uri);

            Audio audio =
                    new Audio(
                            id,
                            new MediaMetadataCompat.Builder()
                                    .putString(
                                            MediaMetadata.METADATA_KEY_MEDIA_ID, String.valueOf(id))
                                    .putString(
                                            MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
                                            mediaMetadataRetriever.extractMetadata(
                                                    MediaMetadataRetriever.METADATA_KEY_TITLE))
                                    .putString(
                                            MediaMetadata.METADATA_KEY_TITLE,
                                            mediaMetadataRetriever.extractMetadata(
                                                    MediaMetadataRetriever.METADATA_KEY_TITLE))
                                    .putString(
                                            MediaMetadata.METADATA_KEY_ARTIST,
                                            mediaMetadataRetriever.extractMetadata(
                                                    MediaMetadataRetriever.METADATA_KEY_ARTIST))
                                    .putLong(
                                            MediaMetadata.METADATA_KEY_DURATION,
                                            Long.parseLong(duration))
                                    .putBitmap(
                                            MediaMetadata.METADATA_KEY_ALBUM_ART,
                                            BitmapFactory.decodeByteArray(
                                                    picture,
                                                    0,
                                                    Objects.requireNonNull(picture).length))
                                    .build(),
                            Long.parseLong(duration),
                            uri);

            mediaMetadataRetriever.close();

            return audio;

        } catch (IOException | IllegalStateException | NullPointerException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static long extractId(Context context, String duration, Uri uri) {

        Cursor cursor =
                context.getApplicationContext()
                        .getContentResolver()
                        .query(
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                                        ? MediaStore.Audio.Media.getContentUri(
                                                MediaStore.VOLUME_EXTERNAL)
                                        : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                new String[] {
                                    MediaStore.Audio.Media._ID,
                                },
                                MediaStore.Audio.Media.DURATION + " = ?",
                                new String[] {duration},
                                null);

        if (cursor == null) return -1;

        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);

        //noinspection LoopStatementThatDoesntLoop
        while (cursor.moveToNext()) {

            long id = cursor.getLong(idColumn);

            cursor.close();

            return id;
        }

        return -1;
    }

    private static Audio fetchMetadata(Context context, String duration, Uri uri)
            throws IllegalArgumentException {

        Cursor cursor =
                context.getApplicationContext()
                        .getContentResolver()
                        .query(
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                                        ? MediaStore.Audio.Media.getContentUri(
                                                MediaStore.VOLUME_EXTERNAL)
                                        : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                new String[] {
                                    MediaStore.Audio.Media._ID,
                                    MediaStore.Audio.Media.DISPLAY_NAME,
                                    MediaStore.Audio.Media.ARTIST,
                                    MediaStore.Audio.Media.DURATION,
                                },
                                MediaStore.Audio.Media.DURATION + " = ?",
                                new String[] {duration},
                                null);

        if (cursor == null) return null;

        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID);

        int nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME);

        int durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION);

        int artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.ARTIST);

        //noinspection LoopStatementThatDoesntLoop
        while (cursor.moveToNext()) {

            long id = cursor.getLong(idColumn);

            Uri contentUri =
                    ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id);

            int d = cursor.getInt(durationColumn);

            String name = cursor.getString(nameColumn);

            if (name != null) {
                int index = name.lastIndexOf(".");

                if (index > -1) name = name.substring(0, index);
            }

            if (name == null || name.equals("<unknown>")) name = extractName(uri);

            String artist = cursor.getString(artistColumn);

            if (artist == null || artist.equals("<unknown>")) artist = "<Unknown Artist>";

            cursor.close();

            return new Audio(
                    id,
                    new MediaMetadataCompat.Builder()
                            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, String.valueOf(id))
                            .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, name)
                            .putString(MediaMetadata.METADATA_KEY_TITLE, name)
                            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
                            .putLong(MediaMetadata.METADATA_KEY_DURATION, Long.parseLong(duration))
                            .putString(
                                    MediaMetadata.METADATA_KEY_ALBUM_ART_URI,
                                    "content://media/external/audio/media/" + id + "/albumart")
                            .build(),
                    d,
                    contentUri);
        }

        return null;
    }

    private static String extractName(Uri uri) {
        try {

            String lastPathSegment = uri.getLastPathSegment();

            String[] split = URLDecoder.decode(lastPathSegment, "UTF-8").split("/");

            if (split.length == 0) return "<Unknown Title>";

            String name = split[split.length - 1].replace("%20", " ");

            int index = name.lastIndexOf(".");

            if (index > -1) return name.substring(0, index);

            return name;

        } catch (Exception e) {
            e.printStackTrace();
        }

        return "<Unknown Title>";
    }

    /**
     * <a href="https://stackoverflow.com/a/36062748/9652621"/></a>
     *
     * @param context - context
     * @param drawableId - drawable res id
     * @return - Uri String
     * @noinspection SameParameterValue
     */
    private static String getUriToDrawable(@NonNull Context context, @AnyRes int drawableId) {
        return ContentResolver.SCHEME_ANDROID_RESOURCE
                + "://"
                + context.getResources().getResourcePackageName(drawableId)
                + '/'
                + context.getResources().getResourceTypeName(drawableId)
                + '/'
                + context.getResources().getResourceEntryName(drawableId);
    }
}
