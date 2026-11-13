package com.paysetu.sunandanproject

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paysetu.sunandanproject.ui.theme.SunandanProjectTheme
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class SyncState {
    IDLE,
    SYNCING,
    SYNCED,
    ERROR
}

class MainActivity : ComponentActivity() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SunandanProjectTheme {
                LiveTyperScreen(httpClient = httpClient)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTyperScreen(httpClient: OkHttpClient) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // Preferences to save Room ID and Web Display URL
    val prefs = remember {
        context.getSharedPreferences("live_typer_prefs", Context.MODE_PRIVATE)
    }

    var roomId by remember {
        mutableStateOf(prefs.getString("room_id", "live-demo-room") ?: "live-demo-room")
    }

    var webBaseUrl by remember {
        mutableStateOf(
            prefs.getString("web_base_url", "https://sunandan-live.vercel.app")
                ?: "https://sunandan-live.vercel.app"
        )
    }

    var textInput by remember { mutableStateOf("") }
    var syncState by remember { mutableStateOf(SyncState.IDLE) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    var debounceJob by remember { mutableStateOf<Job?>(null) }

    // Full URL to view
    val fullDisplayUrl = remember(webBaseUrl, roomId) {
        val cleanBase = webBaseUrl.trimEnd('/')
        "$cleanBase?room=$roomId"
    }

    // Function to transmit text to the zero-maintenance cloud relay (ntfy.sh)
    fun transmitText(content: String) {
        syncState = SyncState.SYNCING
        val jsonPayload = JSONObject().apply {
            put("text", content)
            put("timestamp", System.currentTimeMillis())
        }.toString()

        val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://ntfy.sh/$roomId")
            .header("X-Title", "live-sync")
            .post(requestBody)
            .build()

        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                coroutineScope.launch(Dispatchers.Main) {
                    syncState = SyncState.ERROR
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
                coroutineScope.launch(Dispatchers.Main) {
                    syncState = SyncState.SYNCED
                }
            }
        })
    }

    // Helper to queue broadcast with smooth typing debounce (100ms)
    fun onUserType(newText: String) {
        textInput = newText
        debounceJob?.cancel()
        debounceJob = coroutineScope.launch {
            delay(100) // 100ms debounce: feels 100% instant to the user, doesn't spam network
            transmitText(newText)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Live Screen Broadcaster",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Pulsing / live status dot
                            val statusColor = when (syncState) {
                                SyncState.IDLE -> Color(0xFF9E9E9E)
                                SyncState.SYNCING -> Color(0xFF2196F3)
                                SyncState.SYNCED -> Color(0xFF4CAF50)
                                SyncState.ERROR -> Color(0xFFF44336)
                            }
                            val statusLabel = when (syncState) {
                                SyncState.IDLE -> "Ready"
                                SyncState.SYNCING -> "Streaming..."
                                SyncState.SYNCED -> "Live on Web"
                                SyncState.ERROR -> "Offline / Retrying"
                            }

                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall.copy(color = statusColor)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Room Settings"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Display URL Banner with Share Button
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Audience Display URL:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = fullDisplayUrl,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            maxLines = 1
                        )
                    }

                    Row {
                        IconButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, fullDisplayUrl)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Display URL"))
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share URL",
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            }

            // Word / Character count indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val charCount = textInput.length
                val wordCount = if (textInput.isBlank()) 0 else textInput.trim().split("\\s+".toRegex()).size
                Text(
                    text = "$wordCount words  •  $charCount chars",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (textInput.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            onUserType("")
                            Toast.makeText(context, "Display cleared", Toast.LENGTH_SHORT).show()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Clear Screen")
                    }
                }
            }

            // The Main Typing Canvas
            OutlinedTextField(
                value = textInput,
                onValueChange = { onUserType(it) },
                placeholder = {
                    Text(
                        text = "Whatever you type here appears live on the URL in real time...\n\n(Tip: Voice dictation on your keyboard works live too!)",
                        style = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.outline)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(16.dp),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Bottom Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        if (textInput.isNotEmpty()) {
                            clipboardManager.setText(AnnotatedString(textInput))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = textInput.isNotEmpty()
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy")
                }

                Button(
                    onClick = {
                        // Force an immediate sync push
                        debounceJob?.cancel()
                        transmitText(textInput)
                        Toast.makeText(context, "Force synced to web", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Sync")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Sync Now")
                }
            }
        }
    }

    // Room & URL Settings Dialog
    if (showSettingsDialog) {
        var tempRoomId by remember { mutableStateOf(roomId) }
        var tempBaseUrl by remember { mutableStateOf(webBaseUrl) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("Display Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Customize your private channel name so spectators only see your live stream.",
                        style = MaterialTheme.typography.bodySmall
                    )

                    OutlinedTextField(
                        value = tempRoomId,
                        onValueChange = { tempRoomId = it.trim().replace(" ", "-") },
                        label = { Text("Room Channel ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = tempBaseUrl,
                        onValueChange = { tempBaseUrl = it.trim() },
                        label = { Text("Web Host URL (Vercel)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        roomId = tempRoomId.ifBlank { "live-demo-room" }
                        webBaseUrl = tempBaseUrl.ifBlank { "https://sunandan-live.vercel.app" }
                        prefs.edit()
                            .putString("room_id", roomId)
                            .putString("web_base_url", webBaseUrl)
                            .apply()
                        showSettingsDialog = false
                        // Transmit to new room immediately
                        transmitText(textInput)
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
