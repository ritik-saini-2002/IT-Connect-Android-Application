package com.example.ritik_2.profile.profilecompletion

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.example.ritik_2.theme.ITConnectTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

class ImageCropActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SOURCE_URI  = "source_uri"
        const val EXTRA_RESULT_URI  = "result_uri"

        fun createIntent(ctx: Context, sourceUri: Uri) =
            Intent(ctx, ImageCropActivity::class.java).apply {
                putExtra(EXTRA_SOURCE_URI, sourceUri.toString())
            }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uriString = intent.getStringExtra(EXTRA_SOURCE_URI) ?: run { finish(); return }
        val sourceUri = uriString.toUri()

        setContent {
            ITConnectTheme {
                ImageCropScreen(
                    sourceUri  = sourceUri,
                    onCropped  = { croppedUri ->
                        val result = Intent().apply {
                            putExtra(EXTRA_RESULT_URI, croppedUri.toString())
                        }
                        setResult(RESULT_OK, result)
                        finish()
                    },
                    onCancel   = { finish() }
                )
            }
        }
    }
}

@Composable
fun ImageCropScreen(
    sourceUri: Uri,
    onCropped: (Uri) -> Unit,
    onCancel : () -> Unit
) {
    val context     = LocalContext.current
    val scope       = rememberCoroutineScope()
    var bitmap      by remember { mutableStateOf<Bitmap?>(null) }
    var isSaving    by remember { mutableStateOf(false) }

    // Transform state
    var scale       by remember { mutableStateOf(1f) }
    var offsetX     by remember { mutableStateOf(0f) }
    var offsetY     by remember { mutableStateOf(0f) }
    var canvasSize  by remember { mutableStateOf(IntSize.Zero) }

    // Load bitmap
    LaunchedEffect(sourceUri) {
        withContext(Dispatchers.IO) {
            val stream = context.contentResolver.openInputStream(sourceUri)
            val raw    = BitmapFactory.decodeStream(stream)
            stream?.close()
            if (raw != null) {
                // Auto-orient using EXIF if needed
                bitmap = Bitmap.createScaledBitmap(raw,
                    min(raw.width, 2048), min(raw.height, 2048), true)
            }
        }
    }

    Scaffold(
        topBar = {
            Box(
                Modifier.fillMaxWidth()
                    .background(Color.Black)
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Close, "Cancel", tint = Color.White)
                    }
                    Text("Crop Photo",
                        color      = Color.White,
                        fontSize   = 16.sp,
                        fontWeight = FontWeight.SemiBold)
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(28.dp).padding(end = 8.dp),
                            color       = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = {
                            val bmp = bitmap ?: return@IconButton
                            isSaving = true
                            scope.launch {
                                val cropped = cropBitmap(bmp, scale, offsetX, offsetY, canvasSize)
                                val uri     = saveBitmapToCache(context, cropped)
                                withContext(Dispatchers.Main) {
                                    isSaving = false
                                    if (uri != null) onCropped(uri)
                                    else Toast.makeText(context, "Crop failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) {
                            Icon(Icons.Default.Check, "Done", tint = Color(0xFF4CAF50))
                        }
                    }
                }
            }
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            // ── Crop viewport ─────────────────────────────────────────────────
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale   = (scale * zoom).coerceIn(0.5f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                bitmap?.let { bmp ->
                    val imageBitmap = bmp.asImageBitmap()
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Draw transformed image
                        val cs   = size
                        val bW   = bmp.width.toFloat()
                        val bH   = bmp.height.toFloat()
                        // Fit to canvas initially
                        val baseScale = min(cs.width / bW, cs.height / bH)
                        val dW   = bW * baseScale * scale
                        val dH   = bH * baseScale * scale
                        val left = (cs.width - dW) / 2f + offsetX
                        val top  = (cs.height - dH) / 2f + offsetY

                        drawImage(
                            image    = imageBitmap,
                            dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                            dstSize   = androidx.compose.ui.unit.IntSize(dW.toInt(), dH.toInt())
                        )

                        // Dark overlay outside circle
                        drawCropOverlay(cs)
                    }
                } ?: Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Instructions ──────────────────────────────────────────────────
            Text("Pinch to zoom · Drag to reposition",
                color    = Color.White.copy(0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 8.dp))

            // ── Reset button ──────────────────────────────────────────────────
            TextButton(
                onClick  = { scale = 1f; offsetX = 0f; offsetY = 0f },
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text("Reset", color = Color.White.copy(0.7f))
            }
        }
    }
}

// Draw dark overlay with circular cutout
private fun DrawScope.drawCropOverlay(cs: Size) {
    val radius  = min(cs.width, cs.height) / 2f * 0.9f
    val centerX = cs.width  / 2f
    val centerY = cs.height / 2f

    // Semi-transparent overlay
    drawRect(color = Color.Black.copy(alpha = 0.55f), size = cs)

    // Clear circle (punch-out effect via BlendMode — approximate with white circle)
    drawCircle(
        color  = Color.Transparent,
        radius = radius,
        center = Offset(centerX, centerY),
        blendMode = androidx.compose.ui.graphics.BlendMode.Clear
    )

    // Circle border
    drawCircle(
        color  = Color.White.copy(0.85f),
        radius = radius,
        center = Offset(centerX, centerY),
        style  = Stroke(width = 2.dp.toPx())
    )

    // Grid lines (rule of thirds)
    val left   = centerX - radius
    val top    = centerY - radius
    val third  = radius * 2 / 3f
    val stroke = Stroke(width = 0.5.dp.toPx())
    for (i in 1..2) {
        drawLine(Color.White.copy(0.3f),
            Offset(left + third * i, top),
            Offset(left + third * i, top + radius * 2), stroke.width)
        drawLine(Color.White.copy(0.3f),
            Offset(left, top + third * i),
            Offset(left + radius * 2, top + third * i), stroke.width)
    }
}

// Crop the bitmap to a circle based on current transform
private suspend fun cropBitmap(
    source    : Bitmap,
    scale     : Float,
    offsetX   : Float,
    offsetY   : Float,
    canvasSize: IntSize
): Bitmap = withContext(Dispatchers.Default) {
    val cs       = canvasSize
    val bW       = source.width.toFloat()
    val bH       = source.height.toFloat()
    val baseScale = min(cs.width / bW, cs.height / bH)
    val dW       = bW * baseScale * scale
    val dH       = bH * baseScale * scale
    val left     = (cs.width - dW) / 2f + offsetX
    val top      = (cs.height - dH) / 2f + offsetY

    val radius   = min(cs.width, cs.height) / 2f * 0.9f
    val cx       = cs.width  / 2f
    val cy       = cs.height / 2f

    // Map circle crop rect back to source bitmap coordinates
    val cropLeft   = ((cx - radius - left) / dW * bW).coerceIn(0f, bW)
    val cropTop    = ((cy - radius - top)  / dH * bH).coerceIn(0f, bH)
    val cropRight  = ((cx + radius - left) / dW * bW).coerceIn(0f, bW)
    val cropBottom = ((cy + radius - top)  / dH * bH).coerceIn(0f, bH)

    val cropW = (cropRight  - cropLeft).toInt().coerceAtLeast(1)
    val cropH = (cropBottom - cropTop).toInt().coerceAtLeast(1)

    val cropped = Bitmap.createBitmap(source, cropLeft.toInt(), cropTop.toInt(), cropW, cropH)

    // Scale to 512×512
    val size    = 512
    val scaled  = Bitmap.createScaledBitmap(cropped, size, size, true)

    // Make circular
    val output  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas  = Canvas(output)
    val paint   = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
    canvas.drawBitmap(scaled, 0f, 0f, paint)
    output
}

private suspend fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri? =
    withContext(Dispatchers.IO) {
        try {
            val file = File(context.cacheDir, "cropped_avatar_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            android.util.Log.e("ImageCrop", "saveBitmapToCache: ${e.message}")
            null
        }
    }