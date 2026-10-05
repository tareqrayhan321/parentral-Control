package com.example.ui.components

import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

private const val TAG = "QrScannerDialog"

private fun Context.findLifecycleOwner(): LifecycleOwner? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is LifecycleOwner) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** Reads QR codes from camera frames with ZXing (no Google Play services module needed). */
private class QrAnalyzer(private val onFound: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true
            )
        )
    }

    override fun analyze(image: ImageProxy) {
        try {
            val plane = image.planes[0]               // Y (luminance) plane
            val rowStride = plane.rowStride
            val width = image.width
            val height = image.height
            val buffer = plane.buffer
            val data = ByteArray(rowStride * height)
            buffer.get(data, 0, minOf(buffer.remaining(), data.size))
            val source = PlanarYUVLuminanceSource(data, rowStride, height, 0, 0, width, height, false)
            val text = reader.decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
            if (!text.isNullOrBlank()) onFound(text)
        } catch (_: Exception) {
            // No QR code in this frame: normal, keep scanning.
        } finally {
            reader.reset()
            image.close()
        }
    }
}

/** Full-screen in-app QR scanner. Calls [onResult] once with the QR text. */
@Composable
fun QrScannerDialog(
    onResult: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    var delivered by remember { mutableStateOf(false) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        // TextureView based: works on older Samsung tablets where SurfaceView previews stay black.
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    val owner = ctx.findLifecycleOwner()
                    if (owner == null) {
                        error = "Camera could not be started."
                    } else {
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            try {
                                val provider = providerFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }
                                @Suppress("DEPRECATION")
                                val analysis = ImageAnalysis.Builder()
                                    .setTargetResolution(Size(1280, 720))
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()
                                    .also { a ->
                                        a.setAnalyzer(executor, QrAnalyzer { text ->
                                            ContextCompat.getMainExecutor(ctx).execute {
                                                if (!delivered) {
                                                    delivered = true
                                                    onResult(text)
                                                }
                                            }
                                        })
                                    }
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis
                                )
                            } catch (e: Exception) {
                                Log.e(TAG, "Camera start failed", e)
                                error = "ক্যামেরা চালু করা যায়নি: ${e.message ?: "unknown error"}"
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                    }
                    previewView
                },
                onRelease = {
                    try {
                        ProcessCameraProvider.getInstance(context).get().unbindAll()
                    } catch (_: Exception) { }
                }
            )

            // Viewfinder frame
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(260.dp)
                    .border(3.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(24.dp))
            )

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(24.dp)
            ) {
                Text(
                    "অভিভাবকের ফোনের QR কোডটি ফ্রেমের ভেতরে ধরুন",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(
                        error ?: "",
                        color = Color(0xFFFFB4AB),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                    )
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(24.dp)
                    .fillMaxWidth()
            ) { Text("Cancel") }
        }
    }
}
