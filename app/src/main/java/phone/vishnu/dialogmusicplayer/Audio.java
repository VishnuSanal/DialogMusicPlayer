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

import android.media.MediaMetadata;
import android.net.Uri;
import android.support.v4.media.MediaMetadataCompat;
import androidx.annotation.NonNull;

public class Audio {

    long id;
    private MediaMetadataCompat mediaMetadata;
    private long duration;
    private Uri uri;

    public Audio() {}

    public Audio(long id, MediaMetadataCompat mediaMetadata, long duration, Uri uri) {
        this.id = id;
        this.mediaMetadata = mediaMetadata;
        this.duration = duration;
        this.uri = uri;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public MediaMetadataCompat getMediaMetadata() {
        return mediaMetadata;
    }

    public void setMediaMetadata(MediaMetadataCompat mediaMetadata) {
        this.mediaMetadata = mediaMetadata;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public Uri getUri() {
        return uri;
    }

    public void setUri(Uri uri) {
        this.uri = uri;
    }

    @NonNull
    @Override
    public String toString() {
        return "Audio["
                + "\nid: "
                + id
                + "\nMETADATA_KEY_MEDIA_ID: "
                + mediaMetadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                + "\nMETADATA_KEY_DISPLAY_TITLE: "
                + mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)
                + "\nMETADATA_KEY_TITLE: "
                + mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
                + "\nMETADATA_KEY_ARTIST: "
                + mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
                + "\nMETADATA_KEY_DURATION: "
                + mediaMetadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)
                + "\nMETADATA_KEY_ALBUM_ART_URI: "
                + mediaMetadata.getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI)
                + "\nMETADATA_KEY_ALBUM_ART: "
                + mediaMetadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART)
                + "\nduration: "
                + duration
                + "\nuri: "
                + uri
                + "\n]";
    }
}
