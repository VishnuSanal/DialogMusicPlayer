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

import android.content.Context;
import android.os.Build;
import androidx.core.content.ContextCompat;

public class ColorUtils {

    public static int getAccentColor(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            return ContextCompat.getColor(context, R.color.material_dynamic_primary40);

        return ContextCompat.getColor(context, R.color.accentColor);
    }

    public static int getAccentColorLight(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            return ContextCompat.getColor(context, R.color.material_dynamic_primary60);

        return ContextCompat.getColor(context, R.color.accentColor);
    }
}
