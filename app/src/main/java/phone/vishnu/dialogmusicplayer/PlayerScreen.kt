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

import android.os.Build
import android.support.v4.media.session.PlaybackStateCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val poppins = FontFamily(Font(R.font.poppins))

/**
 * The whole player screen. Replaces `activity_main.xml`.
 *
 * Stateless except for two pieces of pure-UI state (slider drag + the
 * remaining-time toggle); everything else comes in via [state], and every
 * action leaves via a callback so the Activity owns the media plumbing.
 */
@Composable
fun PlayerScreen(
    state: PlayerUiState,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onRepeat: () -> Unit,
    onCycleSpeed: () -> Unit,
    onBackgroundTap: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Transparent area above the content — tapping it sends the app to back.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { onBackgroundTap() } },
            contentAlignment = Alignment.Center,
        ) {
            AlbumArt(state)
        }

        BottomSheet(
            state = state,
            onPlayPause = onPlayPause,
            onSeekTo = onSeekTo,
            onRewind = onRewind,
            onForward = onForward,
            onRepeat = onRepeat,
            onCycleSpeed = onCycleSpeed,
        )
    }
}

@Composable
private fun AlbumArt(state: PlayerUiState) {
    val shape = RoundedCornerShape(8.dp)
    val modifier = Modifier
        .padding(32.dp)
        .size(220.dp)
        .clip(shape)
        // Consume taps so tapping the artwork does not background the app.
        .pointerInput(Unit) { detectTapGestures { } }

    val albumArt = state.albumArt
    if (albumArt != null) {
        Image(
            bitmap = albumArt.asImageBitmap(),
            contentDescription = stringResource(R.string.album_art_iv),
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Image(
            painter = painterResource(R.drawable.ic_music_note),
            contentDescription = stringResource(R.string.album_art_iv),
            modifier = modifier,
        )
    }
}

@Composable
private fun BottomSheet(
    state: PlayerUiState,
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onRepeat: () -> Unit,
    onCycleSpeed: () -> Unit,
) {
    val textColor = colorResource(R.color.textColor)
    val textColorLight = colorResource(R.color.textColorLight)

    var timeReversed by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(colorResource(R.color.BGColor))
            .padding(bottom = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.music_player),
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.6.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 2.dp),
        )

        Text(
            text = state.title,
            color = textColor,
            fontFamily = poppins,
            fontSize = 14.sp,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .basicMarquee(iterations = Int.MAX_VALUE),
        )

        Text(
            text = state.artist,
            color = textColor,
            fontFamily = poppins,
            fontSize = 12.sp,
            letterSpacing = 1.2.sp,
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .basicMarquee(iterations = Int.MAX_VALUE),
        )

        PlaybackSlider(
            positionMs = state.positionMs,
            durationMs = state.durationMs,
            onSeek = onSeekTo,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatTime(state.positionMs, timeReversed, state.durationMs),
                color = textColorLight,
                fontFamily = poppins,
                fontSize = 12.sp,
                // clickable (not a raw pointerInput) so the toggle is exposed to
                // TalkBack and keyboard users as an activatable control.
                modifier = Modifier.clickable(
                    onClickLabel = stringResource(R.string.toggle_remaining_time),
                    role = Role.Button,
                ) { timeReversed = !timeReversed },
            )
            Text(
                text = formatTime(state.durationMs, timeReversed, state.durationMs),
                color = textColorLight,
                fontFamily = poppins,
                fontSize = 12.sp,
            )
        }

        ControlsRow(
            state = state,
            onPlayPause = onPlayPause,
            onRewind = onRewind,
            onForward = onForward,
            onRepeat = onRepeat,
            onCycleSpeed = onCycleSpeed,
        )
    }
}

@Composable
private fun PlaybackSlider(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }

    val maxValue = durationMs.coerceAtLeast(1L).toFloat()
    val value = (if (dragging) dragValue else positionMs.toFloat()).coerceIn(0f, maxValue)
    val accent = dmpAccentColor()

    Slider(
        value = value,
        valueRange = 0f..maxValue,
        onValueChange = {
            dragging = true
            dragValue = it
        },
        onValueChangeFinished = {
            onSeek(dragValue.toLong())
            dragging = false
        },
        colors = SliderDefaults.colors(
            thumbColor = accent,
            activeTrackColor = accent,
            inactiveTrackColor = colorResource(R.color.sliderInactiveColor),
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}

@Composable
private fun ControlsRow(
    state: PlayerUiState,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onRepeat: () -> Unit,
    onCycleSpeed: () -> Unit,
) {
    val accent = dmpAccentColor()
    val accentLight = dmpAccentColorLight()
    val textColorLight = colorResource(R.color.textColorLight)

    val playPauseIcon = when (state.playbackState) {
        PlaybackStateCompat.STATE_PLAYING -> R.drawable.ic_pause
        PlaybackStateCompat.STATE_STOPPED -> R.drawable.ic_replay
        else -> R.drawable.ic_play
    }

    val repeatIcon = if (state.repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) {
        R.drawable.ic_repeat_one
    } else {
        R.drawable.ic_repeat
    }
    val repeatTint = if (state.repeatMode == PlaybackStateCompat.REPEAT_MODE_NONE) {
        textColorLight
    } else {
        accent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ControlButton(
            iconRes = repeatIcon,
            contentDescription = stringResource(R.string.repeat_track_toggle),
            tint = repeatTint,
            buttonSize = 40.dp,
            iconSize = 26.dp,
            onClick = onRepeat,
        )

        // Variable playback speed needs PlaybackParams (API 23+); the service is
        // a no-op below that, so the control is only offered where it works.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            SpeedButton(speed = state.playbackSpeed, onClick = onCycleSpeed)
        }

        Spacer(modifier = Modifier.weight(1f))

        ControlButton(
            iconRes = R.drawable.ic_rewind,
            contentDescription = stringResource(R.string.rewind_button),
            tint = accentLight,
            buttonSize = 40.dp,
            iconSize = 28.dp,
            onClick = onRewind,
        )

        ControlButton(
            iconRes = R.drawable.ic_seek,
            contentDescription = stringResource(R.string.forward_button),
            tint = accentLight,
            buttonSize = 40.dp,
            iconSize = 28.dp,
            onClick = onForward,
        )

        Spacer(modifier = Modifier.weight(1f))

        ControlButton(
            iconRes = playPauseIcon,
            contentDescription = stringResource(R.string.play_pause_button),
            tint = accent,
            buttonSize = 64.dp,
            iconSize = 44.dp,
            onClick = onPlayPause,
        )
    }
}

@Composable
private fun ControlButton(
    iconRes: Int,
    contentDescription: String,
    tint: Color,
    buttonSize: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(buttonSize),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Tappable playback-speed label (e.g. `1.5x`). Cycles to the next speed on tap;
 * tinted with the accent colour whenever the speed is not 1x.
 */
@Composable
private fun SpeedButton(speed: Float, onClick: () -> Unit) {
    Text(
        text = stringResource(speedLabelRes(speed)),
        color = if (speed == 1f) colorResource(R.color.textColorLight) else dmpAccentColor(),
        fontFamily = poppins,
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(
                onClickLabel = stringResource(R.string.playback_speed_toggle),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

private fun speedLabelRes(speed: Float): Int = when (speed) {
    0.5f -> R.string.zero_five_x
    0.75f -> R.string.zero_seven_five_x
    1.25f -> R.string.one_two_five_x
    1.5f -> R.string.one_five_x
    2f -> R.string.two_x
    else -> R.string.one_x
}

/**
 * `m:ss`, or `-m:ss` counting down when [reversed] (tap the elapsed-time label
 * to toggle). Mirrors the old `MainActivity.getFormattedTime`.
 */
private fun formatTime(millis: Long, reversed: Boolean, totalMs: Long): String {
    if (reversed) return "-" + formatTime(totalMs - millis, false, totalMs)

    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
