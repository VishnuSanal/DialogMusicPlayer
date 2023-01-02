/*
 * Copyright (C) 2021 - 2023 Vishnu Sanal. T
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

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;

public class AudioUtils {

    public static Audio getMetaData(Context context, String duration, Uri uri) {

        try (Cursor cursor =
                context.getApplicationContext()
                        .getContentResolver()
                        .query(
                                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                                        ? MediaStore.Audio.Media.getContentUri(
                                                MediaStore.VOLUME_EXTERNAL)
                                        : MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                new String[] {
                                    MediaStore.Video.Media._ID,
                                    MediaStore.Video.Media.DISPLAY_NAME,
                                    MediaStore.Video.Media.ARTIST,
                                    MediaStore.Video.Media.DURATION,
                                },
                                MediaStore.Video.Media.DURATION + " = ?",
                                new String[] {duration},
                                null)) {

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

                return new Audio(id, name, artist, d, contentUri);
            }
        }

        return new Audio(-1, extractName(uri), "<Unknown Artist>", Long.parseLong(duration), uri);
    }

    private static String extractName(Uri uri) {

        String[] split = uri.getLastPathSegment().split("/");

        if (split.length == 0) split = new String[] {"<Unknown Title>"};

        return split[split.length - 1];
    }
}
