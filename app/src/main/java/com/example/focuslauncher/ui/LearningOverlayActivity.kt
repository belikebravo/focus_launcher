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
import com.example.focuslauncher.ui.common.QuizCard
import com.example.focuslauncher.ui.common.QuizState

class LearningOverlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val targetPackage = intent.getStringExtra("TARGET_PACKAGE")
        val requiredTimeSeconds = intent.getLongExtra("REQUIRED_TIME_SECONDS", 10L)
        
        if (targetPackage == null) {
            finish()
            return
        }

        val prefs = getSharedPreferences("focus_settings", android.content.Context.MODE_PRIVATE)
        val isQuizMode = prefs.getBoolean("quiz_mode_enabled", false)
        val repository = KnowledgeRepository.getInstance(this)

        setContent {
            FocusLauncherTheme {
                // Multi-stage loading strategy
                var displayNugget by remember { mutableStateOf<KnowledgeNugget?>(null) }
                var displayQuiz by remember { mutableStateOf<QuizQuestion?>(null) }
                
                // Initialize Data
                LaunchedEffect(Unit) {
                    if (isQuizMode) {
                        var q = repository.getRandomQuiz()
                        if (q == null) {
                            try { repository.refreshNuggets() } catch(e: Exception) {}
                            q = repository.getRandomQuiz()
                        }
                        displayQuiz = q
                    } else {
                         var n = repository.getRandomNugget()
                         if (n.id == "fallback" || n.id == "loading") {
                             try { repository.refreshNuggets() } catch(e: Exception) {}
                             val flowNugget = repository.currentNugget.value // Try instant access or listen flow? 
                             // Simplify: just re-get random
                             n = repository.getRandomNugget()
                         }
                         displayNugget = n
                    }
                }

                LearningOverlayScreen(
                    targetPackage = targetPackage ?: "Unknown",
                    nugget = displayNugget,
                    quiz = displayQuiz,
                    isQuizMode = isQuizMode,
                    totalSeconds = requiredTimeSeconds,
                    onContinue = {
                        val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)
                        if (launchIntent != null) {
                            startActivity(launchIntent)
                        }
                        finish()
                    },
                    onNext = {
                        if (isQuizMode) {
                            displayQuiz = repository.getRandomQuiz()
                        } else {
                            displayNugget = repository.getRandomNugget()
                        }
                    },
                    onCancel = { finish() }
                )
            }
        }
    }
}

@Composable
fun LearningOverlayScreen(
    targetPackage: String,
    nugget: KnowledgeNugget?,
    quiz: QuizQuestion?,
    isQuizMode: Boolean,
    totalSeconds: Long,
    onContinue: () -> Unit,
    onNext: () -> Unit,
    onCancel: () -> Unit
) {
    var secondsLeft by remember { mutableStateOf(totalSeconds) }
    var canProceed by remember { mutableStateOf(false) }
    
    // Quiz State
    var quizState by remember(quiz) { mutableStateOf(QuizState()) }

    LaunchedEffect(Unit) {
        val timer = object : CountDownTimer(totalSeconds * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                secondsLeft = (millisUntilFinished / 1000) + 1
            }

            override fun onFinish() {
                secondsLeft = 0
                if (!isQuizMode) {
                    canProceed = true // Only auto-unlock for Nuggets? 
                    // Or maintain timer for Quiz too, but prioritize correctness?
                    // User said "instead of old knowledge mode". 
                    // Let's say: Timer is a fallback, but Correct Answer is instant.
                    canProceed = true
                } else {
                     // In Quiz Mode, maybe timer finishes but we still require answer?
                     // Or just allow skip after timer?
                     // "Wait display": usually implies wait time.
                     // Let's allow proceed after timer OR correct answer.
                     canProceed = true 
                }
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
            // SCROLLABLE CONTENT
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                
                if (isQuizMode) {
                     if (quiz != null) {
                         QuizCard(
                             quiz = quiz,
                             uiState = quizState,
                             onOptionSelected = { idx -> 
                                 if (!quizState.isAnswered) {
                                     val isCorrect = idx == quiz.correctAnswerIndex
                                     quizState = QuizState(idx, true, isCorrect)
                                     if (isCorrect) {
                                         canProceed = true
                                     }
                                 }
                             },
                             onNext = onNext
                         )
                     } else {
                         Text("Loading Quiz...", color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
                     }
                } else if (nugget != null) {
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
                } else {
                     Text("Loading Wisdom...", color = Color.Gray, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                
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
