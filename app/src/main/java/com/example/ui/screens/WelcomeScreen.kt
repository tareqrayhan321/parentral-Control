package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val WelcomeBg = Color(0xFFF1F3FF)
private val Ink = Color(0xFF1F2233)
private val InkMuted = Color(0xFF4A4F63)
private val Accent = Color(0xFF4A6CF7)

private data class WelcomePage(
    val icon: ImageVector,
    val blob: Color,
    val title: String,
    val body: String
)

private val welcomePages = listOf(
    WelcomePage(
        Icons.Default.Shield, Color(0xFFDDE3FF),
        "Remotely Manage Your Child's Device",
        "Set rules and see how your child's phone is used, right from your own phone."
    ),
    WelcomePage(
        Icons.Default.Schedule, Color(0xFFFFE9CC),
        "Daily Screen Time Limits",
        "Choose how long each app can be used every day. Rules apply automatically."
    ),
    WelcomePage(
        Icons.Default.Block, Color(0xFFFFDCDC),
        "Block Apps Instantly",
        "Stop distracting apps with one tap, or only during study and bed time."
    ),
    WelcomePage(
        Icons.Default.History, Color(0xFFDDF3E4),
        "Bedtime & Study Schedules",
        "Create schedules that lock the phone when it is time to rest or learn."
    ),
    WelcomePage(
        Icons.Default.Language, Color(0xFFE6DDFB),
        "Safer Internet",
        "Turn on a DNS filter to keep harmful websites away from your child."
    ),
    WelcomePage(
        Icons.Default.QrCode2, Color(0xFFD9F0F5),
        "Pair in Seconds",
        "Scan one QR code on your child's phone to link both devices."
    )
)

@Composable
fun WelcomeScreen(
    isSigningIn: Boolean,
    onGoogleSignIn: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { welcomePages.size })

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WelcomeBg)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { index ->
            val page = welcomePages[index]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Illustration(page.icon, page.blob)
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    text = page.title,
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Ink,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = page.body,
                    fontSize = 16.sp,
                    lineHeight = 23.sp,
                    color = InkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        }

        // Page dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 20.dp)
        ) {
            repeat(welcomePages.size) { i ->
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (i == pagerState.currentPage) Accent else Color(0xFFD5D9E8))
                )
            }
        }

        OutlinedButton(
            onClick = onGoogleSignIn,
            enabled = !isSigningIn,
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .testTag("welcome_google_sign_in"),
            shape = RoundedCornerShape(30.dp),
            border = BorderStroke(1.5.dp, Color(0xFFDADDEA))
        ) {
            if (isSigningIn) {
                Text("Signing in…", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = InkMuted)
            } else {
                Text(text = googleG(), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(14.dp))
                Text("Continue with Google", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = InkMuted)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = buildAnnotatedString {
                append("By continuing, you agree to the ")
                withStyle(SpanStyle(color = Accent)) { append("Terms of Service") }
                append(" and ")
                withStyle(SpanStyle(color = Accent)) { append("Privacy Policy") }
            },
            fontSize = 13.sp,
            color = Color(0xFF7A7F94),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 28.dp)
        )
    }
}

private fun googleG() = buildAnnotatedString {
    withStyle(SpanStyle(color = Color(0xFF4285F4))) { append("G") }
}

/** Simple original illustration: soft blob, a few confetti dots and a big icon in a rounded badge. */
@Composable
private fun Illustration(icon: ImageVector, blob: Color) {
    Box(modifier = Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(blob, radius = size.minDimension * 0.46f)
            drawCircle(Color(0xFFFF6FA5), radius = 7.dp.toPx(), center = Offset(size.width * 0.82f, size.height * 0.22f))
            drawCircle(Color(0xFFFFB400), radius = 9.dp.toPx(), center = Offset(size.width * 0.14f, size.height * 0.30f))
            drawCircle(Color(0xFF3DBE7A), radius = 6.dp.toPx(), center = Offset(size.width * 0.20f, size.height * 0.82f))
            drawCircle(Accent, radius = 5.dp.toPx(), center = Offset(size.width * 0.86f, size.height * 0.74f))
        }
        Box(
            modifier = Modifier
                .size(116.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Color.White),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(64.dp))
        }
    }
}
