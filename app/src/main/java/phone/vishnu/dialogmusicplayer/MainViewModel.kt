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

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = SaveItemRepository(application)

    private val _uiState = MutableStateFlow(PlayerUiState())

    /** The single source of truth the Compose UI observes. */
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    fun onMetadataChanged(title: String, artist: String, albumArt: Bitmap?, durationMs: Long) {
        _uiState.update {
            it.copy(
                title = title,
                artist = artist,
                albumArt = albumArt,
                durationMs = durationMs,
            )
        }
    }

    fun onPlaybackStateChanged(playbackState: Int, positionMs: Long) {
        _uiState.update { it.copy(playbackState = playbackState, positionMs = positionMs) }
    }

    fun onRepeatModeChanged(repeatMode: Int) {
        _uiState.update { it.copy(repeatMode = repeatMode) }
    }

    fun insert(saveItem: SaveItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertSaveItem(saveItem)
        }
    }

    fun delete(saveItem: SaveItem) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSaveItem(saveItem)
        }
    }

    fun getSaveItem(id: Long): LiveData<SaveItem> {
        val liveData = MutableLiveData(SaveItem(id, 0))

        viewModelScope.launch(Dispatchers.IO) {
            repository.getSaveItem(id)?.let { liveData.postValue(it) }
        }

        return liveData
    }
}
