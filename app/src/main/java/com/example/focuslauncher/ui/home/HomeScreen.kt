package com.example.focuslauncher.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.example.focuslauncher.ui.common.QuizCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.sp
import com.example.focuslauncher.data.AppModel
import com.example.focuslauncher.ui.util.launchApp

@OptIn(ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val hasNotificationPermission = remember(context) {
        val packageName = context.packageName
        val listeners = android.provider.Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        listeners != null && listeners.contains(packageName)
    }
    
    // Location permission check removed as Weather is removed.
    
    // State for the long-press dialog
    var selectedApp by remember { androidx.compose.runtime.mutableStateOf<AppModel?>(null) }

    if (selectedApp != null) {
        val app = selectedApp!!
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selectedApp = null },
            title = { Text(text = app.label) },
            text = { Text("Choose an action for ${app.label}") },
            confirmButton = {
                Column {
                    androidx.compose.material3.TextButton(
                        onClick = {
                            viewModel.onToggleBlocked(app)
                            selectedApp = null
                        }
                    ) {
                        Text(if (app.isBlocked) "Unblock" else "Block")
                    }
                    androidx.compose.material3.TextButton(
                        onClick = {
                            val packageUri = Uri.parse("package:${app.packageName}")
                            val uninstallIntent = Intent(Intent.ACTION_DELETE, packageUri)
                            context.startActivity(uninstallIntent)
                            selectedApp = null
                        }
                    ) {
                        Text("Uninstall", color = Color.Red)
                    }
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel.onAppLongClick(app) // Toggle Favorite
                        selectedApp = null
                    }
                ) {
                    Text(if (app.isFavorite) "Unfavorite" else "Favorite")
                }
            }
        )
    }

    // Check if default launcher
    val isDefaultLauncher = remember(context) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolveInfo = context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        resolveInfo?.activityInfo?.packageName == context.packageName
    }

    val focusRequester = remember { FocusRequester() }
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(initialPage = 0) { 2 }
    
    // Auto-focus keyboard ONLY when on the App List page (Page 1)
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 1) {
            focusRequester.requestFocus()
        }
    }

    Box(modifier = modifier.fillMaxSize().padding(top = 16.dp)) {
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
        if (page == 0) {
            // PAGE 0: Knowledge Hub
            val nugget by viewModel.currentNugget.collectAsState()
            val allNuggets by viewModel.allNuggets.collectAsState()
            val refreshState by viewModel.refreshState.collectAsState()
            val selectedTopics by viewModel.selectedTopics.collectAsState()
            val savedApiKey by viewModel.geminiApiKey.collectAsState(initial = "")
            
            val isQuizMode by viewModel.isQuizModeEnabled.collectAsState(initial = false)
            val currentQuiz by viewModel.currentQuiz.collectAsState()
            
            // UI State for this page
            var viewMode by remember { androidx.compose.runtime.mutableStateOf("card") } // "card" or "list"
            var newTopicText by remember { androidx.compose.runtime.mutableStateOf("") } 

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {

                // HEADER
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Knowledge Hub",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row {
                        // Config Button -> Launches Unified Settings
                        androidx.compose.material3.IconButton(onClick = { 
                            val intent = Intent(context, com.example.focuslauncher.ui.settings.SettingsActivity::class.java)
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.Settings, contentDescription = "Configure", tint = Color.Gray)
                        }
                        // Refresh Button
                         androidx.compose.material3.IconButton(onClick = { viewModel.refreshNugget() }) {
                             if (refreshState == "Loading") {
                                 CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                             } else {
                                 Icon(
                                     imageVector = Icons.Default.Refresh,
                                     contentDescription = "Refresh",
                                     tint = Color.White
                                 )
                             }
                        }
                    }
                }
                
                // CONFIG SECTION MOVED TO SETTINGS ACTIVITY
                
                // VIEW TOGGLE
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text(
                        text = "Card View",
                        color = if (viewMode == "card") Color.White else Color.Gray,
                        fontWeight = if (viewMode == "card") FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clickable { viewMode = "card" }.padding(end = 16.dp)
                    )
                    Text(
                        text = "Library (${allNuggets.size})",
                         color = if (viewMode == "list") Color.White else Color.Gray,
                        fontWeight = if (viewMode == "list") FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.clickable { viewMode = "list" }
                    )
                }
                
                // DATA EMPTY STATE
                if (allNuggets.isEmpty() && refreshState != "Loading") {
                     Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                         Text("No knowledge yet.\nAdd a Topic or API Key above.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.Gray)
                     }
                } else {

                    if (viewMode == "card") {
                        // --- CARD VIEW ---
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.verticalScroll(rememberScrollState())
                            ) {
                                if (isQuizMode) {
                                    if (currentQuiz != null) {
                                        QuizCard(
                                            quiz = currentQuiz!!,
                                            uiState = uiState.quizState,
                                            onOptionSelected = { idx -> viewModel.submitAnswer(idx, currentQuiz!!.correctAnswerIndex) },
                                            onNext = { viewModel.nextQuiz() }
                                        )
                                    } else {
                                        // Quiz Mode on, but no quizzes yet?
                                        if (refreshState == "Loading") {
                                             Text("Fetching Quizzes...", color = Color.Gray)
                                        } else {
                                             Text("No quizzes found.\nCheck API Key or Connection.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.Gray)
                                        }
                                    }
                                } else if (nugget != null) {
                                    Text(
                                        text = nugget!!.topic.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        letterSpacing = 2.sp
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = nugget!!.shortText,
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        lineHeight = 36.sp,
                                        modifier = Modifier.clickable { viewModel.nextNugget() } 
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Text(
                                        text = nugget!!.detailedText,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color.LightGray,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        lineHeight = 24.sp
                                    )
                                } else if (refreshState == "Loading") {
                                    Text("Fetching Wisdom...", color = Color.Gray)
                                } 
                            }
                            
                            // Bottom Controls (Card View Only)
                             Column(
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                            ) {
                                Row {
                                    if (isQuizMode) {
                                         // In Quiz Mode, "Next" logic is inside the QuizCard usually, 
                                         // but we can put a "Skip" button here if unanswered?
                                         // Or hide these controls.
                                         if (uiState.quizState.isAnswered) {
                                             androidx.compose.material3.TextButton(onClick = { viewModel.nextQuiz() }) { Text("Next Question >", color = Color.White) }
                                         } else {
                                             androidx.compose.material3.TextButton(onClick = { viewModel.nextQuiz() }) { Text("Skip >", color = Color.Gray) }
                                         }
                                    } else {
                                        androidx.compose.material3.TextButton(onClick = { viewModel.previousNugget() }) { Text("< Prev", color = Color.Gray) }
                                        Spacer(modifier = Modifier.width(32.dp))
                                        androidx.compose.material3.TextButton(onClick = { viewModel.nextNugget() }) { Text("Next >", color = Color.White) }
                                    }
                                }
                            }
                        }
                    } else {
                        // --- LIST VIEW ---
                        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            items(allNuggets) { item ->
                                Column(modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .background(Color(0xFF1E1E1E), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .padding(16.dp)
                                ) {
                                    Text(item.topic.displayName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Text(item.shortText, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(item.detailedText, style = MaterialTheme.typography.bodyMedium, color = Color.LightGray)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // PAGE 1: App List & Tools (Existing UI)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 48.dp, start = 24.dp, end = 24.dp)
            ) {
                // Focus Header removed here as it's cleaner without it on the list page
                // Text(text = "Focus.", ...) 
                
        // ... (Rest of existing UI logic continues below) ...
        
        // Toggles moved to Settings Screen
        
        // Deep Focus Status/Button
        val deepFocusEndTime by viewModel.deepFocusEndTime.collectAsState(initial = 0L)
        val currentTime = System.currentTimeMillis()
        val isDeepFocusActive = currentTime < deepFocusEndTime
        
        // Restore Usage Permission Check
        val appOps = context.getSystemService(android.app.AppOpsManager::class.java)
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        val hasUsagePermission = mode == android.app.AppOpsManager.MODE_ALLOWED
        
        // Permission Warning
        if (!hasUsagePermission) {
             androidx.compose.material3.Card(
                colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable {
                        val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        context.startActivity(intent)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Settings, contentDescription = "Permission", tint = Color.White)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Permission Required", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Tap to enable 'Usage Access' for blocking.", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        
        // Monitor Service Logic
        // Always run the service to enforce Strict Blocking rules (Social Apps) even if Focus Mode is off.
        val shouldRunService = true 
        
        LaunchedEffect(shouldRunService) {
            val intent = Intent(context, com.example.focuslauncher.service.AppBlockingService::class.java)
            if (shouldRunService) {
                context.startForegroundService(intent)
            }
        }
        
        if (isDeepFocusActive) {
            val minutesLeft = ((deepFocusEndTime - currentTime) / 60000).coerceAtLeast(1)
            Text(
                text = "Deep Focus: $minutesLeft m left",
                color = Color.Red,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        } else {
            var showTimerDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
            
            androidx.compose.material3.Button(
                 onClick = { 
                     if (!hasUsagePermission) {
                         android.widget.Toast.makeText(context, "Deep Focus requires Usage Access", android.widget.Toast.LENGTH_LONG).show()
                         val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                         context.startActivity(intent)
                     } else {
                         showTimerDialog = true 
                     }
                 },
                 modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                 colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                 Text("Start Deep Focus (30m)")
            }
            
            if (showTimerDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showTimerDialog = false },
                    title = { Text("Deep Focus") },
                    text = { Text("Block ALL non-favorite apps for 30 minutes? You cannot undo this.") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(
                            onClick = {
                                viewModel.setDeepFocus(30)
                                showTimerDialog = false
                            }
                        ) {
                            Text("Start")
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showTimerDialog = false }) { Text("Cancel") }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        
        // Set Default Launcher Button (Only if NOT default)
        if (!isDefaultLauncher) {
            androidx.compose.material3.Button(
                 onClick = {
                     val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                     context.startActivity(intent)
                 },
                 modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                 Text("Set as Default Launcher")
            }
        }

        // Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            if (uiState.searchQuery.isEmpty()) {
                Text(
                    text = "Search apps...",
                    style = TextStyle(color = Color.Gray, fontSize = 24.sp)
                )
            }
            BasicTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChange(it) },
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 24.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { 
                     // Launch top app? 
                     if (uiState.filteredApps.isNotEmpty()) {
                         val app = uiState.filteredApps[0]
                         val launchIntent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                         context.startActivity(launchIntent)
                     }
                }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        }

        Divider(color = Color.DarkGray)
        
        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onBackground)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Favorites Section (Only when search is empty)
                if (uiState.searchQuery.isEmpty()) {
                    if (uiState.favorites.isNotEmpty()) {
                        item {
                            Text(
                                text = "Quick Access",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        items(uiState.favorites, key = { "fav_${it.packageName}_${it.isWork}" }) { app ->
                            AppItem(app = app, onClick = { launchApp(context, app) }, onLongClick = { selectedApp = app })
                        }
                    }
                    
                    if (uiState.highUsageApps.isNotEmpty()) {
                        item {
                             Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))
                             Text(
                                text = "Detox Recommended (>1h Today)",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                         items(uiState.highUsageApps, key = { "high_${it.packageName}_${it.isWork}" }) { app ->
                            AppItem(app = app, onClick = { launchApp(context, app) }, onLongClick = { selectedApp = app })
                        }
                    }
                    
                    if (uiState.dustyApps.isNotEmpty()) {
                        item {
                             Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 8.dp))
                             Text(
                                text = "Dusty Apps (Unused >30 Days)",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                         items(uiState.dustyApps, key = { "dust_${it.packageName}_${it.isWork}" }) { app ->
                            AppItem(app = app, onClick = { launchApp(context, app) }, onLongClick = { selectedApp = app })
                        }
                    }
                }

                // All Apps / Filtered Results
                items(uiState.filteredApps, key = { "${it.packageName}_${it.isWork}" }) { app ->
                    AppItem(
                        app = app, 
                        onClick = {
                            launchApp(context, app)
                        },
                        onLongClick = {
                             selectedApp = app
                        }
                    )
                }
            }
        }
            } // Close Column
        } // Close else
    } // Close HorizontalPager
        
    // Settings Button (Top Right) - Only show on App List (Page 1)
    if (pagerState.currentPage == 1) {
        androidx.compose.material3.IconButton(
            onClick = {
                val intent = Intent(context, com.example.focuslauncher.ui.settings.SettingsActivity::class.java)
                context.startActivity(intent)
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings",
                tint = Color.White
            )
        }
    }
    } // Close Box
} // Close HomeScreen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppItem(
    app: AppModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Text Only Interface
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge.copy(
                textDecoration = if (app.isBlocked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                color = if (app.isBlocked) Color.Gray else MaterialTheme.colorScheme.onBackground
            ),
            fontSize = 20.sp
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        if (app.usageDuration > 0) {
            val minutes = app.usageDuration / 1000 / 60
            if (minutes > 0) {
                 Text(
                    text = "${minutes}m",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (minutes > 60) Color.Red else Color.Gray
                )
            }
        }
        
        if (app.isFavorite) {
            Spacer(modifier = Modifier.size(8.dp))
            Icon(
                imageVector = Icons.Default.Star, 
                contentDescription = "Favorite",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

// QuizCard moved to com.example.focuslauncher.ui.common
