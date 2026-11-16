package com.amyrmo.damavand.lite.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.ui.LiteViewModel
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage

/**
 * QR scan import (3.2.0, PattNG): point the camera at a config QR code
 * (or a subscription QR). The scanned text goes through the same
 * add-subscription path as a pasted link, so single links and sub URLs
 * both work. One scan at a time — after a scan the screen closes.
 */
private const val FAILED_QR_COOLDOWN_MS = 4_000L

@Composable
fun ScannerScreen(vm: LiteViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }
    // The analyzer closure reads this State object (stable reference),
    // so the one-scan guard stays correct across recompositions.
    val scanned = remember { mutableStateOf<String?>(null) }
    // The last code that failed to import, and when: ignored for a few
    // seconds, so a bad QR held in view doesn't loop (a toast, and for a
    // sub URL a new fetch, on every frame).
    val lastFailed = remember { mutableStateOf<String?>(null) }
    val lastFailedAt = remember { longArrayOf(0L) }
    // Camera + ML Kit live only as long as this screen: the provider is
    // bound to the Activity's lifecycle, so release it on dispose
    // (otherwise the camera stays on until the activity stops).
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }
    val scanner = remember { BarcodeScanning.getClient() }
    val disposed = remember { booleanArrayOf(false) }
    DisposableEffect(Unit) {
        onDispose {
            disposed[0] = true
            providerFuture.addListener({
                runCatching { providerFuture.get().unbindAll() }
            }, ContextCompat.getMainExecutor(context))
            scanner.close()
        }
    }
    val subImport by vm.subImport.collectAsState()

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    LaunchedEffect(subImport) {
        when (val st = subImport) {
            is LiteViewModel.SubImportState.Added -> {
                vm.resetSubImport()
                Toast.makeText(
                    context, context.getString(R.string.qr_added), Toast.LENGTH_SHORT
                ).show()
                onBack()
            }
            is LiteViewModel.SubImportState.Failed -> {
                vm.resetSubImport()
                Toast.makeText(
                    context, context.getString(st.resId), Toast.LENGTH_SHORT
                ).show()
                lastFailed.value = scanned.value
                lastFailedAt[0] = SystemClock.elapsedRealtime()
                scanned.value = null
            }
            else -> Unit
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(24.dp))
        SubScreenHeader(titleRes = R.string.qr_scan_title, onBack = onBack)
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.qr_scan_hint),
            fontSize = 14.sp,
            color = LiteColors.textSecondary,
            fontFamily = Vazirmatn,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        if (!hasPermission) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.qr_need_permission),
                    fontSize = 14.sp,
                    color = LiteColors.textSecondary,
                    fontFamily = Vazirmatn,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    providerFuture.addListener({
                        // Left the screen before the provider was ready.
                        if (disposed[0]) return@addListener
                        val provider = providerFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analyzer = ImageAnalysis.Builder()
                            .setBackpressureStrategy(
                                ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
                            )
                            .build()
                        analyzer.setAnalyzer(
                            ContextCompat.getMainExecutor(ctx)
                        ) { imageProxy ->
                            if (disposed[0]) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val mediaImage = imageProxy.image
                            if (mediaImage == null) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            val image = InputImage.fromMediaImage(
                                mediaImage, imageProxy.imageInfo.rotationDegrees
                            )
                            scanner.process(image)
                                .addOnSuccessListener { barcodes ->
                                    val value = barcodes
                                        .firstOrNull()?.rawValue?.trim()
                                    val coolingDown = value == lastFailed.value &&
                                        SystemClock.elapsedRealtime() - lastFailedAt[0] < FAILED_QR_COOLDOWN_MS
                                    if (!value.isNullOrEmpty() && scanned.value == null && !coolingDown) {
                                        scanned.value = value
                                        vm.addSubscription(value)
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        }
                        val owner = ctx as? LifecycleOwner ?: return@addListener
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                owner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analyzer
                            )
                        } catch (_: Exception) {
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 24.dp)
            )
        }
    }
}
