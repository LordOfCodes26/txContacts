package com.goodwy.commons.compose.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.goodwy.commons.models.contacts.ContactPosterData
import com.goodwy.commons.models.contacts.PosterTextStyle

/**
 * iOS-style Contact Poster composable.
 * Displays a full-bleed background image with gradient overlay and large contact name text.
 *
 * @param posterData The poster configuration (image URI, scale, offset, text style)
 * @param contactName The contact's display name
 * @param subtitle Optional subtitle (e.g., phone label)
 * @param modifier Modifier for the composable
 */
@OptIn(ExperimentalGlideComposeApi::class)
@Composable
fun ContactPoster(
    posterData: ContactPosterData?,
    contactName: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Background image with transform
        if (posterData != null && posterData.imageUri.isNotEmpty()) {
            val imageUri = Uri.parse(posterData.imageUri)
            GlideImage(
                model = imageUri,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (posterData.scale != 1.0f || posterData.offsetX != 0.0f || posterData.offsetY != 0.0f) {
                            Modifier
                                .graphicsLayer {
                                    scaleX = posterData.scale
                                    scaleY = posterData.scale
                                    translationX = posterData.offsetX
                                    translationY = posterData.offsetY
                                }
                        } else {
                            Modifier
                        }
                    ),
                contentScale = ContentScale.Crop
            )
        } else {
            // Fallback gradient background when no image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    )
            )
        }

        // Gradient overlay for text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.0f),
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.6f)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        // Contact name and subtitle
        PosterTextContent(
            contactName = contactName,
            subtitle = subtitle,
            textStyle = posterData?.textStyle,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 24.dp, vertical = 48.dp)
        )
    }
}

@Composable
private fun BoxScope.PosterTextContent(
    contactName: String,
    subtitle: String?,
    textStyle: PosterTextStyle?,
    modifier: Modifier = Modifier
) {
    val textColor = textStyle?.let { Color(it.textColor) } ?: Color.White
    val fontSize = textStyle?.fontSize ?: 48f
    val fontWeight = when (textStyle?.fontWeight ?: 700) {
        in 100..300 -> FontWeight.Light
        in 400..500 -> FontWeight.Normal
        in 600..700 -> FontWeight.SemiBold
        else -> FontWeight.Bold
    }
    val textAlign = when (textStyle?.textAlignment ?: "start") {
        "center" -> TextAlign.Center
        "end" -> TextAlign.End
        else -> TextAlign.Start
    }

    Box(modifier = modifier) {
        Text(
            text = contactName,
            color = textColor,
            fontSize = fontSize.sp,
            fontWeight = fontWeight,
            textAlign = textAlign,
            style = MaterialTheme.typography.displayLarge.copy(
                fontWeight = fontWeight,
                letterSpacing = (-0.5).sp
            )
        )

        subtitle?.let {
            Text(
                text = it,
                color = textColor.copy(alpha = 0.9f),
                fontSize = (fontSize * 0.5f).sp,
                fontWeight = FontWeight.Normal,
                textAlign = textAlign,
                modifier = Modifier.padding(top = (fontSize * 0.7f).dp)
            )
        }
    }
}
