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

// 1. DATA MODELS WITH FULL JSON SERIALIZATION & PERSISTENCE
data class Batsman(
    val name: String,
    var runs: Int = 0,
    var balls: Int = 0,
    var fours: Int = 0,
    var sixes: Int = 0,
    var isOut: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("runs", runs)
        put("balls", balls)
        put("fours", fours)
        put("sixes", sixes)
        put("isOut", isOut)
    }

    companion object {
        fun fromJson(obj: JSONObject): Batsman = Batsman(
            name = obj.optString("name", "Batsman"),
            runs = obj.optInt("runs", 0),
            balls = obj.optInt("balls", 0),
            fours = obj.optInt("fours", 0),
            sixes = obj.optInt("sixes", 0),
            isOut = obj.optBoolean("isOut", false)
        )
    }
}

data class Bowler(
    val name: String,
    var ballsBowled: Int = 0,
    var runsConceded: Int = 0,
    var wickets: Int = 0
) {
    val oversFormatted: String
        get() = "${ballsBowled / 6}.${ballsBowled % 6}"

    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("ballsBowled", ballsBowled)
        put("runsConceded", runsConceded)
        put("wickets", wickets)
    }

    companion object {
        fun fromJson(obj: JSONObject): Bowler = Bowler(
            name = obj.optString("name", "Bowler"),
            ballsBowled = obj.optInt("ballsBowled", 0),
            runsConceded = obj.optInt("runsConceded", 0),
            wickets = obj.optInt("wickets", 0)
        )
    }
}

data class MatchState(
    var battingTeam: String = "SHRI BODGESHWAR",
    var bowlingTeam: String = "SHRUTESH 11",
    var totalRuns: Int = 0,
    var totalWickets: Int = 0,
    var totalBallsBowled: Int = 0,
    var maxOvers: Int = 8,
    var tossInfo: String = "SHRUTESH 11 DECIDED TO BOWL",
    var target: Int = 0,
    
    var strikerName: String = "GAURESH S",
    var nonStrikerName: String = "AJAY YADAV",
    var currentBowlerName: String = "HARSHAD M",
    
    var batsmenList: MutableList<Batsman> = mutableListOf(),
    var bowlersList: MutableList<Bowler> = mutableListOf(),
    var currentOverBalls: MutableList<String> = mutableListOf(),
    var lastEvent: String? = null,
    var eventId: Long = 0,
    var isOverComplete: Boolean = false
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
            eventId = this.eventId,
            isOverComplete = this.isOverComplete
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

        // State persistence metadata
        root.put("totalBallsBowled", totalBallsBowled)
        root.put("isOverComplete", isOverComplete)

        val bListJson = JSONArray()
        batsmenList.forEach { bListJson.put(it.toJson()) }
        root.put("saved_batsmen_list", bListJson)

        val bwListJson = JSONArray()
        bowlersList.forEach { bwListJson.put(it.toJson()) }
        root.put("saved_bowlers_list", bwListJson)

        return root.toString()
    }

    companion object {
        fun createDefaultMatch(): MatchState {
            return MatchState(
                battingTeam = "SHRI BODGESHWAR",
                bowlingTeam = "SHRUTESH 11",
                totalRuns = 0,
                totalWickets = 0,
                totalBallsBowled = 0,
                maxOvers = 8,
                tossInfo = "SHRUTESH 11 DECIDED TO BOWL",
                strikerName = "GAURESH S",
                nonStrikerName = "AJAY YADAV",
                currentBowlerName = "HARSHAD M",
                batsmenList = mutableListOf(
                    Batsman("GAURESH S"),
                    Batsman("AJAY YADAV"),
                    Batsman("PRATHAMESH K"),
                    Batsman("ROHIT P"),
                    Batsman("SUNIL V"),
                    Batsman("KUNAL T")
                ),
                bowlersList = mutableListOf(
                    Bowler("HARSHAD M"),
                    Bowler("SACHIN R"),
                    Bowler("AMIT S"),
                    Bowler("VIKRAM M")
                ),
                currentOverBalls = mutableListOf(),
                isOverComplete = false
            )
        }

        fun fromJson(jsonStr: String): MatchState {
            val root = JSONObject(jsonStr)
            val bTeam = root.optJSONObject("teamBatting")
            val bwTeam = root.optJSONObject("teamBowling")

            val state = MatchState(
                battingTeam = bTeam?.optString("name", "SHRI BODGESHWAR") ?: "SHRI BODGESHWAR",
                bowlingTeam = bwTeam?.optString("name", "SHRUTESH 11") ?: "SHRUTESH 11",
                totalRuns = bTeam?.optInt("runs", 0) ?: 0,
                totalWickets = bTeam?.optInt("wickets", 0) ?: 0,
                totalBallsBowled = root.optInt("totalBallsBowled", 0),
                maxOvers = root.optInt("totalOvers", 8),
                tossInfo = root.optString("tossInfo", ""),
                target = root.optInt("target", 0),
                lastEvent = root.optString("lastEvent", null),
                eventId = root.optLong("eventId", 0),
                isOverComplete = root.optBoolean("isOverComplete", false)
            )

            val strikerObj = root.optJSONObject("striker")
            if (strikerObj != null) state.strikerName = strikerObj.optString("name", "GAURESH S")

            val nonStrikerObj = root.optJSONObject("nonStriker")
            if (nonStrikerObj != null) state.nonStrikerName = nonStrikerObj.optString("name", "AJAY YADAV")

            val bowlerObj = root.optJSONObject("bowler")
            if (bowlerObj != null) state.currentBowlerName = bowlerObj.optString("name", "HARSHAD M")

            // Saved ball list
            val ballsArr = root.optJSONArray("thisOver")
            if (ballsArr != null) {
                for (i in 0 until ballsArr.length()) {
                    state.currentOverBalls.add(ballsArr.optString(i))
                }
            }

            // Saved batsmen list
            val bArr = root.optJSONArray("saved_batsmen_list")
            if (bArr != null && bArr.length() > 0) {
                for (i in 0 until bArr.length()) {
                    val bObj = bArr.optJSONObject(i)
                    if (bObj != null) state.batsmenList.add(Batsman.fromJson(bObj))
                }
            } else {
                state.batsmenList = createDefaultMatch().batsmenList
            }

            // Saved bowlers list
            val bwArr = root.optJSONArray("saved_bowlers_list")
            if (bwArr != null && bwArr.length() > 0) {
                for (i in 0 until bwArr.length()) {
                    val bwObj = bwArr.optJSONObject(i)
                    if (bwObj != null) state.bowlersList.add(Bowler.fromJson(bwObj))
                }
            } else {
                state.bowlersList = createDefaultMatch().bowlersList
            }

            return state
        }
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

    // RESTORE SAVED MATCH STATE FROM PREFERENCES (PERSISTENCE)
    var matchState by remember {
        mutableStateOf(
            run {
                val savedJson = prefs.getString("saved_match_state", null)
                if (!savedJson.isNullOrBlank()) {
                    try {
                        MatchState.fromJson(savedJson)
                    } catch (e: Exception) {
                        MatchState.createDefaultMatch()
                    }
                } else {
                    MatchState.createDefaultMatch()
                }
            }
        )
    }

    val historyStack = remember { mutableStateListOf<MatchState>() }
    var isLiveSynced by remember { mutableStateOf(true) }

    var showTeamSetupDialog by remember { mutableStateOf(false) }
    var showPlayerPickerModal by remember { mutableStateOf(false) }
    var pickerTarget by remember { mutableStateOf("striker") }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showOverCompleteDialog by remember { mutableStateOf(false) }

    // Helper to broadcast match state to cloud relay and persist to SharedPreferences
    fun broadcastAndSaveState(state: MatchState) {
        matchState = state
        prefs.edit().putString("saved_match_state", state.toJsonString()).apply()

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

    // On initial composition, sync current match state to OBS automatically
    LaunchedEffect(Unit) {
        broadcastAndSaveState(matchState)
    }

    // SCORING ENGINE WITH PROPER OVER-RESET LOGIC
    fun recordBall(runs: Int, isWide: Boolean = false, isNoBall: Boolean = false, isWicket: Boolean = false) {
        // Prevent exceeding max overs
        if (matchState.totalBallsBowled >= matchState.maxOvers * 6 && !isWide && !isNoBall) {
            Toast.makeText(context, "Innings Complete! Max overs reached (${matchState.maxOvers} ov).", Toast.LENGTH_SHORT).show()
            return
        }

        historyStack.add(matchState.deepCopy())
        val state = matchState.deepCopy()
        state.eventId = System.currentTimeMillis()

        // IF THE PREVIOUS BALL FINISHED AN OVER, CLEAR THE OVER BUBBLES NOW FOR THE NEW OVER!
        if (state.isOverComplete) {
            state.currentOverBalls.clear()
            state.isOverComplete = false
        }

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

        // CHECK OVER COMPLETION: 6 legal balls bowled in current over
        val legalBallsInCurrentOver = state.totalBallsBowled % 6
        if (isLegalBall && legalBallsInCurrentOver == 0 && state.totalBallsBowled > 0) {
            // Over finished: Auto-swap striker and non-striker
            val temp = state.strikerName
            state.strikerName = state.nonStrikerName
            state.nonStrikerName = temp

            // Mark over complete so the balls stay visible in OBS until the next over begins!
            state.isOverComplete = true

            // Prompt to select the next bowler
            showOverCompleteDialog = true
        }

        broadcastAndSaveState(state)
    }

    fun undoLastBall() {
        if (historyStack.isNotEmpty()) {
            val last = historyStack.removeAt(historyStack.size - 1)
            broadcastAndSaveState(last)
            Toast.makeText(context, "Last ball undone!", Toast.LENGTH_SHORT).show()
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
        broadcastAndSaveState(state)
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
                            text = "🏏 Cricket Scorer",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isLiveSynced) Color(0xFF4CAF50) else Color(0xFFFF9800))
                        )
                    }
                },
                actions = {
                    // New Match / Reset Match Button
                    IconButton(onClick = { showResetConfirmDialog = true }) {
                        Icon(imageVector = Icons.Default.RestartAlt, contentDescription = "New Match", tint = Color(0xFFE53935))
                    }
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

                    // Ball-by-ball pill history for the current over
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (matchState.isOverComplete) "OVER COMPLETE:" else "THIS OVER:",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (matchState.isOverComplete) Color(0xFFE53935) else Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                        if (matchState.currentOverBalls.isNotEmpty()) {
                            Text(
                                text = "${matchState.currentOverBalls.size} balls",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    if (matchState.currentOverBalls.isEmpty()) {
                        Text(
                            text = "Waiting for first ball of over...",
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    } else {
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
            }

            Spacer(modifier = Modifier.weight(1f))

            // 4. BIG TOUCH SCORING KEYPAD
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

                // Bottom Row: Wicket and Undo
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

    // 5. OVER COMPLETE DIALOG (Prompts to select next bowler)
    if (showOverCompleteDialog) {
        val bowlers = matchState.bowlersList.map { it.name }
        AlertDialog(
            onDismissRequest = { showOverCompleteDialog = false },
            title = {
                Text("Over Complete! 🎯", fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text(
                        text = "Over ${matchState.currentOversFormatted} finished. Select the next bowler to bowl:",
                        fontSize = 14.sp,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(bowlers) { bName ->
                            val isCurrent = bName == matchState.currentBowlerName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        historyStack.add(matchState.deepCopy())
                                        val state = matchState.deepCopy()
                                        state.currentBowlerName = bName
                                        broadcastAndSaveState(state)
                                        showOverCompleteDialog = false
                                        Toast.makeText(context, "$bName is now bowling", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(vertical = 10.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = bName,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrent) Color(0xFFD97706) else Color.Black
                                )
                                if (isCurrent) {
                                    Text("(Just Bowled)", fontSize = 11.sp, color = Color.Gray)
                                } else {
                                    Icon(Icons.Default.Check, contentDescription = "Select", tint = Color(0xFF16A34A))
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showOverCompleteDialog = false }) {
                    Text("Done")
                }
            }
        )
    }

    // 6. RESET MATCH CONFIRMATION DIALOG
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Start New Match?") },
            text = { Text("This will reset the score to 0-0 and clear all batsman and bowler stats. Are you sure?") },
            confirmButton = {
                Button(
                    onClick = {
                        val freshState = MatchState.createDefaultMatch()
                        freshState.battingTeam = matchState.battingTeam
                        freshState.bowlingTeam = matchState.bowlingTeam
                        freshState.maxOvers = matchState.maxOvers
                        historyStack.clear()
                        broadcastAndSaveState(freshState)
                        showResetConfirmDialog = false
                        Toast.makeText(context, "New Match Started (0-0)!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Reset Match")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // 7. PLAYER PICKER MODAL (Striker / Non-Striker / Bowler)
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

                                        broadcastAndSaveState(state)
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
                                broadcastAndSaveState(state)
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

    // 8. TOURNAMENT & TEAM SETUP DIALOG
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
                        broadcastAndSaveState(state)
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

    // 9. ROOM & VERCEL URL SETTINGS
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
                        broadcastAndSaveState(matchState)
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
