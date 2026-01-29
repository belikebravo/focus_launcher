package com.example.focuslauncher.ui.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focuslauncher.data.knowledge.Topic
import com.example.focuslauncher.ui.theme.FocusLauncherTheme

class SettingsActivity : ComponentActivity() {
    private val viewModel: SettingsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FocusLauncherTheme {
                SettingsScreen(
                    viewModel = viewModel,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {

    val selectedTopics by viewModel.selectedTopics.collectAsState()
    val appList by viewModel.appListState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            item {
                Text(
                    text = "General Settings",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            item {
                 // Focus Mode
                 val isFocus by viewModel.isFocusModeEnabled.collectAsState(initial = false)
                 Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Focus Mode (Block Notifications)", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Switch(checked = isFocus, onCheckedChange = { viewModel.toggleFocusMode(it) })
                }
            }
            
            item {
                // Wait Time
                val minutes by viewModel.focusWaitTimeMinutes.collectAsState(initial = 5)
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        text = "Strict App Wait Time: $minutes min",
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "For Social Media & Restricted Apps",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Slider(
                        value = minutes.toFloat(),
                        onValueChange = { viewModel.setFocusWaitTimeMinutes(it.toInt()) },
                        valueRange = 1f..60f, // 1 to 60 minutes
                        steps = 58,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                }

                // Standard Wait Time
                val standardSeconds by viewModel.standardWaitTimeSeconds.collectAsState(initial = 10)
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        text = "Standard Friction: $standardSeconds sec",
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "For normal non-work apps",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                    Slider(
                        value = standardSeconds.toFloat(),
                        onValueChange = { viewModel.setStandardWaitTimeSeconds(it.toInt()) },
                        valueRange = 5f..120f, // 5s to 2 mins
                        steps = 22,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.DarkGray
                        )
                    )
                }
            }
            
            item {
                Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 24.dp))
                Text(
                     text = "Knowledge Settings",
                     color = MaterialTheme.colorScheme.primary,
                     style = MaterialTheme.typography.titleMedium,
                     modifier = Modifier.padding(vertical = 8.dp)
                )
                
                // API Key
                var apiKeyInput by remember { mutableStateOf("") }
                val currentKey by viewModel.geminiApiKey.collectAsState(initial = "")
                val verifyState by viewModel.verificationState.collectAsState()
                
                val context = androidx.compose.ui.platform.LocalContext.current
                
                // Link to get API Key (Styled as Chip)
                androidx.compose.material3.SuggestionChip(
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://aistudio.google.com/app/apikey"))
                        context.startActivity(intent)
                    },
                    label = { Text("Get Free API Key", color = Color(0xFF4285F4)) },
                    border = androidx.compose.material3.AssistChipDefaults.assistChipBorder(borderColor = Color(0xFF4285F4))
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedTextField(
                    value = if (apiKeyInput.isEmpty() && currentKey.isNotEmpty()) currentKey else apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("Gemini API Key") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    )
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = { viewModel.verifyAndRefretch(apiKeyInput.ifEmpty { currentKey }) },
                        enabled = verifyState != VerificationState.Loading
                    ) {
                        when(verifyState) {
                            VerificationState.Loading -> CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                            VerificationState.Success -> Icon(Icons.Default.Check, contentDescription = "Verified")
                            else -> Text("Verify & Save")
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Active Topics (Dynamic - matches User Selection + Custom)
                Text("Active Topics", color = Color.White, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                
                if (selectedTopics.isEmpty()) {
                    Text("No topics selected.", color = Color.Gray, fontSize = 12.sp)
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        selectedTopics.forEach { topic ->
                            InputChip(
                                selected = true,
                                onClick = { viewModel.toggleTopic(topic) },
                                label = { Text(topic.displayName) },
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Suggested Topics (Hardcoded - Filter out selected)
                val allPredefined = listOf(
                    Topic("CS", "Computer Science"),
                    Topic("HIST", "History"),
                    Topic("SCI", "Science"),
                    Topic("BIZ", "Business"),
                    Topic("LANG", "Languages"),
                    Topic("ART", "Arts"),
                    Topic("PHIL", "Philosophy"),
                    Topic("PSYCH", "Psychology")
                )
                val suggested = allPredefined.filter { pre -> selectedTopics.none { it.id == pre.id } }
                
                if (suggested.isNotEmpty()) {
                    Text("Suggested Topics", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        suggested.forEach { topic ->
                            FilterChip(
                                selected = false,
                                onClick = { viewModel.toggleTopic(topic) },
                                label = { Text(topic.displayName) },
                                leadingIcon = { Icon(Icons.Default.Add, contentDescription = "Add") }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Add Custom Topic
                var newTopicText by remember { mutableStateOf("") }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTopicText,
                        onValueChange = { newTopicText = it },
                        label = { Text("Add Custom Topic") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { 
                            viewModel.addTopic(newTopicText)
                            newTopicText = ""
                        },
                        contentPadding = PaddingValues(horizontal = 0.dp),
                        modifier = Modifier.width(48.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add")
                    }
                }
            }

            item {
                Divider(color = Color.DarkGray, modifier = Modifier.padding(vertical = 24.dp))
                Text(
                    text = "Safe Apps (Excluded)",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Text(
                    text = "These apps will open instantly without the learning friction.",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            items(appList) { app ->
                val isExcluded = app.isFavorite // Mapped to isFavorite in ViewModel for simplicity
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { viewModel.toggleAppExclusion(app.packageName) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isExcluded,
                        onCheckedChange = { viewModel.toggleAppExclusion(app.packageName) }
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(text = app.label, color = Color.White, fontSize = 16.sp)
                        Text(text = app.packageName, color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
