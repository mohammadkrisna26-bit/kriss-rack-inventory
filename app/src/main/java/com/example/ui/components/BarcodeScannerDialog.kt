package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun BarcodeScannerDialog(
    onDismissRequest: () -> Unit,
    onBarcodeScanned: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedExplanation by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var manualInput by remember { mutableStateOf("") }
    var isTorchEnabled by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<androidx.camera.core.CameraControl?>(null) }

    // Scanner state
    var isScanningActive by remember { mutableStateOf(true) }
    var scannerStatus by remember { mutableStateOf("Mencari barcode...") }
    var detectedBarcode by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            permissionDeniedExplanation = false
            cameraError = null
            isScanningActive = true
            scannerStatus = "Mencari barcode..."
        } else {
            permissionDeniedExplanation = true
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Laser Animation
    val infiniteTransition = rememberInfiniteTransition(label = "scanner_laser")
    val laserOffsetRatio by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    // Trigger phone vibration when barcode is found
    fun triggerHapticFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(70, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                @Suppress("DEPRECATION")
                vibrator?.vibrate(70)
            }
        } catch (_: Exception) {
            // Ignore if vibration not permitted or not present
        }
    }

    // Process scan result with debounce and visual confirmation
    fun handleFoundBarcode(code: String) {
        val trimmed = code.trim()
        if (trimmed.isEmpty() || !isScanningActive) return
        isScanningActive = false
        detectedBarcode = trimmed
        scannerStatus = "Barcode ditemukan: $trimmed"
        triggerHapticFeedback()

        coroutineScope.launch {
            delay(550) // Allow user to see positive green confirmation
            onBarcodeScanned(trimmed)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .padding(vertical = 12.dp)
                .testTag("barcode_scanner_modal"),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header: Title & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "SCAN BARCODE ARTIKEL",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Scan barcode untuk membaca Nomor Artikel produk",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.testTag("close_scanner_button")
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Tutup Scanner")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scanner Status Indicator Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = if (detectedBarcode != null) Color(0xFF10B981)
                    else Color(0xFF1E293B),
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (detectedBarcode != null) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF38BDF8))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = scannerStatus,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Camera Viewport / Reticle / Fallback Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF0F172A)),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission && cameraError == null && !permissionDeniedExplanation) {
                        // Live CameraX with ML Kit & ZXing
                        CameraScannerView(
                            isScanningActive = isScanningActive,
                            onBarcodeDetected = { code -> handleFoundBarcode(code) },
                            onError = { err -> cameraError = err },
                            onCameraReady = { control -> cameraControl = control },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Scanner Guide Reticle (Square/Rectangular box with dynamic state)
                        val reticleBorderColor = if (detectedBarcode != null) Color(0xFF10B981) else Color(0xFF38BDF8)
                        Box(
                            modifier = Modifier
                                .size(240.dp, 150.dp)
                                .border(2.5.dp, reticleBorderColor, RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            // Animated Horizontal Scanning Laser Line (only when actively searching)
                            if (isScanningActive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.94f)
                                        .height(2.5.dp)
                                        .offset(y = (150f * laserOffsetRatio).dp)
                                        .background(Color(0xFFEF4444))
                                )
                            }
                        }

                        // Petunjuk Singkat Di Bawah Kamera
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Black.copy(alpha = 0.65f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = if (detectedBarcode != null) "✓ Barcode terverifikasi" else "Arahkan kamera ke barcode produk",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }

                        // Torch / Flash Toggle Button
                        if (cameraControl != null) {
                            IconButton(
                                onClick = {
                                    val nextState = !isTorchEnabled
                                    isTorchEnabled = nextState
                                    cameraControl?.enableTorch(nextState)
                                },
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.5f))
                            ) {
                                Icon(
                                    imageVector = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = "Flashlight",
                                    tint = if (isTorchEnabled) Color(0xFFFBBF24) else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    } else if (permissionDeniedExplanation || !hasCameraPermission) {
                        // Permission Denied UI (Requirement 18)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = Color(0xFFF87171),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Akses kamera ditolak. Izinkan akses kamera pada pengaturan browser untuk menggunakan scanner.",
                                color = Color.White,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                modifier = Modifier.testTag("retry_camera_permission_button")
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("COBA LAGI", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Camera Unavailable / Error UI (Requirement 15, 19)
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Kamera tidak dapat digunakan pada perangkat ini.",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Silakan masukkan nomor artikel atau barcode secara manual di bawah.",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Manual Input Fallback (Requirement 15, 16)
                OutlinedTextField(
                    value = manualInput,
                    onValueChange = { manualInput = it },
                    label = { Text("Nomor Artikel Manual") },
                    placeholder = { Text("Contoh: 190351") },
                    leadingIcon = {
                        Icon(Icons.Default.Keyboard, contentDescription = null)
                    },
                    trailingIcon = {
                        if (manualInput.isNotEmpty()) {
                            IconButton(onClick = { manualInput = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "Hapus")
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (manualInput.isNotBlank()) {
                                handleFoundBarcode(manualInput)
                            }
                        }
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_barcode_input")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Quick Barcode Test Buttons (For Store Simulation & Testing on Desktop/Emulator)
                Text(
                    text = "Pintasan Simulasi Barcode Toko:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("10000001", "10000002", "20000001", "89927531").forEach { sample ->
                        OutlinedButton(
                            onClick = { handleFoundBarcode(sample) },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = sample.takeLast(4),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons: [ TUTUP ] & [ MASUKKAN ARTIKEL MANUAL / GUNAKAN ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("cancel_scanner_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("TUTUP", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            if (manualInput.isNotBlank()) {
                                handleFoundBarcode(manualInput)
                            }
                        },
                        enabled = manualInput.isNotBlank(),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(48.dp)
                            .testTag("submit_manual_article_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("GUNAKAN ARTIKEL", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * CameraX implementation with native Google ML Kit Barcode Scanning + ZXing secondary fallback.
 * Strictly uses rear camera (DEFAULT_BACK_CAMERA) as default, ensures clean unbinding on dispose.
 */
@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraScannerView(
    isScanningActive: Boolean,
    onBarcodeDetected: (String) -> Unit,
    onError: (String) -> Unit,
    onCameraReady: (CameraControl) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val isScanningRef = remember { AtomicBoolean(isScanningActive) }
    isScanningRef.set(isScanningActive)

    // ML Kit client configured for all standard retail barcodes
    val mlKitScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_QR_CODE
            )
            .build()
        BarcodeScanning.getClient(options)
    }

    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    // Ensure camera stream and executor are completely stopped and cleaned up (Requirements 22, 23)
    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                cameraProviderRef?.unbindAll()
                cameraExecutor.shutdown()
                mlKitScanner.close()
            } catch (_: Exception) {}
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProviderRef = cameraProvider

                    // Select Rear Camera strictly (Requirement 7)
                    val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                        CameraSelector.DEFAULT_BACK_CAMERA
                    } else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                        CameraSelector.DEFAULT_FRONT_CAMERA
                    } else {
                        onError("Kamera tidak dapat digunakan pada perangkat ini.")
                        return@addListener
                    }

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    // ImageAnalysis use case with Keep-Only-Latest backpressure strategy
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        if (!isScanningRef.get()) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )

                            // Primary: Google ML Kit
                            mlKitScanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    if (isScanningRef.get() && barcodes.isNotEmpty()) {
                                        val barcode = barcodes.firstOrNull()
                                        val raw = barcode?.rawValue ?: barcode?.displayValue
                                        if (!raw.isNullOrBlank()) {
                                            isScanningRef.set(false)
                                            ContextCompat.getMainExecutor(ctx).execute {
                                                onBarcodeDetected(raw)
                                            }
                                        }
                                    }
                                }
                                .addOnFailureListener {
                                    // Secondary: ZXing Fallback if ML Kit has an issue
                                    if (isScanningRef.get()) {
                                        val zxingCode = decodeWithZxing(imageProxy)
                                        if (!zxingCode.isNullOrBlank()) {
                                            isScanningRef.set(false)
                                            ContextCompat.getMainExecutor(ctx).execute {
                                                onBarcodeDetected(zxingCode)
                                            }
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            // Fallback to ZXing on raw imageProxy buffer if mediaImage is null
                            if (isScanningRef.get()) {
                                val zxingCode = decodeWithZxing(imageProxy)
                                if (!zxingCode.isNullOrBlank()) {
                                    isScanningRef.set(false)
                                    ContextCompat.getMainExecutor(ctx).execute {
                                        onBarcodeDetected(zxingCode)
                                    }
                                }
                            }
                            imageProxy.close()
                        }
                    }

                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )

                    onCameraReady(camera.cameraControl)

                    // Enable auto-focus on center of screen (Requirement 10)
                    try {
                        val meteringPointFactory = SurfaceOrientedMeteringPointFactory(1f, 1f)
                        val centerPoint = meteringPointFactory.createPoint(0.5f, 0.5f)
                        val action = FocusMeteringAction.Builder(centerPoint, FocusMeteringAction.FLAG_AF).build()
                        camera.cameraControl.startFocusAndMetering(action)
                    } catch (_: Exception) {}

                } catch (e: Exception) {
                    onError("Kamera tidak dapat digunakan pada perangkat ini.")
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}

/**
 * Secondary pure-Java ZXing decoder running on the YUV byte buffer.
 * Supports EAN-13, EAN-8, UPC-A, UPC-E, Code 128, Code 39, ITF, QR Code.
 */
private fun decodeWithZxing(imageProxy: ImageProxy): String? {
    return try {
        val plane = imageProxy.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val width = imageProxy.width
        val height = imageProxy.height
        val source = PlanarYUVLuminanceSource(
            bytes, width, height,
            0, 0, width, height,
            false
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        val reader = MultiFormatReader()
        val result = reader.decodeWithState(bitmap)
        result.text
    } catch (_: Exception) {
        null
    }
}
