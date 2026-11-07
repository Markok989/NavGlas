package com.example.naviglas.ui

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.naviglas.engine.AssistantState
import com.example.naviglas.ui.theme.AccentGreen
import com.example.naviglas.ui.theme.DarkBackground
import com.example.naviglas.ui.theme.DarkSurface
import com.example.naviglas.ui.theme.DarkSurfaceVariant
import com.example.naviglas.ui.theme.TextPrimary
import com.example.naviglas.ui.theme.TextSecondary
import com.example.naviglas.ui.theme.WarningRed

@Composable
fun MainDrivingScreen(
    currentState: AssistantState,
    recognizedText: String,
    spokenFeedback: String,
    isServiceRunning: Boolean,
    onMicClick: () -> Unit,
    onQuickEtaClick: () -> Unit,
    onSosClick: () -> Unit
) {
    val isListening = currentState == AssistantState.LISTENING || currentState == AssistantState.WAITING_FOR_CONFIRMATION

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Zaglavlje statusa vožnje
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = AccentGreen,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "NaviGlas Vožnja",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = if (isServiceRunning) "Režim vožnje aktivan" else "Spremno",
                            fontSize = 14.sp,
                            color = TextSecondary
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.BluetoothConnected,
                    contentDescription = null,
                    tint = AccentGreen,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Prikaz prepoznatog teksta i povratnog izgovora
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkSurfaceVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val statusTitle = when (currentState) {
                    AssistantState.LISTENING -> "Slušam vaše komande..."
                    AssistantState.PROCESSING -> "Obrađujem..."
                    AssistantState.SPEAKING -> "Asistent govori..."
                    AssistantState.WAITING_FOR_CONFIRMATION -> "Čekam vašu potvrdu (Da / Otkaži)..."
                    AssistantState.IDLE -> "Pritisnite ili izgovorite komandu"
                }

                Text(
                    text = statusTitle,
                    fontSize = 16.sp,
                    color = AccentGreen,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (recognizedText.isNotBlank()) "\"$recognizedText\"" else spokenFeedback.ifBlank { "Npr. 'Pozovi Tijanu' ili 'Pošalji poruku Verici da stižem'" },
                    fontSize = 18.sp,
                    color = TextPrimary,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }

        // Ogromno Push-To-Talk Mikrofon Dugme
        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(if (isListening) pulseScale else 1.0f)
                .background(
                    color = if (isListening) AccentGreen else DarkSurface,
                    shape = CircleShape
                )
                .border(
                    width = 4.dp,
                    color = AccentGreen,
                    shape = CircleShape
                )
                .clickable { onMicClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Pokreni mikrofon",
                tint = if (isListening) DarkBackground else AccentGreen,
                modifier = Modifier.size(80.dp)
            )
        }

        Text(
            text = "Dugme na volanu • Dodir mehur-dugmeta • Pritisak ekrana",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )

        // Brze prečice u vožnji
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .clickable { onQuickEtaClick() },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⏱ Stižem za 10 min",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
            }

            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .clickable { onSosClick() },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = WarningRed.copy(alpha = 0.2f))
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = WarningRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ŠALJI SOS",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WarningRed
                    )
                }
            }
        }
    }
}
