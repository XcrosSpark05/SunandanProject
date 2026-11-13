package com.paysetu.sunandanproject

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paysetu.sunandanproject.ui.theme.SunandanProjectTheme
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

// DATA MODELS FOR CRICKET MATCH STATE
data class Batsman(
    val name: String,
    var runs: Int = 0,
    var balls: Int = 0,
    var fours: Int = 0,
    var sixes: Int = 0,
    var isOut: Boolean = false
)

data class Bowler(
    val name: String,
    var ballsBowled: Int = 0,
    var runsConceded: Int = 0,
    var wickets: Int = 0
) {
    val oversFormatted: String
        get() = "${ballsBowled / 6}.${ballsBowled % 6}"
}

data class MatchState(
    var battingTeam: String = "SHRI BODGESHWAR",
    var bowlingTeam: String = "SHRUTESH 11",
    var totalRuns: Int = 13,
    var totalWickets: Int = 0,
    var totalBallsBowled: Int = 6,
    var maxOvers: Int = 8,
    var tossInfo: String = "SHRUTESH 11 DECIDED TO BOWL",
    var target: Int = 0,
    
    var strikerName: String = "GAURESH S",
    var nonStrikerName: String = "AJAY YADAV",
    var currentBowlerName: String = "HARSHAD M",
    
    var batsmenList: MutableList<Batsman> = mutableListOf(),
    var bowlersList: MutableList<Bowler> = mutableListOf(),
    var currentOverBalls: MutableList<String> = mutableListOf("•", "6", "•", "WD", "•", "•", "6"),
    var lastEvent: String? = null,
    var eventId: Long = 0
) {
    val currentOversFormatted: String
        get() = "${totalBallsBowled / 6}.${totalBallsBowled % 6}"

    val crr: String
        get() {
            if (totalBallsBowled == 0) return "0.00"
            val oversFloat = (totalBallsBowled / 6) + ((totalBallsBowled % 6) / 6.0)
            if (oversFloat <= 0.0) return "0.00"
            return String.format("%.2f", totalRuns / oversFloat)
        }

    val projectedScore: Int
        get() {
            if (totalBallsBowled == 0) return 0
            val oversFloat = (totalBallsBowled / 6) + ((totalBallsBowled % 6) / 6.0)
            if (oversFloat <= 0.0) return 0
            val runRate = totalRuns / oversFloat
            return (runRate * maxOvers).toInt()
        }

    fun deepCopy(): MatchState {
        return MatchState(
            battingTeam = this.battingTeam,
            bowlingTeam = this.bowlingTeam,
            totalRuns = this.totalRuns,
            totalWickets = this.totalWickets,
            totalBallsBowled = this.totalBallsBowled,
            maxOvers = this.maxOvers,
            tossInfo = this.tossInfo,
            target = this.target,
            strikerName = this.strikerName,
            nonStrikerName = this.nonStrikerName,
            currentBowlerName = this.currentBowlerName,
            batsmenList = this.batsmenList.map { it.copy() }.toMutableList(),
            bowlersList = this.bowlersList.map { it.copy() }.toMutableList(),
            currentOverBalls = this.currentOverBalls.toMutableList(),
            lastEvent = this.lastEvent,
            eventId = this.eventId
        )
    }

    fun toJsonString(): String {
        val root = JSONObject()
        val bTeam = JSONObject().apply {
            put("name", battingTeam)
            put("runs", totalRuns)
            put("wickets", totalWickets)
            put("overs", currentOversFormatted)
        }
        root.put("teamBatting", bTeam)

        val bwTeam = JSONObject().apply {
            put("name", bowlingTeam)
        }
        root.put("teamBowling", bwTeam)

        root.put("totalOvers", maxOvers)
        root.put("tossInfo", tossInfo)
        root.put("target", target)
        root.put("crr", crr)
        root.put("projected", projectedScore)

        // Striker
        val strikerObj = batsmenList.find { it.name == strikerName } ?: Batsman(strikerName)
        root.put("striker", JSONObject().apply {
            put("name", strikerObj.name)
            put("runs", strikerObj.runs)
            put("balls", strikerObj.balls)
            put("isStriker", true)
        })

        // Non-Striker
        val nonStrikerObj = batsmenList.find { it.name == nonStrikerName } ?: Batsman(nonStrikerName)
        root.put("nonStriker", JSONObject().apply {
            put("name", nonStrikerObj.name)
            put("runs", nonStrikerObj.runs)
            put("balls", nonStrikerObj.balls)
            put("isStriker", false)
        })

        // Bowler
        val bowlerObj = bowlersList.find { it.name == currentBowlerName } ?: Bowler(currentBowlerName)
        root.put("bowler", JSONObject().apply {
            put("name", bowlerObj.name)
            put("wickets", bowlerObj.wickets)
            put("runsConceded", bowlerObj.runsConceded)
            put("overs", bowlerObj.oversFormatted)
        })

        val ballsArr = JSONArray()
        currentOverBalls.forEach { ballsArr.put(it) }
        root.put("thisOver", ballsArr)

        root.put("lastEvent", lastEvent ?: "")
        root.put("eventId", eventId)

        return root.toString()
    }
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
                CricketScorerScreen(httpClient = httpClient)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CricketScorerScreen(httpClient: OkHttpClient) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("cricket_scorer_prefs", Context.MODE_PRIVATE) }

    var roomId by remember { mutableStateOf(prefs.getString("room_id", "cricket-live-1") ?: "cricket-live-1") }
    var webBaseUrl by remember { mutableStateOf(prefs.getString("web_url", "https://sunandanproject.vercel.app") ?: "https://sunandanproject.vercel.app") }

    // Initial default match state matching the user's screenshot
    var matchState by remember {
        mutableStateOf(
            MatchState(
                battingTeam = "SHRI BODGESHWAR",
                bowlingTeam = "SHRUTESH 11",
                totalRuns = 13,
                totalWickets = 0,
                totalBallsBowled = 6,
                maxOvers = 8,
                tossInfo = "SHRUTESH 11 DECIDED TO BOWL",
                strikerName = "GAURESH S",
                nonStrikerName = "AJAY YADAV",
                currentBowlerName = "HARSHAD M",
                batsmenList = mutableListOf(
                    Batsman("GAURESH S", runs = 0, balls = 0),
                    Batsman("AJAY YADAV", runs = 12, balls = 6, fours = 0, sixes = 2),
                    Batsman("PRATHAMESH K"),
                    Batsman("ROHIT P"),
                    Batsman("SUNIL V"),
                    Batsman("KUNAL T")
                ),
                bowlersList = mutableListOf(
                    Bowler("HARSHAD M", ballsBowled = 6, runsConceded = 13, wickets = 0),
                    Bowler("SACHIN R"),
                    Bowler("AMIT S"),
                    Bowler("VIKRAM M")
                ),
                currentOverBalls = mutableListOf("•", "6", "•", "WD", "•", "•", "6")
            )
        )
    }

    // Undo stack
    val historyStack = remember { mutableStateListOf<MatchState>() }

    var isLiveSynced by remember { mutableStateOf(true) }
    var showTeamSetupDialog by remember { mutableStateOf(false) }
    var showPlayerPickerModal by remember { mutableStateOf(false) }
    var pickerTarget by remember { mutableStateOf("striker") } // "striker", "nonStriker", "bowler"
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showWicketDialog by remember { mutableStateOf(false) }

    // Helper to push match state to cloud relay
    fun broadcastMatchState(state: MatchState) {
        val payload = state.toJsonString()
        val requestBody = payload.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://ntfy.sh/$roomId")
            .header("X-Title", "cricket-overlay-update")
            .post(requestBody)
            .build()

        isLiveSynced = false
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                coroutineScope.launch(Dispatchers.Main) { isLiveSynced = false }
            }
            override fun onResponse(call: Call, response: Response) {
                response.close()
                coroutineScope.launch(Dispatchers.Main) { isLiveSynced = true }
            }
        })
    }

    // Scoring Engine
    fun recordBall(runs: Int, isWide: Boolean = false, isNoBall: Boolean = false, isWicket: Boolean = false) {
        // Save state for undo
        historyStack.add(matchState.deepCopy())

        val state = matchState.deepCopy()
        state.eventId = System.currentTimeMillis()

        val striker = state.batsmenList.find { it.name == state.strikerName } ?: Batsman(state.strikerName).also { state.batsmenList.add(it) }
        val bowler = state.bowlersList.find { it.name == state.currentBowlerName } ?: Bowler(state.currentBowlerName).also { state.bowlersList.add(it) }

        var ballLabel = "$runs"
        var isLegalBall = true

        if (isWide) {
            state.totalRuns += (1 + runs)
            bowler.runsConceded += (1 + runs)
            ballLabel = if (runs > 0) "WD+$runs" else "WD"
            isLegalBall = false
            state.lastEvent = null
        } else if (isNoBall) {
            state.totalRuns += (1 + runs)
            bowler.runsConceded += (1 + runs)
            striker.runs += runs
            striker.balls += 1
            if (runs == 4) striker.fours++
            if (runs == 6) striker.sixes++
            ballLabel = if (runs > 0) "NB+$runs" else "NB"
            isLegalBall = false
            state.lastEvent = if (runs == 4) "4" else if (runs == 6) "6" else null
        } else if (isWicket) {
            state.totalWickets += 1
            striker.balls += 1
            striker.isOut = true
            bowler.wickets += 1
            bowler.ballsBowled += 1
            state.totalBallsBowled += 1
            ballLabel = "WKT"
            state.lastEvent = "WKT"
        } else {
            // Legal ball
            state.totalRuns += runs
            striker.runs += runs
            striker.balls += 1
            if (runs == 4) striker.fours++
            if (runs == 6) striker.sixes++
            bowler.runsConceded += runs
            bowler.ballsBowled += 1
            state.totalBallsBowled += 1
            ballLabel = if (runs == 0) "•" else "$runs"
            state.lastEvent = if (runs == 4) "4" else if (runs == 6) "6" else null
        }

        state.currentOverBalls.add(ballLabel)

        // Swap striker on odd runs (1, 3) for legal balls
        if (isLegalBall && (runs == 1 || runs == 3)) {
            val temp = state.strikerName
            state.strikerName = state.nonStrikerName
            state.nonStrikerName = temp
        }

        // Check Over completion (6 legal balls in current over)
        val legalBallsInOver = state.totalBallsBowled % 6
        if (isLegalBall && legalBallsInOver == 0) {
            // End of Over: Auto-swap striker
            val temp = state.strikerName
            state.strikerName = state.nonStrikerName
            state.nonStrikerName = temp
            
            // Clear current over list for next over
            state.currentOverBalls.clear()
            Toast.makeText(context, "Over Complete! Select next bowler.", Toast.LENGTH_SHORT).show()
        }

        matchState = state
        broadcastMatchState(state)
    }

    fun undoLastBall() {
        if (historyStack.isNotEmpty()) {
            val last = historyStack.removeAt(historyStack.size - 1)
            matchState = last
            broadcastMatchState(last)
            Toast.makeText(context, "Undone!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Nothing to undo", Toast.LENGTH_SHORT).show()
        }
    }

    fun swapStrikersManual() {
        historyStack.add(matchState.deepCopy())
        val state = matchState.deepCopy()
        val temp = state.strikerName
        state.strikerName = state.nonStrikerName
        state.nonStrikerName = temp
        matchState = state
        broadcastMatchState(state)
    }

    val fullObsUrl = remember(webBaseUrl, roomId) {
        "${webBaseUrl.trimEnd('/')}/?room=$roomId"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "🏏 Cricket Live Scorer",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(if (isLiveSynced) Color(0xFF4CAF50) else Color(0xFFFF9800))
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isLiveSynced) "OBS LIVE" else "SYNCING",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLiveSynced) Color(0xFF4CAF50) else Color(0xFFFF9800)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showTeamSetupDialog = true }) {
                        Icon(imageVector = Icons.Default.GroupAdd, contentDescription = "Team Registration")
                    }
                    IconButton(onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, fullObsUrl)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share OBS Overlay URL"))
                    }) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = "Share OBS URL")
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            
            // 1. BROADCAST MINI PREVIEW CARD (Matches what OBS is showing)
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = matchState.battingTeam.uppercase(),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = Color(0xFF1E3A8A)
                        )
                        Text(
                            text = "${matchState.totalRuns} - ${matchState.totalWickets}",
                            fontWeight = FontWeight.Black,
                            fontSize = 26.sp,
                            color = Color(0xFF1E3A8A)
                        )
                        Text(
                            text = "${matchState.currentOversFormatted} (${matchState.maxOvers})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF374151)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "CRR: ${matchState.crr} • Proj: ${matchState.projectedScore}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF4B5563)
                        )
                        Text(
                            text = "vs ${matchState.bowlingTeam}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E)
                        )
                    }
                }
            }

            // 2. ACTIVE BATSMEN CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "BATSMEN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        TextButton(
                            onClick = { swapStrikersManual() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.SwapHoriz, contentDescription = "Swap", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Swap Strike")
                        }
                    }

                    val striker = matchState.batsmenList.find { it.name == matchState.strikerName } ?: Batsman(matchState.strikerName)
                    val nonStriker = matchState.batsmenList.find { it.name == matchState.nonStrikerName } ?: Batsman(matchState.nonStrikerName)

                    // Striker Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                pickerTarget = "striker"
                                showPlayerPickerModal = true
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("▶", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(striker.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Text("${striker.runs} (${striker.balls}b) [4s:${striker.fours} 6s:${striker.sixes}]", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    // Non-Striker Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                pickerTarget = "nonStriker"
                                showPlayerPickerModal = true
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(nonStriker.name, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = Color.Gray)
                        }
                        Text("${nonStriker.runs} (${nonStriker.balls}b)", fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Color.Gray)
                    }
                }
            }

            // 3. ACTIVE BOWLER & CURRENT OVER BALLS
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    val bowler = matchState.bowlersList.find { it.name == matchState.currentBowlerName } ?: Bowler(matchState.currentBowlerName)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                pickerTarget = "bowler"
                                showPlayerPickerModal = true
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("BOWLER (Tap to change)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Text(bowler.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Text(
                            text = "${bowler.wickets}-${bowler.runsConceded} (${bowler.oversFormatted} ov)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Ball-by-ball pill history
                    Text("THIS OVER:", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(matchState.currentOverBalls) { ball ->
                            val (bgColor, textColor) = when {
                                ball == "6" -> Color(0xFF2563EB) to Color.White
                                ball == "4" -> Color(0xFF16A34A) to Color.White
                                ball == "WKT" -> Color(0xFFDC2626) to Color.White
                                ball.contains("WD") || ball.contains("NB") -> Color(0xFFD97706) to Color.White
                                else -> Color(0xFFF3F4F6) to Color.Black
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(bgColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = ball, fontWeight = FontWeight.Bold, fontSize = 11.sp, color = textColor)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 4. BIG QUICK SCORING KEYPAD
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Runs 0, 1, 2, 3
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { recordBall(0) }, modifier = Modifier.weight(1f).height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569))) {
                        Text("0", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = { recordBall(1) }, modifier = Modifier.weight(1f).height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))) {
                        Text("1", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = { recordBall(2) }, modifier = Modifier.weight(1f).height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))) {
                        Text("2", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = { recordBall(3) }, modifier = Modifier.weight(1f).height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))) {
                        Text("3", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Boundaries 4, 6 and Extras
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { recordBall(4) }, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))) {
                        Text("FOUR 4", fontSize = 15.sp, fontWeight = FontWeight.Black)
                    }
                    Button(onClick = { recordBall(6) }, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))) {
                        Text("SIX 6", fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
                    Button(onClick = { recordBall(0, isWide = true) }, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))) {
                        Text("WIDE", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = { recordBall(0, isNoBall = true) }, modifier = Modifier.weight(1f).height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309))) {
                        Text("NO BALL", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Bottom Action Row: Wicket and Undo
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { recordBall(0, isWicket = true) },
                        modifier = Modifier.weight(2f).height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Wicket")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("OUT / WICKET", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }

                    OutlinedButton(
                        onClick = { undoLastBall() },
                        modifier = Modifier.weight(1f).height(50.dp)
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("UNDO")
                    }
                }
            }
        }
    }

    // 5. PLAYER PICKER MODAL (Striker / Non-Striker / Bowler)
    if (showPlayerPickerModal) {
        val isBowling = pickerTarget == "bowler"
        val teamList = if (isBowling) matchState.bowlersList.map { it.name } else matchState.batsmenList.filter { !it.isOut }.map { it.name }
        var newPlayerName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPlayerPickerModal = false },
            title = {
                Text(
                    text = when (pickerTarget) {
                        "striker" -> "Select Striker"
                        "nonStriker" -> "Select Non-Striker"
                        else -> "Select Active Bowler"
                    }
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(teamList) { player ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        historyStack.add(matchState.deepCopy())
                                        val state = matchState.deepCopy()
                                        if (pickerTarget == "striker") state.strikerName = player
                                        else if (pickerTarget == "nonStriker") state.nonStrikerName = player
                                        else state.currentBowlerName = player

                                        matchState = state
                                        broadcastMatchState(state)
                                        showPlayerPickerModal = false
                                    }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(player, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                                Icon(Icons.Default.ChevronRight, contentDescription = "Select")
                            }
                            HorizontalDivider()
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = newPlayerName,
                        onValueChange = { newPlayerName = it },
                        label = { Text("Or add new player") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (newPlayerName.isNotBlank()) {
                                val name = newPlayerName.trim().uppercase()
                                historyStack.add(matchState.deepCopy())
                                val state = matchState.deepCopy()
                                if (isBowling) {
                                    state.bowlersList.add(Bowler(name))
                                    state.currentBowlerName = name
                                } else {
                                    state.batsmenList.add(Batsman(name))
                                    if (pickerTarget == "striker") state.strikerName = name else state.nonStrikerName = name
                                }
                                matchState = state
                                broadcastMatchState(state)
                                showPlayerPickerModal = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    ) {
                        Text("Add & Select")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlayerPickerModal = false }) { Text("Close") }
            }
        )
    }

    // 6. TOURNAMENT & TEAM SETUP DIALOG
    if (showTeamSetupDialog) {
        var teamA by remember { mutableStateOf(matchState.battingTeam) }
        var teamB by remember { mutableStateOf(matchState.bowlingTeam) }
        var oversCount by remember { mutableStateOf("${matchState.maxOvers}") }
        var tossDetail by remember { mutableStateOf(matchState.tossInfo) }

        AlertDialog(
            onDismissRequest = { showTeamSetupDialog = false },
            title = { Text("Match Setup & Teams") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = teamA,
                        onValueChange = { teamA = it },
                        label = { Text("Batting Team Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = teamB,
                        onValueChange = { teamB = it },
                        label = { Text("Bowling Team Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = oversCount,
                        onValueChange = { oversCount = it },
                        label = { Text("Total Overs (e.g. 8, 10, 20)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tossDetail,
                        onValueChange = { tossDetail = it },
                        label = { Text("Toss Info (e.g. Shrutesh 11 decided to bowl)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val state = matchState.deepCopy()
                        state.battingTeam = teamA.trim().ifBlank { "BATTING TEAM" }
                        state.bowlingTeam = teamB.trim().ifBlank { "BOWLING TEAM" }
                        state.maxOvers = oversCount.toIntOrNull() ?: 8
                        state.tossInfo = tossDetail.trim()
                        matchState = state
                        broadcastMatchState(state)
                        showTeamSetupDialog = false
                        Toast.makeText(context, "Match Setup Updated!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Apply to OBS")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTeamSetupDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 7. ROOM & VERCEL URL SETTINGS
    if (showSettingsDialog) {
        var tempRoom by remember { mutableStateOf(roomId) }
        var tempUrl by remember { mutableStateOf(webBaseUrl) }

        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = { Text("OBS Stream Settings") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Set your room channel. The same room must be in the OBS Browser Source link.", fontSize = 12.sp, color = Color.Gray)
                    OutlinedTextField(
                        value = tempRoom,
                        onValueChange = { tempRoom = it.trim().replace(" ", "-") },
                        label = { Text("Room Channel ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it.trim() },
                        label = { Text("Vercel Web App URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        roomId = tempRoom.ifBlank { "cricket-live-1" }
                        webBaseUrl = tempUrl.ifBlank { "https://sunandanproject.vercel.app" }
                        prefs.edit().putString("room_id", roomId).putString("web_url", webBaseUrl).apply()
                        showSettingsDialog = false
                        broadcastMatchState(matchState)
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSettingsDialog = false }) { Text("Cancel") }
            }
        )
    }
}
