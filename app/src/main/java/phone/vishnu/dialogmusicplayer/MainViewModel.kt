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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    var repository: SaveItemRepository? = null

    init {
        this.repository = SaveItemRepository(application)
    }

    fun insert(saveItem: SaveItem?) {
        viewModelScope.launch(Dispatchers.IO) {
            repository?.insertSaveItem(saveItem)
        }
    }

    fun delete(saveItem: SaveItem?) {
        viewModelScope.launch(Dispatchers.IO) {
            repository?.deleteSaveItem(saveItem)
        }
    }

    fun getSaveItem(id: Long): MutableLiveData<SaveItem> {
        val mutableLiveData: MutableLiveData<SaveItem> = MutableLiveData(SaveItem(id, 0))

        viewModelScope.launch(Dispatchers.IO) {
            val saveItem = repository?.getSaveItem(id)

            if (saveItem != null) {
                mutableLiveData.postValue(saveItem)
            }
        }

        return mutableLiveData
    }
}
