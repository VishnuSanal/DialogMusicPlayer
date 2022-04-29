/*
 * Copyright (C) 2021 - 2022 Vishnu Sanal. T
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

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import java.io.File;
import java.io.IOException;

public class FileUtils {

    public static void clearApplicationData(Context context) {
        try {
            AsyncTask.execute(() -> clear(context));
        } catch (Exception e) {
            Log.e("vishnu", "clearApplicationData: " + e);
        }
    }

    private static void clear(Context context) {

        String path = context.getFilesDir().getPath();

        File file = new File(path);

        if (!file.exists()) return;

        String command = "rm -rf " + path;

        try {
            Runtime.getRuntime().exec(command);
            Log.i("vishnu", "clearApplicationData() Deleted: " + path);
        } catch (IOException e) {
            Log.e("vishnu", "clearApplicationData: " + e);
        }
    }
}
