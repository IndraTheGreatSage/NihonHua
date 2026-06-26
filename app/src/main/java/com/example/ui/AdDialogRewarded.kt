package com.example.ui

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.AdRewardManager
import kotlinx.coroutines.delay
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun RewardedAdDialog(
    onRewardedMinutesApplied: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity

    // UI state
    var statusText by remember { mutableStateOf("Memuat iklan...") }
    var isShowingError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (activity != null) {
            AdRewardManager.load(context)
        }
    }

    // Attempt show shortly after load
    LaunchedEffect(activity) {
        if (activity == null) {
            isShowingError = true
            statusText = "Activity tidak tersedia"
            return@LaunchedEffect
        }

        delay(600)
        AdRewardManager.show(
            activity = activity,
            onRewardEarned = {
                // callback to apply reward in ViewModel/repository
                onRewardedMinutesApplied()
                onDismiss()
            },
            onAdFailed = { message ->
                isShowingError = true
                statusText = message
                onDismiss()
            }
        )
    }

    Dialog(onDismissRequest = { /* force watched/handled by callbacks */ }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("PROMO STREAMING", color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Spacer(modifier = Modifier.height(12.dp))
                Text(statusText, color = Color.White.copy(alpha = 0.8f))
                Spacer(modifier = Modifier.height(16.dp))

                if (!isShowingError) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Tutup")
                    }
                }
            }
        }
    }
}

