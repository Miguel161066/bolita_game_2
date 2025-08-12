package com.example.bolita

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

@Serializable
data class UserScore(val username: String, val level: Int, val screenPercent: Int, val bestTimeMs: Long)

@Serializable
data class Replay(val username: String, val level: Int, val positions: List<Pair<Long, Pair<Float, Float>>>, val lines: List<Pair<Pair<Float,Float>, Pair<Float,Float>>>)


class Repo(val ctx: Context) {
    private val prefs: SharedPreferences = ctx.getSharedPreferences("apbolita", Context.MODE_PRIVATE)
    private val json = Json { encodeDefaults = true }

    fun saveSettings(key: String, value: String){ prefs.edit().putString(key, value).apply() }
    fun getSettings(key: String, default: String) = prefs.getString(key, default) ?: default

    private fun getUsersJson() = prefs.getString("users_json","[]")!!
    private fun setUsersJson(s:String) = prefs.edit().putString("users_json", s).apply()
    private fun getReplaysJson() = prefs.getString("replays_json","[]")!!
    private fun setReplaysJson(s:String) = prefs.edit().putString("replays_json", s).apply()

    fun loadUsers(): MutableList<UserScore> {
        return try{ json.decodeFromString(getUsersJson()) }catch(e:Exception){ mutableListOf() }
    }
    fun saveUsers(list: List<UserScore>){ setUsersJson(json.encodeToString(list)) }

    fun loadReplays(): MutableList<Replay>{
        return try{ json.decodeFromString(getReplaysJson()) }catch(e:Exception){ mutableListOf() }
    }
    fun saveReplays(list: List<Replay>){ setReplaysJson(json.encodeToString(list)) }

    fun top5ForLevel(level:Int): List<UserScore>{
        return loadUsers().filter{ it.level==level }.sortedBy{ it.bestTimeMs }.take(5)
    }

    fun updateUserScore(user: UserScore){
        val users = loadUsers().filterNot{ it.username==user.username && it.level==user.level }.toMutableList()
        users.add(user)
        val grouped = users.groupBy{ it.level }.flatMap { (_, list)->
            list.sortedBy{ it.bestTimeMs }.take(5)
        }
        saveUsers(grouped)
    }

    fun deleteUser(username:String){
        val users = loadUsers().filterNot{ it.username==username }
        saveUsers(users)
        val replays = loadReplays().filterNot{ it.username==username }
        saveReplays(replays)
    }
}

data class Ball(var x:Float, var y:Float, var vx:Float, var vy:Float, val r:Float)

fun reflectVector(vx:Float, vy:Float, nx:Float, ny:Float): Pair<Float,Float>{
    val dot = vx*nx + vy*ny
    val rx = vx - 2*dot*nx
    val ry = vy - 2*dot*ny
    return Pair(rx, ry)
}

fun pointSegmentDistance(px:Float, py:Float, x1:Float, y1:Float, x2:Float, y2:Float): Float{
    val dx = x2-x1; val dy = y2-y1
    val l2 = dx*dx+dy*dy
    val t = if (l2==0f) 0f else max(0f, min(1f, ((px-x1)*dx + (py-y1)*dy)/l2))
    val projx = x1 + t*dx
    val projy = y1 + t*dy
    return kotlin.math.hypot(px-projx, py-projy)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = Repo(this)
        if (repo.getSettings("percent","") == "") repo.saveSettings("percent","80")
        if (repo.getSettings("time_limit_ms","") == "") repo.saveSettings("time_limit_ms","30000")
        if (repo.getSettings("base_speed","") == "") repo.saveSettings("base_speed","300")
        if (repo.getSettings("shape","") == "") repo.saveSettings("shape","fullscreen")
        setContent {
            MaterialTheme {
                AppRoot(repo)
            }
        }
    }
}

@Composable
fun AppRoot(repo: Repo){
    var username by remember { mutableStateOf<String?>(null) }
    var nav by remember { mutableStateOf("demo") }

    when(nav){
        "demo" -> DemoScreen(onSkip={nav="login"}, onEnd={nav="login"})
        "login" -> LoginScreen(onEnter={ name-> username=name; nav="home" }, repo=repo)
        "home" -> HomeScreen(username!!, repo, onStartGame={ nav="game" }, onViewTop={ nav="top" })
        "game" -> GameContainer(username!!, repo, onExit = { nav = "home" })
        "top" -> TopListScreen(repo, onBack = { nav = "home" })
        else -> Text("Unknown")
    }
}

@Composable
fun DemoScreen(onSkip:()->Unit, onEnd:()->Unit){
    val scope = rememberCoroutineScope()
    var remaining by remember{ mutableStateOf(3) }
    LaunchedEffect(Unit){
        while(remaining>0){ delay(1000); remaining-- }
        onEnd()
    }
    Box(modifier=Modifier.fillMaxSize().background(Color(0xFF101214)), contentAlignment = Alignment.Center){
        Column(horizontalAlignment = Alignment.CenterHorizontally){
            Text("Demo", color=Color(0xFF00D4FF), fontSize=30.sp)
            Text("$remaining s", color=Color.White, fontSize=20.sp)
            Spacer(Modifier.height(16.dp))
            Button(onClick=onSkip){ Text("Saltar demo") }
        }
    }
}

@Composable
fun LoginScreen(onEnter:(String)->Unit, repo: Repo){
    var name by remember{ mutableStateOf("") }
    Column(modifier=Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally){
        Text("Introduce tu nombre", fontSize=20.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value=name, onValueChange={ name=it }, singleLine=true)
        Spacer(Modifier.height(8.dp))
        Button(onClick={ if(name.isNotBlank()){ onEnter(name) } }){
            Text("Entrar")
        }
    }
}

@Composable
fun HomeScreen(username: String, repo: Repo, onStartGame:()->Unit, onViewTop:()->Unit){
    val isAdmin = username.equals("ADMIN", true)
    val prefs = repo.ctx.getSharedPreferences("apbolita", Context.MODE_PRIVATE)
    var percent by remember { mutableStateOf(repo.getSettings("percent","80")) }
    var baseSpeed by remember { mutableStateOf(repo.getSettings("base_speed","300")) }
    var timeLimit by remember { mutableStateOf(repo.getSettings("time_limit_ms","30000")) }
    var shape by remember { mutableStateOf(repo.getSettings("shape","fullscreen")) }
    var level by remember { mutableStateOf(1) }

    Column(modifier=Modifier.fillMaxSize().padding(16.dp)){
        Text("Usuario: $username", fontWeight = FontWeight.Bold, color=Color.White)
        Spacer(Modifier.height(12.dp))
        Text("Nivel (velocidad):", color=Color.White)
        Row{ for(i in 1..5){
            Button(onClick={ level=i; prefs.edit().putInt("level_selected", i).apply() }, modifier=Modifier.padding(4.dp)){
                Text("$i")
            }
        }}
        Spacer(Modifier.height(12.dp))
        Button(onClick=onStartGame){ Text("Empezar partida") }
        Spacer(Modifier.height(12.dp))
        Button(onClick=onViewTop){ Text("Ver Top 5 por nivel") }
        Spacer(Modifier.height(12.dp))
        if(isAdmin){
            Divider(color=Color.Gray); Text("Opciones ADMIN", fontWeight = FontWeight.Bold, color=Color.White)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically){
                Text("% pantalla para cerrar:", color=Color.White )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(value=percent, onValueChange={ percent=it }, singleLine=true, modifier=Modifier.width(120.dp))
                Spacer(Modifier.width(8.dp))
                Button(onClick={ repo.saveSettings("percent",percent) }){ Text("Guardar") }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically){
                Text("Velocidad base (px/s):", color=Color.White)
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(value=baseSpeed, onValueChange={ baseSpeed=it }, singleLine=true, modifier=Modifier.width(120.dp))
                Spacer(Modifier.width(8.dp))
                Button(onClick={ repo.saveSettings("base_speed",baseSpeed) }){ Text("Guardar") }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically){
                Text("Tiempo límite (ms):", color=Color.White)
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(value=timeLimit, onValueChange={ timeLimit=it }, singleLine=true, modifier=Modifier.width(140.dp))
                Spacer(Modifier.width(8.dp))
                Button(onClick={ repo.saveSettings("time_limit_ms",timeLimit) }){ Text("Guardar") }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically){
                Text("Forma recinto:", color=Color.White)
                Spacer(Modifier.width(8.dp))
                Button(onClick={ shape="fullscreen"; repo.saveSettings("shape","fullscreen") }){ Text("Pantalla") }
                Spacer(Modifier.width(8.dp))
                Button(onClick={ shape="circle"; repo.saveSettings("shape","circle") }){ Text("Circular") }
                Spacer(Modifier.width(8.dp))
                Button(onClick={ shape="square"; repo.saveSettings("shape","square") }){ Text("Cuadrado") }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick={ repo.saveUsers(listOf()); repo.saveReplays(listOf()) }){ Text("Borrar lista de usuarios (resetea top)") }
        }
    }
}

@Composable
fun TopListScreen(repo: Repo, onBack:()->Unit){
    var levelStr by remember{ mutableStateOf("1") }
    val level = levelStr.toIntOrNull()?:1
    val list = remember(level){ repo.top5ForLevel(level) }
    Column(modifier=Modifier.fillMaxSize().padding(16.dp)){
        Row(verticalAlignment = Alignment.CenterVertically){
            Text("Nivel:")
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(value=levelStr, onValueChange={ levelStr=it })
            Spacer(Modifier.width(8.dp))
            Button(onClick={}){ Text("Cargar") }
        }
        Spacer(Modifier.height(12.dp))
        Text("Top 5 nivel $level")
        Spacer(Modifier.height(8.dp))
        for((i,u) in list.withIndex()){
            Row(modifier=Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween){
                Text("${i+1}. ${u.username} - ${(u.bestTimeMs/1000.0).format(2)}s", color=Color.White)
                if(i<2){ Button(onClick={ /* TODO: open replay viewer */ }){ Text("Ver partida") } }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick=onBack){ Text("Volver") }
    }
}

fun Double.format(dec:Int)="%.\${dec}f".format(this)

@Composable
fun GameContainer(username: String, repo: Repo, onExit:()->Unit){
    val ctx = LocalContext.current
    val prefs = ctx.getSharedPreferences("apbolita", Context.MODE_PRIVATE)
    val percent = repo.getSettings("percent","80").toInt()
    val baseSpeed = repo.getSettings("base_speed","300").toFloat()
    val timeLimitMs = repo.getSettings("time_limit_ms","30000").toLong()
    val savedLevel = prefs.getInt("level_selected", 1)
    val level = savedLevel
    val speedMultiplier = 1f + (level-1)*0.1f

    var finished by remember{ mutableStateOf<Pair<Boolean, Long>?>(null) } // success, time
    val positions = remember{ mutableStateListOf<Pair<Long, Pair<Float,Float>>>() }
    val lines = remember{ mutableStateListOf<Pair<Pair<Float,Float>, Pair<Float,Float>>>() }

    Column(Modifier.fillMaxSize()){
        Box(modifier=Modifier.weight(1f).fillMaxWidth()){
            GameCanvas(
                speed = baseSpeed * speedMultiplier,
                percentToClose = percent,
                timeLimitMs = timeLimitMs,
                onFinish = { success, timeMs ->
                    finished = Pair(success, timeMs)
                },
                username = username,
                recordPositions = positions,
                recordLines = lines,
                repo = repo,
                level = level
            )
        }
        Row(modifier=Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween){
            Button(onClick={ /* new? */ }){ Text("Nueva partida") }
            Button(onClick={ onExit() }){ Text("Terminar") }
        }
        if(finished!=null){
            val (ok, timeMs) = finished!!
            Column(modifier=Modifier.fillMaxWidth().padding(8.dp)){
                Text(if(ok) "Has atrapado la bolita en ${(timeMs/1000.0).format(2)} s" else "No atrapaste la bolita (tiempo) - ${(timeMs/1000.0).format(2)} s")
                Spacer(Modifier.height(8.dp))
                val top = repo.top5ForLevel(level)
                for((i,u) in top.withIndex()){
                    Row(modifier=Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween){
                        Text("${i+1}. ${u.username} - ${(u.bestTimeMs/1000.0).format(2)}s")
                        if(i<2){ Button(onClick={ /* view replay */ }){ Text("Ver partida") } }
                    }
                }
            }
        }
    }
}

@Composable
fun GameCanvas(
    speed: Float,
    percentToClose: Int,
    timeLimitMs: Long,
    onFinish: (Boolean, Long)->Unit,
    username: String,
    recordPositions: MutableList<Pair<Long, Pair<Float,Float>>>,
    recordLines: MutableList<Pair<Pair<Float,Float>, Pair<Float,Float>>>,
    repo: Repo,
    level: Int
){
    val scope = rememberCoroutineScope()
    var ball by remember{ mutableStateOf<Ball?>(null) }
    var lastTime by remember{ mutableStateOf(SystemClock.elapsedRealtime()) }
    var startedAt by remember{ mutableStateOf(SystemClock.elapsedRealtime()) }
    var running by remember{ mutableStateOf(true) }
    var linesState by remember{ mutableStateOf(listOf<Pair<Pair<Float,Float>, Pair<Float,Float>>>()) }

    var canvasSize by remember{ mutableStateOf(Size(1f,1f)) }

    val vibrator = LocalContext.current.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?

    LaunchedEffect(running, canvasSize){
        if(!running) return@LaunchedEffect
        if(canvasSize.width<=1f) return@LaunchedEffect
        if(ball==null){
            ball = Ball(canvasSize.width/2f, canvasSize.height/2f, (speed/60f)*(if((0..1).random()==1)1f else -1f), (speed/60f)*(if((0..1).random()==1)1f else -1f), 14f)
            startedAt = SystemClock.elapsedRealtime()
            lastTime = startedAt
        }
        while(running){
            val now = SystemClock.elapsedRealtime()
            val dt = (now-lastTime)/1000f
            lastTime = now
            val b = ball!!
            b.x += b.vx*dt
            b.y += b.vy*dt
            if(b.x - b.r < 0){ b.x = b.r; b.vx = -b.vx }
            if(b.x + b.r > canvasSize.width){ b.x = canvasSize.width - b.r; b.vx = -b.vx }
            if(b.y - b.r < 0){ b.y = b.r; b.vy = -b.vy }
            if(b.y + b.r > canvasSize.height){ b.y = canvasSize.height - b.r; b.vy = -b.vy }
            for(seg in linesState){
                val (a,bp) = seg
                val dist = pointSegmentDistance(b.x, b.y, a.first, a.second, bp.first, bp.second)
                if(dist <= b.r + 1f){
                    val sx = bp.first - a.first
                    val sy = bp.second - a.second
                    val nx = -sy
                    val ny = sx
                    val nl = sqrt(nx*nx + ny*ny)
                    if(nl>0){
                        val nnx = nx/nl
                        val nny = ny/nl
                        val (rvx,rvy) = reflectVector(b.vx, b.vy, nnx, nny)
                        b.vx = rvx
                        b.vy = rvy
                    }
                }
            }
            if((now - (recordPositions.lastOrNull()?.first ?: 0L)) >= 50){
                recordPositions.add(Pair(now-startedAt, Pair(b.x, b.y)))
            }
            val elapsed = now - startedAt
            val pts = linesState.flatMap{ listOf( it.first, it.second ) }.map{ Offset(it.first, it.second) }
            val success = if(pts.size>=3){
                val hull = convexHull(pts)
                val area = polygonArea(hull)
                val screenArea = canvasSize.width * canvasSize.height
                (area/screenArea*100f) <= percentToClose
            } else false
            if(success){
                onFinish(true, elapsed)
                scope.launch {
                    val users = repo.loadUsers()
                    val existing = users.firstOrNull{ it.username==username && it.level==level }
                    val curBest = existing?.bestTimeMs ?: Long.MAX_VALUE
                    val realTime = elapsed
                    if(realTime < curBest){
                        repo.updateUserScore(UserScore(username, level, percentToClose, realTime))
                    }
                    val replay = Replay(username, level, recordPositions.toList(), recordLines.toList())
                    val replays = repo.loadReplays().toMutableList(); replays.add(replay)
                    repo.saveReplays(replays)
                }
                vibrator?.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
                running = false
                break
            }
            if(elapsed >= timeLimitMs){
                onFinish(false, elapsed)
                running = false
                break
            }
            delay(16)
        }
    }

    var dragStart by remember { mutableStateOf<Offset?>(null) }
    Canvas(modifier=Modifier.fillMaxSize().pointerInput(Unit){
        detectDragGestures(onDragStart = { offset -> dragStart = offset },
            onDragEnd = {
                val end = dragStart // end will be set by last move if available; prototype uses start->last
                // no-op here; real implementation would store segments while dragging
                dragStart = null
            },
            onDrag = { change, _ ->
                // treat the drag as instantaneous line from first touch to current pointer
            }
        )
    }){
        canvasSize = Size(size.width, size.height)
        drawRect(color=Color(0xFF101214), size = size)
        for(seg in linesState){
            drawLine(color=Color.White, strokeWidth=6f, start=Offset(seg.first.first, seg.first.second), end=Offset(seg.second.first, seg.second.second))
        }
        val b = ball
        if(b!=null){
            drawCircle(color=Color(0xFF00D4FF), radius=b.r, center=Offset(b.x, b.y))
        }
    }
}

fun stateListToList(list: List<Pair<Pair<Float,Float>, Pair<Float,Float>>>) = list

fun convexHull(points: List<Offset>): List<Offset>{
    if(points.size<=1) return points
    val pts = points.distinctBy{ Pair(it.x, it.y) }.sortedWith(compareBy({it.x},{it.y}))
    val lower = mutableListOf<Offset>()
    for(p in pts){
        while(lower.size>=2 && cross(lower[lower.size-2], lower[lower.size-1], p) <= 0f) lower.removeAt(lower.size-1)
        lower.add(p)
    }
    val upper = mutableListOf<Offset>()
    for(p in pts.reversed()){
        while(upper.size>=2 && cross(upper[upper.size-2], upper[upper.size-1], p) <= 0f) upper.removeAt(upper.size-1)
        upper.add(p)
    }
    lower.removeAt(lower.size-1); upper.removeAt(upper.size-1)
    return (lower+upper)
}
fun cross(a:Offset, b:Offset, c:Offset): Float{
    return (b.x - a.x)*(c.y - a.y) - (b.y - a.y)*(c.x - a.x)
}

fun polygonArea(pts: List<Offset>): Float{
    if(pts.size<3) return 0f
    var area = 0f
    for(i in pts.indices){
        val j = (i+1)%pts.size
        area += pts[i].x*pts[j].y - pts[j].x*pts[i].y
    }
    return kotlin.math.abs(area)/2f
}
