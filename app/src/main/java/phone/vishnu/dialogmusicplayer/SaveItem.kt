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

package phone.vishnu.dialogmusicplayer

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SaveItem(
    @PrimaryKey val id: Long,
    val duration: Long,
) {
    override fun toString(): String {
        return "ResumeItem(path='$id', duration=$duration)"
    }
}
