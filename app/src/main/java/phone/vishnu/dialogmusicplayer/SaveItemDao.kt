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

package phone.vishnu.dialogmusicplayer

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface SaveItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(saveItem: SaveItem?)

    @Update
    fun update(saveItem: SaveItem?)

    @Delete
    fun delete(saveItem: SaveItem?)

    @Query("SELECT * FROM SaveItem")
    fun getAll(): LiveData<List<SaveItem?>>

    @Query("DELETE FROM SaveItem")
    fun deleteAll()

    @Query("SELECT * FROM SaveItem WHERE id = :id")
    fun getSaveItem(id: Long): SaveItem
}
