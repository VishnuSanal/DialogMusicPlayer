package phone.vishnu.dialogmusicplayer

import android.annotation.SuppressLint
import android.content.Context
import android.media.MediaMetadata
import android.net.Uri
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import phone.vishnu.dialogmusicplayer.ui.theme.DialogMusicPlayerTheme

private val poppinsFont = FontFamily(Font(R.font.poppins))

class ComposeActivity : ComponentActivity() {

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DialogMusicPlayerTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    containerColor = Color.Transparent,
                ) { _ ->

                    PlayerUI(
                        applicationContext,
                        Audio(
                            -1,
                            MediaMetadataCompat.Builder().putString(
                                MediaMetadata.METADATA_KEY_MEDIA_ID,
                                "-1",
                            ).putString(
                                MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
                                "Dreaming On",
                            ).putString(
                                MediaMetadata.METADATA_KEY_TITLE,
                                "Dreaming On",
                            ).putString(
                                MediaMetadata.METADATA_KEY_ARTIST,
                                "NEFEX",
                            ).putLong(
                                MediaMetadata.METADATA_KEY_DURATION,
                                100000,
                            ).putBitmap(
                                MediaMetadata.METADATA_KEY_ALBUM_ART,
                                null,
                            ).build(),
                            100000,
                            Uri.EMPTY,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerUI(context: Context, audio: Audio) {
    var sliderState by remember {
        mutableFloatStateOf(
            0f,
        )
    }
    var elapsed by remember {
        mutableLongStateOf(
            0,
        )
    }
    val totalDuration = audio.mediaMetadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION)

    Column(
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))

        Image(
            modifier = Modifier
                .align(alignment = Alignment.CenterHorizontally)
                .padding(12.dp)
                .size(64.dp)
                .aspectRatio(1f),
            alignment = Alignment.Center,
            painter = painterResource(R.drawable.ic_music_note),
            contentDescription = "",
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.35f)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(8.dp),
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                text = context.getString(R.string.app_name),
                textAlign = TextAlign.Center,
                letterSpacing = 1.1.sp,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp, 4.dp, 8.dp, 1.dp),
                fontFamily = poppinsFont,
                text = audio.mediaMetadata.getText(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE)
                    .toString(),
                maxLines = 1,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp, 1.dp, 8.dp, 2.dp),
                fontFamily = poppinsFont,
                text = audio.mediaMetadata.getText(MediaMetadataCompat.METADATA_KEY_ARTIST)
                    .toString(),
                letterSpacing = 1.08.sp,
                maxLines = 1,
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )

            Slider(
                modifier = Modifier.padding(
                    vertical = 16.dp,
                    horizontal = 8.dp,
                ),
                value = sliderState,
                onValueChange = { sliderState = it },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.secondary,
                    activeTrackColor = MaterialTheme.colorScheme.secondary,
                    inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                valueRange = 0f..totalDuration.toFloat(),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    modifier = Modifier
                        .padding(start = 12.dp, end = 4.dp),
                    text = AudioUtils.getFormattedTime(elapsed, totalDuration, false),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                )

                Spacer(Modifier.weight(1f))

                Text(
                    modifier = Modifier
                        .padding(start = 4.dp, end = 12.dp),
                    text = AudioUtils.getFormattedTime(totalDuration, totalDuration, false),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 12.sp,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                TextButton(
                    modifier = Modifier.padding(8.dp, 4.dp, 4.dp, 4.dp),
                    onClick = {},
                    content = {
                        Text(
                            text = context.getString(R.string.one_x),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                        )
                    },
                )

                Spacer(Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    IconButton(
                        modifier = Modifier
                            .padding(8.dp, 4.dp, 8.dp, 4.dp)
                            .size(36.dp),
                        onClick = {},
                    ) {
                        Icon(
                            modifier = Modifier
                                .size(36.dp),
                            painter = painterResource(R.drawable.ic_rewind),
                            tint = MaterialTheme.colorScheme.onSurface,
                            contentDescription = "rewind icon",
                        )
                    }

                    IconButton(
                        modifier = Modifier
                            .padding(4.dp, 2.dp, 4.dp, 4.dp)
                            .size(64.dp),
                        onClick = {},
                    ) {
                        Icon(
                            modifier = Modifier
                                .size(64.dp),
                            painter = painterResource(R.drawable.ic_play),
                            tint = MaterialTheme.colorScheme.onSurface,
                            contentDescription = "play pause icon",
                        )
                    }

                    IconButton(
                        modifier = Modifier
                            .padding(8.dp, 4.dp, 8.dp, 4.dp)
                            .size(36.dp),
                        onClick = {},
                    ) {
                        Icon(
                            modifier = Modifier
                                .size(36.dp),
                            painter = painterResource(R.drawable.ic_seek),
                            tint = MaterialTheme.colorScheme.onSurface,
                            contentDescription = "seek icon",
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                IconButton(
                    modifier = Modifier
                        .padding(4.dp, 4.dp, 8.dp, 4.dp)
                        .size(36.dp),
                    onClick = {},
                ) {
                    Icon(
                        modifier = Modifier
                            .size(36.dp),
                        painter = painterResource(R.drawable.ic_repeat),
                        tint = MaterialTheme.colorScheme.onSurface,
                        contentDescription = "seek icon",
                    )
                }
            }
        }
    }
}
