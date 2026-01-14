package com.android.contacts.activities

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.android.contacts.R
import com.android.contacts.databinding.ActivityScanQrcodeBinding
import com.android.contacts.helpers.VcfImporter
import com.android.contacts.extensions.config
import com.goodwy.commons.extensions.*
import com.goodwy.commons.helpers.SMT_PRIVATE
import com.goodwy.commons.helpers.ensureBackgroundThread
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanQrCodeActivity : SimpleActivity() {
    private lateinit var binding: ActivityScanQrcodeBinding
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private lateinit var cameraExecutor: ExecutorService
    private val barcodeScanner = BarcodeScanning.getClient()
    private var isProcessing = false

    companion object {
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanQrcodeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        setupToolbar()
        
        if (checkCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun setupToolbar() {
//        binding.appbar.setBackgroundColor(Color.TRANSPARENT)
//        binding.toolbar.apply {
//            val textColor = getProperTextColor()
//            navigationIcon = resources.getColoredDrawableWithColor(
//                this@ScanQrCodeActivity,
//                com.goodwy.commons.R.drawable.ic_arrow_left_vector,
//                textColor
//            )
//            setNavigationOnClickListener { finish() }
//            title = getString(R.string.scan_qr_code)
//            setTitleTextColor(textColor)
//            setSearchIconVisible(false)
//        }
    }

    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA),
            CAMERA_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                toast(com.goodwy.commons.R.string.no_camera_permissions)
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.previewView.surfaceProvider)
                }

            imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { imageProxy ->
                        processImageProxy(imageProxy)
                    }
                }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer,
                    imageCapture
                )
            } catch (exc: Exception) {
                showErrorToast(exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )

            barcodeScanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        when (barcode.valueType) {
                            Barcode.TYPE_TEXT, Barcode.TYPE_CONTACT_INFO -> {
                                val rawValue = barcode.rawValue
                                if (rawValue != null && rawValue.contains("BEGIN:VCARD")) {
                                    isProcessing = true
                                    processVCard(rawValue)
                                    return@addOnSuccessListener
                                }
                            }
                        }
                    }
                    imageProxy.close()
                }
                .addOnFailureListener {
                    imageProxy.close()
                }
                .addOnCompleteListener {
                    if (!isProcessing) {
                        imageProxy.close()
                    }
                }
        } else {
            imageProxy.close()
        }
    }

    private fun processVCard(vCardString: String) {
        runOnUiThread {
            binding.progressBar.visibility = View.VISIBLE
        }

        ensureBackgroundThread {
            try {
                // Save vCard to temporary file
                val tempFile = createTempFile("contact", ".vcf", cacheDir)
                tempFile.writeText(vCardString)

                // Import contact using VcfImporter
                val importer = VcfImporter(this@ScanQrCodeActivity)
                val targetSource = if (hasContactPermissions()) {
                    config.lastUsedContactSource.ifEmpty { SMT_PRIVATE }
                } else {
                    SMT_PRIVATE
                }
                val result = importer.importContacts(tempFile.absolutePath, targetSource)

                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    when (result) {
                        VcfImporter.ImportResult.IMPORT_OK -> {
                            toast(com.goodwy.commons.R.string.importing_successful)
                            finish()
                        }
                        VcfImporter.ImportResult.IMPORT_PARTIAL -> {
                            toast(com.goodwy.commons.R.string.importing_some_entries_failed)
                            finish()
                        }
                        VcfImporter.ImportResult.IMPORT_FAIL -> {
                            toast(com.goodwy.commons.R.string.importing_failed)
                            isProcessing = false
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    showErrorToast(e)
                    isProcessing = false
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        barcodeScanner.close()
    }
}

