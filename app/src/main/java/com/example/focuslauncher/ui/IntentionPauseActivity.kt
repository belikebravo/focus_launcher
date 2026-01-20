package com.example.focuslauncher.ui

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focuslauncher.ui.theme.FocusLauncherTheme

class IntentionPauseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val targetPackage = intent.getStringExtra("TARGET_PACKAGE")
        val targetLabel = intent.getStringExtra("TARGET_LABEL") ?: "App"

        if (targetPackage == null) {
            finish()
            return
        }

        setContent {
            FocusLauncherTheme {
                IntentionPauseScreen(
                    label = targetLabel,
                    onContinue = {
                        val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                        }
                        finish()
                    },
                    onCancel = {
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun IntentionPauseScreen(
    label: String,
    onContinue: () -> Unit,
    onCancel: () -> Unit
) {
    var secondsLeft by remember { mutableStateOf(5) }
    var canProceed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val timer = object : CountDownTimer(5000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                secondsLeft = (millisUntilFinished / 1000).toInt() + 1
            }

            override fun onFinish() {
                secondsLeft = 0
                canProceed = true
            }
        }
        timer.start()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "Wait.",
                color = Color.White,
                fontSize = 48.sp,
                style = MaterialTheme.typography.displayLarge
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Take a deep breath.\nDo you really need to open $label?",
                color = Color.LightGray,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                lineHeight = 28.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            if (canProceed) {
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onBackground)
                ) {
                    Text("Yes, I have a purpose", color = MaterialTheme.colorScheme.background)
                }
            } else {
                Text(
                    text = "$secondsLeft",
                    color = Color.Gray,
                    fontSize = 32.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            androidx.compose.material3.TextButton(onClick = onCancel) {
                Text("Never mind", color = Color.Gray)
            }
        }
    }
}
