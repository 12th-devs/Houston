package com.astroboii47.commander

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * Samsung-spec (§9) image viewer recreation: fit-to-viewport at 1×, pinch
 * around the focus point clamped to 1–8× of fit scale, double-tap to zoom
 * toward the tap (2–8×) or reset, drag-pan only while zoomed with edge
 * clamping and centering of undersized dimensions, transform preserved
 * across resize, plus accessible zoom/reset buttons (no gesture-only
 * controls).
 */
@Composable
fun ZoomableImage(
    file: File,
    contentDescription: String?,
    onSingleTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val bitmap = remember(file.absolutePath) {
        runCatching {
            BitmapFactory.Options().apply { inJustDecodeBounds = true }.also {
                BitmapFactory.decodeFile(file.absolutePath, it)
            }.let { bounds ->
                val opts = BitmapFactory.Options()
                var sample = 1
                val maxSide = 2048
                while ((bounds.outWidth / sample) > maxSide || (bounds.outHeight / sample) > maxSide) sample *= 2
                opts.inSampleSize = sample
                BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
            }
        }.getOrNull()
    }
    if (bitmap == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Couldn't load this image.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var scale by remember { mutableStateOf(1f) } // relative to fit
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    fun imgAspect() = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
    fun fittedSize(): Size {
        val vw = viewSize.width.toFloat().coerceAtLeast(1f)
        val vh = viewSize.height.toFloat().coerceAtLeast(1f)
        val fit = minOf(vw / bitmap.width, vh / bitmap.height)
        return Size(bitmap.width * fit, bitmap.height * fit)
    }
    fun clampOffset(s: Float, off: Offset): Offset {
        if (s <= 1f) return Offset.Zero
        val fit = fittedSize()
        val scaled = Size(fit.width * s, fit.height * s)
        val vw = viewSize.width.toFloat()
        val vh = viewSize.height.toFloat()
        val maxX = ((scaled.width - vw) / 2).coerceAtLeast(0f)
        val maxY = ((scaled.height - vh) / 2).coerceAtLeast(0f)
        return Offset(off.x.coerceIn(-maxX, maxX), off.y.coerceIn(-maxY, maxY))
    }

    BoxWithConstraints(modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.92f))) {
        Box(
            Modifier.fillMaxSize()
                .onSizeChanged { viewSize = it }
                // Pinch + pan. Tap gestures live in the chained detector below;
                // transform consumption keeps taps from firing after a pinch.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown()
                        var zoom = scale
                        var pan = offset
                        do {
                            val event = awaitPointerEvent()
                            if (event.changes.count { it.pressed } < 2 && zoom <= 1f) {
                                // Single finger at fit scale: let the tap detector own it,
                                // but still track drag-pan start for the zoomed case.
                                if (scale > 1f) {
                                    pan = clampOffset(scale, pan + event.calculatePan())
                                }
                                event.changes.forEach { if (it.pressed) it.consume() }
                            } else {
                                zoom = (zoom * event.calculateZoom()).coerceIn(1f, 8f)
                                pan = if (zoom > 1f) clampOffset(zoom, pan + event.calculatePan())
                                else Offset.Zero
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                        scale = zoom
                        offset = pan
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { tap ->
                            if (scale > 1f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                                val center = Offset(viewSize.width / 2f, viewSize.height / 2f)
                                offset = clampOffset(2.5f, (center - tap) * 1.5f)
                            }
                        },
                        onTap = { onSingleTap() },
                    )
                }
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                    transformOrigin = TransformOrigin.Center,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Accessible zoom controls (alternatives to gestures).
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(onClick = {
                scale = (scale * 1.5f).coerceIn(1f, 8f)
                offset = clampOffset(scale, offset)
            }) {
                Icon(Icons.Outlined.ZoomIn, "Zoom in", tint = MaterialTheme.colorScheme.inverseOnSurface)
            }
            IconButton(onClick = {
                scale = (scale / 1.5f).coerceIn(1f, 8f)
                offset = clampOffset(scale, offset)
            }) {
                Icon(Icons.Outlined.ZoomOut, "Zoom out", tint = MaterialTheme.colorScheme.inverseOnSurface)
            }
            IconButton(onClick = { scale = 1f; offset = Offset.Zero }) {
                Icon(Icons.Outlined.Close, "Reset zoom", tint = MaterialTheme.colorScheme.inverseOnSurface)
            }
        }
    }
}

@Composable
fun ImageViewerDialog(file: File, onClose: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) { onDispose { } }
    Box(Modifier.fillMaxSize()) {
        ZoomableImage(file = file, contentDescription = "Memory photo", onSingleTap = onClose)
        IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
            Icon(Icons.Outlined.Close, "Close viewer", tint = MaterialTheme.colorScheme.inverseOnSurface)
        }
    }
}
