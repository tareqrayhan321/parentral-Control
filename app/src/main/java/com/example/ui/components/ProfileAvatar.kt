package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.example.core.profile.ProfilePhotoLoader

private val FallbackColors = listOf(
    Color(0xFF0E7C6B), Color(0xFF2F7FB8), Color(0xFF8A5CD0), Color(0xFFD2541B), Color(0xFF1E9E6A)
)

/**
 * Round profile avatar: shows the Google profile photo when available, otherwise a gradient
 * circle with the user's initial (also shown while the photo is loading or if it fails).
 */
@Composable
fun ProfileAvatar(
    photoUrl: String?,
    name: String?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val photo by produceState<Bitmap?>(initialValue = null, photoUrl) {
        value = if (photoUrl.isNullOrBlank()) null
        else ProfilePhotoLoader.load(context.applicationContext, photoUrl)
    }

    val label = name?.trim().orEmpty()
    val initial = label.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
    val base = FallbackColors[(label.lowercase().hashCode() and Int.MAX_VALUE) % FallbackColors.size]

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(base.copy(alpha = 0.85f), base))),
        contentAlignment = Alignment.Center
    ) {
        val bmp = photo
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Profile photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size)
            )
        } else {
            Text(
                text = initial,
                color = Color.White,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
