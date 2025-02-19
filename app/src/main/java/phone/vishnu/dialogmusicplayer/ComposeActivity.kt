package phone.vishnu.dialogmusicplayer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Paint
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import phone.vishnu.dialogmusicplayer.ui.theme.DialogMusicPlayerTheme
import kotlin.math.cos
import kotlin.math.sin

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

//                    PlayerUI(
//                        applicationContext,
//                        Audio(
//                            -1,
//                            MediaMetadataCompat.Builder().putString(
//                                MediaMetadata.METADATA_KEY_MEDIA_ID,
//                                "-1",
//                            ).putString(
//                                MediaMetadata.METADATA_KEY_DISPLAY_TITLE,
//                                "Dreaming On",
//                            ).putString(
//                                MediaMetadata.METADATA_KEY_TITLE,
//                                "Dreaming On",
//                            ).putString(
//                                MediaMetadata.METADATA_KEY_ARTIST,
//                                "NEFEX",
//                            ).putLong(
//                                MediaMetadata.METADATA_KEY_DURATION,
//                                100000,
//                            ).putBitmap(
//                                MediaMetadata.METADATA_KEY_ALBUM_ART,
//                                null,
//                            ).build(),
//                            100000,
//                            Uri.EMPTY,
//                        ),
//                    )

                    var musicProgress by remember { mutableLongStateOf(15L) }
                    val trackDuration = 180000L

                    SemiCircularMusicSlider(
                        progress = musicProgress,
                        trackLength = trackDuration,
                        onProgressChange = { musicProgress = it },
                    )
                }
            }
        }
    }
}

@Composable
fun SemiCircularMusicSlider(
    progress: Long,
    trackLength: Long,
    onProgressChange: (Long) -> Unit,
) {
    var angle by remember { mutableFloatStateOf(-180f) }
    val strokeWidth = 16f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    angle += dragAmount.x / 4
                    angle = angle.coerceIn(-180f, 0f)
                    val newProgress = ((angle + 180) / 360f * trackLength).toLong()
                    onProgressChange(newProgress)
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
        ) {
            val centerX = size.width / 2
            val centerY = size.height
            val radian = Math.toRadians(angle.toDouble())

            val radius = size.width / 2

            val x = centerX + radius * cos(radian)
            val y = centerY + radius * sin(radian)

            // semi-circle track
            drawArc(
                color = Color.White,
                startAngle = -180f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(centerX - radius, centerY - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(strokeWidth),
            )

            // progress arc
            drawArc(
                color = Color.Black.copy(alpha = 0.75f),
                startAngle = -180f,
                sweepAngle = ((progress.toFloat() / trackLength) * 360f),
                useCenter = false,
                topLeft = Offset(centerX - radius, centerY - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(strokeWidth),
            )

            // draggable handle
            drawCircle(Color.Red, 24f, Offset(x.toFloat(), y.toFloat()))

            // current progress
            drawContext.canvas.nativeCanvas.apply {
                drawText(
                    "${progress / 1000} sec",
                    centerX,
                    centerY,
                    Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = 60f
                        textAlign = Paint.Align.CENTER
                    },
                )
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
//        modifier = Modifier.clip(CircleShape),
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
                .clip(RoundedCornerShape(topStartPercent = 50, topEndPercent = 50))
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

//            Slider(
//                modifier = Modifier.padding(
//                    vertical = 16.dp,
//                    horizontal = 8.dp,
//                ),
//                value = sliderState,
//                onValueChange = { sliderState = it },
//                colors = SliderDefaults.colors(
//                    thumbColor = MaterialTheme.colorScheme.secondary,
//                    activeTrackColor = MaterialTheme.colorScheme.secondary,
//                    inactiveTrackColor = MaterialTheme.colorScheme.secondaryContainer,
//                ),
//                valueRange = 0f..totalDuration.toFloat(),
//            )

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
