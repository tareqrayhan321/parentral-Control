package com.example.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.viewmodel.AppMode

private val ScreenBg = Color(0xFFF1EFF6)
private val Navy = Color(0xFF3B4268)
private val Purple = Color(0xFF7E5FD0)
private val Orange = Color(0xFFF99233)
private val Skin = Color(0xFFF2C4A5)

@Composable
fun DeviceRoleSelectionScreen(
    onRoleSelected: (AppMode) -> Unit
) {
    var selectedRole by remember { mutableStateOf<AppMode?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(56.dp))
        Text(
            text = "Who's going to use this device?",
            fontSize = 32.sp,
            lineHeight = 40.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Navy,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(36.dp))

        RoleCard(
            title = "Parent",
            subtitle = "This is my phone",
            selected = selectedRole == AppMode.PARENT,
            selectedColor = Purple,
            tag = "select_parent_role",
            onClick = { selectedRole = AppMode.PARENT },
            illustration = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Person(Color(0xFF2E2A57), Color(0xFF9B7BE8), longHair = true)
                    Person(Color(0xFF4B3B2E), Color(0xFF5B8DEF), longHair = false)
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        RoleCard(
            title = "Kid",
            subtitle = "This is my kid's phone",
            selected = selectedRole == AppMode.CHILD,
            selectedColor = Orange,
            tag = "select_child_role",
            onClick = { selectedRole = AppMode.CHILD },
            illustration = {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Person(Color(0xFF94455D), Color(0xFFEDC96B), longHair = true)
                    Person(Color(0xFF5E3F5E), Color(0xFFEC6E8F), longHair = false)
                }
            }
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { selectedRole?.let(onRoleSelected) },
            enabled = selectedRole != null,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .testTag("confirm_role_button"),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Purple,
                contentColor = Color.White,
                disabledContainerColor = Purple.copy(alpha = 0.35f),
                disabledContentColor = Color.White
            )
        ) {
            Text("Continue", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun RoleCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    selectedColor: Color,
    tag: String,
    onClick: () -> Unit,
    illustration: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    val textColor = if (selected) Color.White else Navy
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) selectedColor else Color.White)
            .clickable(onClick = onClick)
            .animateContentSize()
            .testTag(tag)
            .padding(start = 24.dp, end = 24.dp, top = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(title, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = textColor)
                Text(subtitle, fontSize = 24.sp, color = textColor)
            }
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(30.dp)
                    .border(2.5.dp, if (selected) Color.White else Color(0xFFCFC9E6), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (selected) {
                    Box(modifier = Modifier.size(14.dp).clip(CircleShape).background(Color.White))
                }
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentAlignment = Alignment.BottomCenter
            ) { illustration() } 
        } else {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/** Simple original character: head, hair and shoulders drawn with shapes. */
@Composable
private fun Person(hair: Color, body: Color, longHair: Boolean) {
    Canvas(
        modifier = Modifier
            .width(110.dp)
            .height(130.dp)
            .clipToBounds()
    ) {
        val w = size.width
        val h = size.height
        // shoulders
        drawRoundRect(
            color = body,
            topLeft = Offset(w * 0.05f, h * 0.62f),
            size = Size(w * 0.9f, h * 0.7f),
            cornerRadius = CornerRadius(w * 0.4f)
        )
        // long hair behind the head
        if (longHair) {
            drawRoundRect(
                color = hair,
                topLeft = Offset(w * 0.14f, h * 0.08f),
                size = Size(w * 0.72f, h * 0.66f),
                cornerRadius = CornerRadius(w * 0.36f)
            )
        }
        // face
        drawCircle(Skin, radius = w * 0.29f, center = Offset(w * 0.5f, h * 0.38f))
        // hair cap
        drawArc(
            color = hair,
            startAngle = 195f,
            sweepAngle = 150f,
            useCenter = true,
            topLeft = Offset(w * 0.5f - w * 0.31f, h * 0.38f - w * 0.31f),
            size = Size(w * 0.62f, w * 0.62f)
        )
        // eyes
        val eye = Color(0xFF3B4268)
        drawCircle(eye, radius = w * 0.025f, center = Offset(w * 0.40f, h * 0.41f))
        drawCircle(eye, radius = w * 0.025f, center = Offset(w * 0.60f, h * 0.41f))
        // smile
        drawArc(
            color = Color(0xFFB5654B),
            startAngle = 20f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(w * 0.43f, h * 0.43f),
            size = Size(w * 0.14f, w * 0.10f),
            style = Stroke(width = w * 0.018f)
        )
    }
}
