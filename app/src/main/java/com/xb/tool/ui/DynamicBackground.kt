package com.xb.tool.ui

import android.net.Uri
import android.os.Build
import android.view.LayoutInflater
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.xb.tool.R
import com.xb.tool.core.AppState
import java.io.File

/**
 * Draws the user selected image / looping muted video behind the whole app.
 * Blur is supported on API 31+; the overlay keeps foreground content readable.
 */
@OptIn(UnstableApi::class)
@Composable
fun DynamicBackground(state: AppState, dark: Boolean) {
    val config = state.theme
    val videoPath = config.backgroundVideoPath
    val imagePath = config.backgroundPath
    if (videoPath == null && imagePath == null) return

    val context = LocalContext.current
    val blur = if (Build.VERSION.SDK_INT >= 31) config.backgroundBlur else 0f

    Box(
        Modifier
            .fillMaxSize()
            .alpha(config.backgroundAlpha)
            .then(if (blur > 0f) Modifier.blur(blur.dp) else Modifier)
    ) {
        if (videoPath != null && File(videoPath).exists()) {
            val player = remember(videoPath) {
                ExoPlayer.Builder(context).build().apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                    volume = 0f
                    setMediaItem(MediaItem.fromUri(Uri.fromFile(File(videoPath))))
                    prepare()
                    playWhenReady = true
                }
            }
            val lifecycleOwner = LocalLifecycleOwner.current
            DisposableEffect(lifecycleOwner, player) {
                val observer = LifecycleEventObserver { _, event ->
                    when (event) {
                        Lifecycle.Event.ON_START -> player.play()
                        Lifecycle.Event.ON_STOP -> player.pause()
                        else -> Unit
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                    player.release()
                }
            }
            AndroidView(
                factory = { ctx ->
                    LayoutInflater.from(ctx)
                        .inflate(R.layout.view_video_background, null) as PlayerView
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.player = player
                    view.useController = false
                }
            )
        } else if (imagePath != null) {
            BackgroundImage(imagePath, 1f)
        }
    }

    // Readability scrim over the media layer.
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = if (dark) 0.58f else 0.42f),
                        Color.Black.copy(alpha = if (dark) 0.72f else 0.55f),
                        Color.Black.copy(alpha = if (dark) 0.58f else 0.42f)
                    )
                )
            )
    )
}
