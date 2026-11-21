package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.model.ClinicProfile
import java.io.File

@Composable
fun ClinicLogoAvatar(
    profile: ClinicProfile,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    shape: Shape = RoundedCornerShape(10.dp),
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val hasCustomPhoto = profile.photoUri.isNotBlank() && File(profile.photoUri).exists()

    val clickModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else {
        Modifier
    }

    Surface(
        shape = shape,
        color = Color.White,
        border = border ?: BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
            .size(size)
            .then(clickModifier)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
        ) {
            if (hasCustomPhoto) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(profile.photoUri))
                        .crossfade(true)
                        .build(),
                    contentDescription = "Doctor / Clinic Profile Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                )
            } else if (
                profile.logoType == "CUTE_TEDDY" ||
                profile.logoType == "DR_PRIYANK_LOGO" ||
                profile.logoType.isBlank()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.dr_priyank_logo_full),
                    contentDescription = "Dr Priyank Patel Clinic Logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                )
            } else {
                val icon: ImageVector = when (profile.logoType) {
                    "SHIELD" -> Icons.Default.Security
                    "SYRINGE" -> Icons.Default.Vaccines
                    "STETHOSCOPE" -> Icons.Default.MedicalServices
                    "HEART" -> Icons.Default.Favorite
                    "HOSPITAL" -> Icons.Default.LocalHospital
                    else -> Icons.Default.MedicalServices
                }
                Icon(
                    imageVector = icon,
                    contentDescription = "Clinic Logo",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(size * 0.55f)
                )
            }
        }
    }
}
