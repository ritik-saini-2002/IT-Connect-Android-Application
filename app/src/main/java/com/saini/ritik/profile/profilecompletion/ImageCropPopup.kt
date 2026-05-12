package com.saini.ritik.profile.profilecompletion

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
//import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

// ── Eligible MIME types ───────────────────────────────────────────────────────

val ELIGIBLE_IMAGE_MIME_TYPES = arrayOf(
    "image/jpeg",
    "image/png",
    "image/webp"
)

// ── Launcher helper ───────────────────────────────────────────────────────────

@Composable
fun rememberEligibleImageLauncher(
    onPicked: (Uri) -> Unit
) = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
) { uri: Uri? -> uri?.let { onPicked(it) } }

// ── Crop dialog ───────────────────────────────────────────────────────────────

@Composable
fun ImageCropDialog(
    sourceUri : Uri,
    onCropped : (ByteArray, String) -> Unit,
    onDismiss : () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var bitmap   by remember { mutableStateOf<Bitmap?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var scale    by remember { mutableFloatStateOf(1f) }
    var offsetX  by remember { mutableFloatStateOf(0f) }
    var offsetY  by remember { mutableFloatStateOf(0f) }
    var canvasW  by remember { mutableIntStateOf(0) }
    var canvasH  by remember { mutableIntStateOf(0) }

    // Load + downsample on IO thread to avoid OOM on large images
    LaunchedEffect(sourceUri) {
        withContext(Dispatchers.IO) {
            try {
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(sourceUri)?.use {
                    BitmapFactory.decodeStream(it, null, opts)
                }
                val maxDim = 2048
                val sample = maxOf(1, maxOf(opts.outWidth, opts.outHeight) / maxDim)
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
                bitmap = context.contentResolver.openInputStream(sourceUri)?.use { s ->
                    BitmapFactory.decodeStream(s, null, decodeOpts)
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
                Text(
                    "Crop Photo",
                    color      = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 16.sp
                )
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(28.dp).padding(end = 4.dp),
                        color       = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(
                        enabled = bitmap != null,
                        onClick = {
                            val bmp = bitmap ?: return@IconButton
                            isSaving = true
                            scope.launch {
                                val cropped = cropToCircle(bmp, scale, offsetX, offsetY, canvasW, canvasH)
                                val bytes = withContext(Dispatchers.IO) {
                                    val baos = ByteArrayOutputStream()
                                    cropped.compress(Bitmap.CompressFormat.PNG, 100, baos)
                                    baos.toByteArray()
                                }
                                withContext(Dispatchers.Main) {
                                    isSaving = false
                                    onCropped(bytes, "avatar_${System.currentTimeMillis()}.png")
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Check, "Done", tint = Color(0xFF4CAF50))
                    }
                }
            }

            // ── Crop viewport ─────────────────────────────────────────────────
            // Fixed 300 dp square — keeps the crop circle a sensible size on all screens
            Box(
                Modifier
                    .size(300.dp)
                    .align(Alignment.Center)
                    .onSizeChanged { canvasW = it.width; canvasH = it.height }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale   = (scale * zoom).coerceIn(0.5f, 8f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    val imageBitmap = bmp.asImageBitmap()

                    // Layer 1: image only
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
                            dstOffset = IntOffset(left.roundToInt(), top.roundToInt()),
                            dstSize   = IntSize(dW.roundToInt(), dH.roundToInt())
                        )
                    }

                    // Layer 2: scrim + circle cutout
                    // graphicsLayer(Offscreen) is required for BlendMode.Clear to punch
                    // through the scrim layer only — without it, Clear goes all the way to
                    // the Activity background and you see nothing inside the circle.
                    androidx.compose.foundation.Canvas(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                    ) {
                        drawCropOverlay(size)
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
                Text(
                    "Pinch to zoom · Drag to reposition",
                    color    = Color.White.copy(0.6f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { scale = 1f; offsetX = 0f; offsetY = 0f }) {
                    Text("Reset", color = Color.White.copy(0.7f))
                }
            }
        }
    }
}

// ── Overlay drawing ───────────────────────────────────────────────────────────

private fun DrawScope.drawCropOverlay(cs: Size) {
    // Cap radius at 280 dp so the circle doesn't fill the whole screen
    val maxRadiusPx = 280.dp.toPx()
    val radius  = min(min(cs.width, cs.height) / 2f * 0.85f, maxRadiusPx)
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

    // Must match drawCropOverlay exactly
    val density     = android.content.res.Resources.getSystem().displayMetrics.density
    val maxRadiusPx = 280f * density
    val radius      = min(min(canvasW, canvasH) / 2f * 0.85f, maxRadiusPx)
    val cx          = canvasW / 2f
    val cy          = canvasH / 2f

    val cropL = ((cx - radius - left) / dW * bW).coerceIn(0f, bW - 1f)
    val cropT = ((cy - radius - top)  / dH * bH).coerceIn(0f, bH - 1f)
    val cropR = ((cx + radius - left) / dW * bW).coerceIn(0f, bW)
    val cropB = ((cy + radius - top)  / dH * bH).coerceIn(0f, bH)

    val cropW = (cropR - cropL).roundToInt().coerceAtLeast(1)
    val cropH = (cropB - cropT).roundToInt().coerceAtLeast(1)

    val cropped = Bitmap.createBitmap(source, cropL.roundToInt(), cropT.roundToInt(), cropW, cropH)
    val OUTPUT  = 512
    val scaled  = Bitmap.createScaledBitmap(cropped, OUTPUT, OUTPUT, true)

    val output = Bitmap.createBitmap(OUTPUT, OUTPUT, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint  = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawCircle(OUTPUT / 2f, OUTPUT / 2f, OUTPUT / 2f, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(scaled, 0f, 0f, paint)

    if (cropped != source) cropped.recycle()
    if (scaled  != output) scaled.recycle()

    output
}