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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focuslauncher.data.knowledge.*
import com.example.focuslauncher.data.knowledge.KnowledgeRepository
import com.example.focuslauncher.ui.theme.FocusLauncherTheme

class LearningOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val targetPackage = intent.getStringExtra("TARGET_PACKAGE")
        val requiredTimeSeconds = intent.getLongExtra("REQUIRED_TIME_SECONDS", 10L)
        
        if (targetPackage == null) {
            finish()
            return
        }

        val repository = KnowledgeRepository.getInstance(this)

        setContent {
            FocusLauncherTheme {
                // Multi-stage loading strategy for Speed + Variety:
                // 1. Try to get a random cached nugget IMMEDIATELY (Synchronous)
                var displayNugget by remember { mutableStateOf(repository.getRandomNugget()) }
                
                // 2. If the random fetch gave us a fallback ("loading"), it means cache is empty.
                // In that case, we MUST observe the network flow and trigger a refresh.
                if (displayNugget.id == "fallback" || displayNugget.id == "loading") {
                    val networkNugget by repository.currentNugget.collectAsState(initial = null)
                    
                    LaunchedEffect(networkNugget) {
                        if (networkNugget != null) {
                            displayNugget = networkNugget!!
                        }
                    }
                    
                    LaunchedEffect(Unit) {
                        try {
                            repository.refreshNuggets()
                        } catch (e: Exception) {
                            // Run silently or log error
                            e.printStackTrace()
                        }
                    }
                }
                
                // 3. Optional: Trigger a background refresh even if we have cache, to keep content fresh?
                // For now, let's stick to the user's request for "speed" and "shuffle".
                // Existing cache is fast. No need to wait for network if we have data.

                LearningOverlayScreen(
                    targetPackage = targetPackage ?: "Unknown",
                    nugget = displayNugget,
                    totalSeconds = requiredTimeSeconds,
                    onContinue = {
                        val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                        }
                        finish()
                    },
                    onNext = {
                        displayNugget = repository.getRandomNugget()
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
fun LearningOverlayScreen(
    targetPackage: String,
    nugget: com.example.focuslauncher.data.knowledge.KnowledgeNugget,
    totalSeconds: Long,
    onContinue: () -> Unit,
    onNext: () -> Unit,
    onCancel: () -> Unit
) {
    var secondsLeft by remember { mutableStateOf(totalSeconds) }
    var canProceed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val timer = object : CountDownTimer(totalSeconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                secondsLeft = (millisUntilFinished / 1000) + 1
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
            .background(Color(0xFF121212)), // Dark background
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
        ) {
            // SCROLLABLE CONTENT (Takes all available space above button)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = "Knowledge Nugget",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = nugget.topic.displayName,
                    color = Color.Gray,
                    style = MaterialTheme.typography.titleMedium
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = nugget.shortText,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.headlineMedium
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    text = nugget.detailedText,
                    color = Color.LightGray,
                    fontSize = 18.sp,
                    lineHeight = 28.sp,
                    textAlign = TextAlign.Start,
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Spacer(modifier = Modifier.height(32.dp))
            }
            
            // FIXED BOTTOM BAR (Button)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Next Button
                androidx.compose.material3.OutlinedButton(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Next Question ->")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            
                if (canProceed) {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Green) // Green for Go
                    ) {
                        Text("Continue to App", fontSize = 18.sp, color = Color.Black) 
                    }
                } else {
                    Text(
                        text = "Read to Unlock: ${secondsLeft}s",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                     Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.DarkGray,
                            disabledContainerColor = Color.DarkGray
                        )
                    ) {
                        Text("Reading...", color = Color.Gray)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                androidx.compose.material3.TextButton(onClick = onCancel) {
                    Text("Give Up", color = Color.Gray) // "Give Up" is stronger than Cancel? Or stick to Cancel. "Give Up" fits the "Strict" theme.
                }
            }
        }
    }
}
