package com.example.ritik_2.profile.profilecompletion

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

// ── Eligible MIME types accepted by PocketBase avatar field ───────────────────
val ELIGIBLE_IMAGE_MIME_TYPES = arrayOf(
    "image/jpeg",
    "image/png",
    "image/webp"
)

// ── Launcher that restricts picker to JPEG / PNG / WebP ──────────────────────
/**
 * Use this instead of rememberLauncherForActivityResult(GetContent()) everywhere
 * a profile image is picked. OpenDocument allows explicit MIME filtering.
 *
 * Usage:
 *   val launcher = rememberEligibleImageLauncher { uri -> ... }
 *   launcher.launch(ELIGIBLE_IMAGE_MIME_TYPES)
 */
@Composable
fun rememberEligibleImageLauncher(
    onPicked: (Uri) -> Unit
) = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
) { uri: Uri? -> uri?.let { onPicked(it) } }

// ── Crop dialog ───────────────────────────────────────────────────────────────

/**
 * Full-screen circular crop popup shown as a Dialog inside the current composable.
 * No separate Activity is spawned — keeps memory footprint low.
 *
 * [onCropped] delivers (ByteArray of PNG, filename) after the user taps ✓.
 */
@Composable
fun ImageCropDialog(
    sourceUri : Uri,
    onCropped : (ByteArray, String) -> Unit,
    onDismiss : () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var bitmap    by remember { mutableStateOf<Bitmap?>(null) }
    var isSaving  by remember { mutableStateOf(false) }
    var scale     by remember { mutableFloatStateOf(1f) }
    var offsetX   by remember { mutableFloatStateOf(0f) }
    var offsetY   by remember { mutableFloatStateOf(0f) }
    var canvasW   by remember { mutableIntStateOf(0) }
    var canvasH   by remember { mutableIntStateOf(0) }

    LaunchedEffect(sourceUri) {
        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openInputStream(sourceUri)
                val raw    = BitmapFactory.decodeStream(stream)
                stream?.close()
                if (raw != null) {
                    val maxDim = 2048
                    bitmap = if (raw.width > maxDim || raw.height > maxDim) {
                        val ratio = maxDim.toFloat() / maxOf(raw.width, raw.height)
                        Bitmap.createScaledBitmap(raw,
                            (raw.width  * ratio).toInt(),
                            (raw.height * ratio).toInt(), true)
                    } else raw
                }
            } catch (e: Exception) {
                android.util.Log.e("ImageCrop", "Load failed: ${e.message}")
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress      = true,
            dismissOnClickOutside   = false
        )
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // ── Top bar ───────────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Cancel", tint = Color.White)
                }
                Text("Crop Photo",
                    color      = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 16.sp)
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(28.dp).padding(end = 4.dp),
                        color       = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = {
                        val bmp = bitmap ?: return@IconButton
                        isSaving = true
                        scope.launch {
                            val cropped  = cropToCircle(bmp, scale, offsetX, offsetY, canvasW, canvasH)
                            val bytes    = withContext(Dispatchers.IO) {
                                val baos = java.io.ByteArrayOutputStream()
                                cropped.compress(Bitmap.CompressFormat.PNG, 100, baos)
                                baos.toByteArray()
                            }
                            val filename = "avatar_${System.currentTimeMillis()}.png"
                            withContext(Dispatchers.Main) {
                                isSaving = false
                                onCropped(bytes, filename)
                            }
                        }
                    }) {
                        Icon(Icons.Default.Check, "Done", tint = Color(0xFF4CAF50))
                    }
                }
            }

            // ── Crop viewport ─────────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .align(Alignment.Center)
                    .onSizeChanged { canvasW = it.width; canvasH = it.height }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale   = (scale * zoom).coerceIn(0.5f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    val imageBitmap = bmp.asImageBitmap()
                    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                        val cs    = size
                        val bW    = bmp.width.toFloat()
                        val bH    = bmp.height.toFloat()
                        val baseS = min(cs.width / bW, cs.height / bH)
                        val dW    = bW * baseS * scale
                        val dH    = bH * baseS * scale
                        val left  = (cs.width  - dW) / 2f + offsetX
                        val top   = (cs.height - dH) / 2f + offsetY
                        drawImage(
                            image     = imageBitmap,
                            dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                            dstSize   = androidx.compose.ui.unit.IntSize(dW.toInt(), dH.toInt())
                        )
                        drawCropOverlay(cs)
                    }
                } else {
                    Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }

            // ── Bottom controls ───────────────────────────────────────────────
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Pinch to zoom · Drag to reposition",
                    color    = Color.White.copy(0.6f),
                    fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { scale = 1f; offsetX = 0f; offsetY = 0f }) {
                    Text("Reset", color = Color.White.copy(0.7f))
                }
            }
        }
    }
}

// ── Drawing ───────────────────────────────────────────────────────────────────

private fun DrawScope.drawCropOverlay(cs: androidx.compose.ui.geometry.Size) {
    val radius  = min(cs.width, cs.height) / 2f * 0.9f
    val centerX = cs.width  / 2f
    val centerY = cs.height / 2f
    drawRect(color = Color.Black.copy(alpha = 0.55f), size = cs)
    drawCircle(
        color     = Color.Transparent,
        radius    = radius,
        center    = Offset(centerX, centerY),
        blendMode = BlendMode.Clear
    )
    drawCircle(
        color  = Color.White.copy(0.85f),
        radius = radius,
        center = Offset(centerX, centerY),
        style  = Stroke(width = 2.dp.toPx())
    )
    // Rule-of-thirds grid
    val left  = centerX - radius
    val top   = centerY - radius
    val third = radius * 2 / 3f
    for (i in 1..2) {
        drawLine(Color.White.copy(0.3f),
            Offset(left + third * i, top),
            Offset(left + third * i, top + radius * 2), 1.dp.toPx())
        drawLine(Color.White.copy(0.3f),
            Offset(left, top + third * i),
            Offset(left + radius * 2, top + third * i), 1.dp.toPx())
    }
}

// ── Crop engine ───────────────────────────────────────────────────────────────

private suspend fun cropToCircle(
    source : Bitmap,
    scale  : Float,
    offsetX: Float,
    offsetY: Float,
    canvasW: Int,
    canvasH: Int
): Bitmap = withContext(Dispatchers.Default) {
    val bW    = source.width.toFloat()
    val bH    = source.height.toFloat()
    val baseS = min(canvasW / bW, canvasH / bH)
    val dW    = bW * baseS * scale
    val dH    = bH * baseS * scale
    val left  = (canvasW - dW) / 2f + offsetX
    val top   = (canvasH - dH) / 2f + offsetY
    val radius = min(canvasW, canvasH) / 2f * 0.9f
    val cx    = canvasW / 2f
    val cy    = canvasH / 2f

    val cropL = ((cx - radius - left) / dW * bW).coerceIn(0f, bW)
    val cropT = ((cy - radius - top)  / dH * bH).coerceIn(0f, bH)
    val cropR = ((cx + radius - left) / dW * bW).coerceIn(0f, bW)
    val cropB = ((cy + radius - top)  / dH * bH).coerceIn(0f, bH)

    val cropW   = (cropR - cropL).toInt().coerceAtLeast(1)
    val cropH   = (cropB - cropT).toInt().coerceAtLeast(1)
    val cropped = Bitmap.createBitmap(source, cropL.toInt(), cropT.toInt(), cropW, cropH)
    val scaled  = Bitmap.createScaledBitmap(cropped, 512, 512, true)

    val output  = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
    val canvas  = Canvas(output)
    val paint   = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawCircle(256f, 256f, 256f, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(scaled, 0f, 0f, paint)
    output
}