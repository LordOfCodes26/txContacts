package com.android.contacts.compose

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.goodwy.commons.compose.components.ContactPoster
import com.goodwy.commons.models.contacts.ContactPosterData
import com.goodwy.commons.models.contacts.PosterTextStyle

/**
 * Contact Poster Editor Screen.
 * Allows users to select an image, position/scale it, and preview the poster.
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ContactPosterEditorScreen(
    contactName: String,
    subtitle: String? = null,
    initialPosterData: ContactPosterData? = null,
    onSave: (ContactPosterData) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    // State for image selection and transform
    var imageUri by remember { mutableStateOf<Uri?>(initialPosterData?.imageUri?.let { Uri.parse(it) }) }
    var scale by remember { mutableFloatStateOf(initialPosterData?.scale ?: 1.0f) }
    var offsetX by remember { mutableFloatStateOf(initialPosterData?.offsetX ?: 0f) }
    var offsetY by remember { mutableFloatStateOf(initialPosterData?.offsetY ?: 0f) }

    // Photo picker launcher
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            imageUri = it
            // Reset transform when new image is selected
            scale = 1.0f
            offsetX = 0f
            offsetY = 0f
        }
    }
    
    // Photo picker request (images only)
    val pickVisualMediaRequest = remember {
        PickVisualMediaRequest(
            ActivityResultContracts.PickVisualMedia.ImageOnly
        )
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && imageUri != null) {
            // Image already saved to imageUri
        }
    }

    // Create temporary file for camera (lazy initialization)
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    
    fun createCameraUri(): Uri? {
        if (cameraImageUri == null) {
            cameraImageUri = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "poster_${System.currentTimeMillis()}.jpg")
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            }.let {
                context.contentResolver.insert(
                    android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    it
                )
            }
        }
        return cameraImageUri
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val posterData = ContactPosterData(
                                imageUri = imageUri?.toString() ?: "",
                                scale = scale,
                                offsetX = offsetX,
                                offsetY = offsetY,
                                textStyle = initialPosterData?.textStyle ?: PosterTextStyle()
                            )
                            onSave(posterData)
                        },
                        enabled = imageUri != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Save"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            )
        },
        floatingActionButton = {
            Box {
                FloatingActionButton(
                    onClick = { photoPickerLauncher.launch(pickVisualMediaRequest) },
                    modifier = Modifier.padding(end = 80.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = "Choose from Gallery"
                    )
                }
                FloatingActionButton(
                    onClick = {
                        createCameraUri()?.let { uri ->
                            imageUri = uri
                            cameraLauncher.launch(uri)
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Take Photo"
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Live preview of the poster (background)
            val currentPosterData = ContactPosterData(
                imageUri = imageUri?.toString() ?: "",
                scale = scale,
                offsetX = offsetX,
                offsetY = offsetY,
                textStyle = initialPosterData?.textStyle ?: PosterTextStyle()
            )

            ContactPoster(
                posterData = currentPosterData,
                contactName = contactName,
                subtitle = subtitle
            )

            // Editable image overlay with gestures (semi-transparent to show text below)
            imageUri?.let { uri ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.5f, 3.0f)
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        }
                ) {
                    GlideImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                                alpha = 0.7f // Semi-transparent to see text preview
                            },
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
            }
        }
    }
}
