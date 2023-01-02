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

import android.net.Uri;
import androidx.annotation.NonNull;

public class Audio {

    long id;
    private String name, artist;
    private long duration;
    private Uri uri;

    public Audio() {}

    public Audio(long id, String name, String artist, long duration, Uri uri) {
        this.id = id;
        this.name = name;
        this.artist = artist;
        this.duration = duration;
        this.uri = uri;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
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
        return "Audio{"
                + "id="
                + id
                + ", name='"
                + name
                + '\''
                + ", artist='"
                + artist
                + '\''
                + ", duration="
                + duration
                + ", uri="
                + uri
                + '}';
    }
}
