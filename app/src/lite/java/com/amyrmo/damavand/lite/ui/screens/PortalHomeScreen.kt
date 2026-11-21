package com.amyrmo.damavand.lite.ui.screens

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import com.amyrmo.damavand.lite.AutoSelectIds
import com.amyrmo.damavand.lite.HOME_DESIGN_DAMAVAND
import com.amyrmo.damavand.lite.HOME_DESIGN_PORTAL
import com.amyrmo.damavand.lite.Chain
import com.amyrmo.damavand.lite.Profile
import com.amyrmo.damavand.lite.R
import com.amyrmo.damavand.lite.ui.ConnState
import com.amyrmo.damavand.lite.ui.ConnectionUiState
import com.amyrmo.damavand.lite.ui.LiteViewModel
import com.amyrmo.damavand.lite.ui.theme.LiteColors
import com.amyrmo.damavand.lite.ui.theme.LiteFormat
import com.amyrmo.damavand.lite.ui.theme.Vazirmatn
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * 1.0.1.8 (Portal interface): faithful port of the old Damavand v1
 * Portal home design (`ui/home/PortalHome.kt` + the StageColumn /
 * ParticleBurst helpers from `ui/home/HomeScreen.kt`), adapted to
 * Lite: LiteColors (Damavand Red is the only accent), Lite's
 * ConnectionUiState / ConnState, R.string, Vazirmatn, and Lite's own
 * ConnectionInfoPill. What Lite does not have is left out on
 * purpose: no MotionBudget (always animated), no onboarding tour
 * targets, no FaultBanner (Lite has no ConnectionFault), no vault
 * lock note, no 5-tap secret, and no PortalTopBar — MainActivity's
 * LiteHeader already shows the state pill above every tab.
 *
 * The "Damavand" interface is SummitHomeScreen; MainActivity shows
 * one or the other by the persisted home_design setting.
 */

private val SearchShapes: List<RoundedPolygon> = listOf(
    MaterialShapes.SoftBurst,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Pentagon,
    MaterialShapes.Pill,
    MaterialShapes.Sunny,
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Oval,
)

private const val SEARCH_STEP_SECONDS = 0.72f
private const val TUNNEL_RINGS = 6
private const val TAU = (2.0 * PI).toFloat()

@Composable
fun PortalHomeScreen(
    vm: LiteViewModel,
    connection: ConnectionUiState,
    profiles: List<Profile>,
    selectedId: String?,
    onConnectRequest: () -> Unit,
    onGoToServers: () -> Unit,
    scrollState: ScrollState,
) {
    // Same active-profile resolution as SummitHomeScreen: a selected
    // chain shows its EXIT member, an Auto shows its live member.
    val chains by vm.chains.collectAsState()
    val selectedChain = selectedId?.takeIf { it.startsWith("chain:") }
        ?.removePrefix("chain:")
        ?.let { id -> chains.firstOrNull { it.id == id } }
    val chainMembers = selectedChain?.memberIds?.mapNotNull { id ->
        profiles.firstOrNull { it.id == id }
    } ?: emptyList()
    val selectedAutoId = selectedId?.takeIf { AutoSelectIds.isAuto(it) }
    val autoSession by vm.autoSession.collectAsState(initial = null)
    val autoGroupKey = selectedAutoId?.let { AutoSelectIds.groupKeyOf(it) }
    val autoMembers = autoGroupKey?.let { vm.profilesForGroup(it) } ?: emptyList()
    val autoLive = autoSession?.takeIf { it.profileId == selectedAutoId }
    val profile = if (selectedChain != null) {
        chainMembers.lastOrNull()
    } else if (selectedAutoId != null) {
        autoLive?.selectedProfileId?.let { id -> profiles.firstOrNull { it.id == id } }
            ?: autoMembers.firstOrNull()
    } else {
        profiles.firstOrNull { it.id == selectedId } ?: profiles.firstOrNull()
    }

    val state = connection.state
    val live = state == ConnState.CONNECTED
    val searching = state == ConnState.CONNECTING
    val clock = rememberPortalClock(live || searching)
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val localProxyOn by vm.localProxy.collectAsState(initial = true)
    val socksPort by vm.socksPort.collectAsState(initial = "1080")
    val localProxyAddress = if (localProxyOn) "127.0.0.1:$socksPort" else null
    var burst by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize().background(LiteColors.bg)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 640.dp
            Column(
                Modifier.fillMaxSize().widthIn(max = 560.dp).align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    val scroll = scrollState
                    StageColumn(
                        viewport = maxHeight,
                        stageMin = if (compact) 180.dp else 220.dp,
                        modifier = Modifier.fillMaxWidth().scrollFade(scroll).verticalScroll(scroll),
                    ) {
                        Box(Modifier.fillMaxWidth().layoutId(STAGE_ID)) {
                            PortalStage(
                                state = state,
                                elapsed = connection.elapsedSeconds,
                                downloadBps = connection.downSpeed,
                                uploadBps = connection.upSpeed,
                                clock = clock,
                                onTap = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (live) vm.disconnect() else onConnectRequest()
                                },
                                onLongPress = { burst++ },
                            )
                        }

                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            PortalCaption(state, compact, onStatusTap = { vm.refreshLocation() })
                            AnimatedVisibility(visible = live) {
                                Column {
                                    Spacer(Modifier.height(14.dp))
                                    ConnectionInfoPill(
                                        connection = connection,
                                        onRefresh = { vm.refreshLocation() },
                                        profile = profile,
                                        chain = selectedChain,
                                        chainMembers = chainMembers,
                                    )
                                }
                            }
                            if (localProxyAddress != null && live) {
                                Spacer(Modifier.height(12.dp))
                                Surface(
                                    onClick = {
                                        val clip = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                            as? ClipboardManager
                                        clip?.setPrimaryClip(
                                            ClipData.newPlainText("proxy", localProxyAddress)
                                        )
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.copied),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    },
                                    shape = CircleShape,
                                    color = LiteColors.surface2,
                                ) {
                                    Text(
                                        text = stringResource(R.string.home_local_proxy, localProxyAddress),
                                        style = TextStyle(
                                            fontFamily = Vazirmatn,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                        ),
                                        color = LiteColors.textSecondary,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.height(if (compact) 10.dp else 18.dp))
                        }
                    }
                }
                FlowMeter(
                    live = live,
                    downloadBps = connection.downSpeed,
                    uploadBps = connection.upSpeed,
                    totalDownload = connection.totalDown,
                    totalUpload = connection.totalUp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(8.dp))
                PortalTicket(
                    name = profile?.name,
                    detail = profile?.let { "${it.address}:${it.port}" },
                    locked = false,
                    onClick = onGoToServers,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
                Spacer(Modifier.height(12.dp))
            }
        }
        ParticleBurst(trigger = burst)
    }
}

@Composable
private fun rememberPortalClock(running: Boolean): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val base = time.floatValue
        var start = -1L
        while (true) {
            withInfiniteAnimationFrameNanos { now ->
                if (start < 0L) start = now
                time.floatValue = base + (now - start) / 1_000_000_000f
            }
        }
    }
    return time
}

@Composable
private fun PortalStage(
    state: ConnState,
    elapsed: Int,
    downloadBps: Long,
    uploadBps: Long,
    clock: State<Float>,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val door = (minOf(maxWidth, maxHeight) * 0.56f).coerceIn(124.dp, 272.dp)
        WallOfLight(
            state = state,
            doorRadius = door / 2,
            downloadBps = downloadBps,
            uploadBps = uploadBps,
            clock = clock,
            modifier = Modifier.matchParentSize(),
        )
        PortalDoor(state, elapsed, door, clock, onTap, onLongPress)
    }
}

private class WallSky {
    var width = -1f
    var height = -1f
    var spacing = -1f
    var baseX = FloatArray(0)
    var baseY = FloatArray(0)
    val layers = Array(4) { FloatArray(0) }
    val fill = IntArray(4)
    val paint = Paint().apply {
        strokeCap = StrokeCap.Round
        isAntiAlias = true
    }
    val angle = FloatArray(MAX_PARTICLES)
    val reach = FloatArray(MAX_PARTICLES)
    val speed = FloatArray(MAX_PARTICLES)
    val inbound = BooleanArray(MAX_PARTICLES)
    val alive = BooleanArray(MAX_PARTICLES)
    var lastTime = -1f
    var downDebt = 0f
    var upDebt = 0f
    var moonA = 0f
    var moonB = 0f
    val random = Random(19)

    fun ensure(w: Float, h: Float, s: Float) {
        if (w == width && h == height && s == spacing) return
        width = w
        height = h
        spacing = s
        val cols = floor(w / s).toInt() + 3
        val rows = floor(h / s).toInt() + 3
        val n = cols * rows
        baseX = FloatArray(n)
        baseY = FloatArray(n)
        val ox = (w - (cols - 1) * s) / 2f
        val oy = (h - (rows - 1) * s) / 2f
        var i = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                baseX[i] = ox + c * s + if (r % 2 == 1) s / 2f else 0f
                baseY[i] = oy + r * s
                i++
            }
        }
        for (k in layers.indices) layers[k] = FloatArray(n * 2)
    }

    fun spawn(inward: Boolean) {
        val slot = alive.indexOfFirst { !it }
        if (slot < 0) return
        alive[slot] = true
        inbound[slot] = inward
        angle[slot] = random.nextFloat() * TAU
        reach[slot] = if (inward) 2.9f + random.nextFloat() * 0.4f else 1.02f
        speed[slot] = (0.75f + random.nextFloat() * 0.8f) * if (inward) -1f else 1f
    }

    fun step(time: Float, down: Float, up: Float, flowing: Boolean) {
        val dt = if (lastTime < 0f) 0f else (time - lastTime).coerceIn(0f, 0.1f)
        lastTime = time
        if (dt == 0f) return
        moonA += dt * (0.35f + down * 3.4f)
        moonB -= dt * (0.28f + up * 2.8f)
        if (flowing) {
            downDebt += dt * down * 22f
            upDebt += dt * up * 16f
            while (downDebt >= 1f) {
                spawn(true)
                downDebt -= 1f
            }
            while (upDebt >= 1f) {
                spawn(false)
                upDebt -= 1f
            }
        }
        for (i in 0 until MAX_PARTICLES) {
            if (!alive[i]) continue
            reach[i] += speed[i] * dt
            if (reach[i] < 1.0f || reach[i] > 3.4f) alive[i] = false
        }
    }
}

private const val MAX_PARTICLES = 72

@Composable
private fun WallOfLight(
    state: ConnState,
    doorRadius: Dp,
    downloadBps: Long,
    uploadBps: Long,
    clock: State<Float>,
    modifier: Modifier,
) {
    val opening by animateFloatAsState(
        when (state) {
            ConnState.CONNECTED -> 1f
            ConnState.CONNECTING -> 0.34f
            else -> 0f
        },
        spring(dampingRatio = 0.7f, stiffness = 80f),
        label = "wall-opening",
    )
    val glow by animateFloatAsState(
        when (state) {
            ConnState.CONNECTED -> 1f
            ConnState.CONNECTING -> 0.7f
            ConnState.ERROR -> 0.75f
            else -> 0f
        },
        tween(900),
        label = "wall-glow",
    )
    val searching = state == ConnState.CONNECTING
    val wave by animateFloatAsState(if (searching) 1f else 0f, tween(700), label = "wall-wave")
    val error = state == ConnState.ERROR
    val glowInner by animateColorAsState(
        if (error) LiteColors.error else LiteColors.accent, tween(700), label = "glow-inner",
    )
    val glowOuter by animateColorAsState(
        if (error) LiteColors.error else LiteColors.green, tween(700), label = "glow-outer",
    )
    val dotColor = LiteColors.text
    val accent = LiteColors.accent
    val downColor = LiteColors.green
    val upColor = LiteColors.accent
    val down = rateFraction(downloadBps)
    val up = rateFraction(uploadBps)
    val flowing = state == ConnState.CONNECTED
    val sky = remember { WallSky() }
    val density = LocalDensity.current
    val spacing = with(density) { 19.dp.toPx() }
    val dotSize = with(density) { 2.4.dp.toPx() }
    val ringStroke = with(density) { 1.2.dp.toPx() }
    val dash = with(density) { floatArrayOf(2.dp.toPx(), 7.dp.toPx()) }
    val moonSize = with(density) { 4.dp.toPx() }
    val particleSize = with(density) { 2.2.dp.toPx() }

    Canvas(modifier) {
        val t = clock.value
        sky.ensure(size.width, size.height, spacing)
        sky.step(t, down, up, flowing)
        val c = center
        val r = doorRadius.toPx()
        val reachMax = sqrt(c.x * c.x + c.y * c.y)

        if (glow > 0.01f) {
            val breath = when {
                searching -> 0.82f + 0.18f * sin(t * 3.1f)
                state == ConnState.CONNECTED -> 0.9f + 0.1f * sin(t * 1.3f)
                else -> 1f
            }
            val g = glow * breath
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(glowInner.copy(alpha = 0.40f * g), glowOuter.copy(alpha = 0.14f * g), Color.Transparent),
                    center = c,
                    radius = r * 2.7f,
                ),
                radius = r * 2.7f,
                center = c,
            )
        }

        for (k in 0 until 4) sky.fill[k] = 0
        val n = sky.baseX.size
        val gain = 0.55f + 0.45f * opening
        for (i in 0 until n) {
            val dx = sky.baseX[i] - c.x
            val dy = sky.baseY[i] - c.y
            val d = sqrt(dx * dx + dy * dy)
            val q = d / r
            val nd = d + opening * r * 0.78f / (1f + q * q * q)
            if (nd < r * 1.04f) continue
            val swirl = opening * 0.42f * (r / (nd + r * 0.6f)) * sin(t * 0.38f + q * 0.55f)
            val a = atan2(dy, dx) + swirl
            val fade = (1f - nd / reachMax).coerceIn(0f, 1f)
            val ripple = wave * (0.5f + 0.5f * sin(nd / r * 3.3f - t * 5.2f))
            val alpha = fade * gain * (0.8f + 0.9f * ripple)
            val layer = when {
                alpha > 0.62f -> 0
                alpha > 0.40f -> 1
                alpha > 0.20f -> 2
                alpha > 0.05f -> 3
                else -> continue
            }
            val arr = sky.layers[layer]
            val at = sky.fill[layer] * 2
            arr[at] = c.x + cos(a) * nd
            arr[at + 1] = c.y + sin(a) * nd
            sky.fill[layer]++
        }
        val tint = lerp(dotColor, accent, (opening * 0.85f + wave * 0.35f).coerceAtMost(1f))
        val layerAlpha = floatArrayOf(0.52f, 0.36f, 0.22f, 0.10f)
        drawIntoCanvas { canvas ->
            sky.paint.strokeWidth = dotSize
            for (k in 0 until 4) {
                val count = sky.fill[k]
                if (count == 0) continue
                val arr = sky.layers[k]
                for (j in count * 2 until arr.size) arr[j] = -10_000f
                sky.paint.color = tint.copy(alpha = layerAlpha[k])
                canvas.drawRawPoints(PointMode.Points, arr, sky.paint)
            }
        }

        val ringAlpha = 0.10f + 0.16f * opening + 0.08f * wave
        val ringColor = lerp(dotColor, accent, opening)
        val solid = opening > 0.6f
        val effect = if (solid) null else PathEffect.dashPathEffect(dash, if (searching) -t * 60f else 0f)
        drawCircle(ringColor.copy(alpha = ringAlpha), r * 1.30f, c, style = Stroke(ringStroke, pathEffect = effect))
        drawCircle(ringColor.copy(alpha = ringAlpha * 0.7f), r * 1.62f, c, style = Stroke(ringStroke, pathEffect = effect))

        if (opening > 0.5f) {
            val moonAlpha = ((opening - 0.5f) * 2f).coerceIn(0f, 1f)
            for (trail in 0 until 5) {
                val lag = trail * 0.09f
                val fadeOut = moonAlpha * (1f - trail / 5f)
                drawCircle(
                    downColor.copy(alpha = fadeOut),
                    moonSize * (1f - trail * 0.14f),
                    Offset(c.x + cos(sky.moonA - lag) * r * 1.30f, c.y + sin(sky.moonA - lag) * r * 1.30f),
                )
                drawCircle(
                    upColor.copy(alpha = fadeOut),
                    moonSize * 0.8f * (1f - trail * 0.14f),
                    Offset(c.x + cos(sky.moonB + lag) * r * 1.62f, c.y + sin(sky.moonB + lag) * r * 1.62f),
                )
            }
        }

        for (i in 0 until MAX_PARTICLES) {
            if (!sky.alive[i]) continue
            val dist = sky.reach[i]
            val life = if (sky.inbound[i]) ((dist - 1f) / 2.2f).coerceIn(0f, 1f).let { 1f - (1f - it) * (1f - it) }
            else (1f - (dist - 1f) / 2.4f).coerceIn(0f, 1f)
            val fadeNear = ((dist - 1f) / 0.25f).coerceIn(0f, 1f)
            drawCircle(
                (if (sky.inbound[i]) downColor else upColor).copy(alpha = 0.85f * life * fadeNear),
                particleSize,
                Offset(c.x + cos(sky.angle[i]) * dist * r, c.y + sin(sky.angle[i]) * dist * r),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PortalDoor(
    state: ConnState,
    elapsed: Int,
    doorSize: Dp,
    clock: State<Float>,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val searching = state == ConnState.CONNECTING
    val connected = state == ConnState.CONNECTED
    val error = state == ConnState.ERROR
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(searching) {
        if (!searching) return@LaunchedEffect
        snapshotFlow { (clock.value / SEARCH_STEP_SECONDS).toInt() }.collect { step = it }
    }
    val target: RoundedPolygon = when (state) {
        ConnState.CONNECTED -> MaterialShapes.Circle
        ConnState.ERROR -> MaterialShapes.Burst
        ConnState.CONNECTING -> SearchShapes[step.mod(SearchShapes.size)]
        ConnState.DISCONNECTED -> MaterialShapes.Cookie12Sided
    }
    var from by remember { mutableStateOf(MaterialShapes.Cookie12Sided) }
    var to by remember { mutableStateOf(MaterialShapes.Cookie12Sided) }
    var progress by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(target) {
        if (target === to) return@LaunchedEffect
        Snapshot.withMutableSnapshot {
            from = if (progress >= 0.5f) to else from
            to = target
            progress = 0f
        }
        animate(0f, 1f, animationSpec = spring(dampingRatio = 0.62f, stiffness = 240f)) { value, _ -> progress = value }
    }
    val morph = remember(from, to) { Morph(from, to) }

    val shake = remember { Animatable(0f) }
    LaunchedEffect(error) {
        if (!error) return@LaunchedEffect
        for (x in listOf(14f, -11f, 8f, -5f, 2f, 0f)) shake.animateTo(x, tween(55))
    }

    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val squash by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "door-squash",
    )
    // v1 primaryContainer is a light tint of the accent; a gradient stop
    // needs an opaque color, so the connected center is accent lightened
    // toward white (same idea, no new palette color).
    val c1 by animateColorAsState(
        when {
            connected -> lerp(LiteColors.accent, Color.White, 0.35f)
            searching -> LiteColors.accent
            error -> LiteColors.error
            else -> LiteColors.surface2
        },
        tween(700),
        label = "door-c1",
    )
    val c2 by animateColorAsState(
        when {
            connected -> LiteColors.accent
            searching -> LiteColors.green
            error -> LiteColors.error
            else -> LiteColors.surface2
        },
        tween(700),
        label = "door-c2",
    )
    val c3 by animateColorAsState(
        when {
            connected -> LiteColors.green
            searching -> LiteColors.accent
            error -> LiteColors.error
            else -> LiteColors.surface2
        },
        tween(700),
        label = "door-c3",
    )
    val rim by animateColorAsState(
        when {
            state == ConnState.DISCONNECTED -> LiteColors.hairline
            error -> LiteColors.error
            else -> Color.Transparent
        },
        tween(700),
        label = "door-rim",
    )
    val ink by animateColorAsState(
        when {
            error -> LiteColors.text
            connected -> Color.White
            else -> LiteColors.text
        },
        tween(700),
        label = "door-ink",
    )
    val actionLabel = stringResource(
        when {
            searching -> R.string.action_cancel
            connected -> R.string.action_disconnect
            else -> R.string.action_connect
        },
    )
    val work = remember { Path() }
    val density = LocalDensity.current
    val rimWidth = with(density) { 1.6.dp.toPx() }
    val sealed by animateFloatAsState(
        if (state == ConnState.DISCONNECTED) 1f else 0f,
        tween(500),
        label = "door-sealed",
    )
    val tunnel by animateFloatAsState(
        if (connected) 1f else 0f,
        tween(900),
        label = "door-tunnel",
    )

    Box(
        Modifier
            .size(doorSize)
            .graphicsLayer {
                translationX = shake.value * density.density
                scaleX = squash
                scaleY = squash
            }
            .semantics { contentDescription = actionLabel }
            .combinedClickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onLongClick = onLongPress,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            work.rewind()
            morph.toPath(progress.coerceIn(0f, 1f), work)
            work.transform(Matrix().apply { scale(size.width, size.height) })
            val bounds = work.getBounds()
            work.translate(center - bounds.center)
            val turn = when {
                searching -> clock.value * 150f
                connected -> clock.value * 8f
                else -> 0f
            }
            val brush = if (searching) {
                Brush.sweepGradient(listOf(c1, c2, c3, c1), center)
            } else {
                Brush.radialGradient(listOf(c1, c2, c3), center, size.minDimension / 2f)
            }
            rotate(turn) {
                drawPath(work, brush)
                if (rim.alpha > 0.01f) drawPath(work, rim, style = Stroke(rimWidth))
                if (sealed > 0.01f) {
                    scale(0.78f, center) {
                        drawPath(work, rim.copy(alpha = rim.alpha * 0.55f * sealed), style = Stroke(rimWidth / 0.78f))
                    }
                }
            }
            if (tunnel > 0.01f) {
                val radius = size.minDimension / 2f
                val drift = clock.value * 0.22f
                for (k in 0 until TUNNEL_RINGS) {
                    val phase = (drift + k.toFloat() / TUNNEL_RINGS) % 1f
                    val ringRadius = radius * (1f - phase) * 0.92f
                    val life = 4f * phase * (1f - phase)
                    drawCircle(
                        ink.copy(alpha = 0.30f * life * tunnel),
                        ringRadius,
                        center,
                        style = Stroke(rimWidth * (0.6f + (1f - phase) * 0.9f)),
                    )
                }
            }
        }
        AnimatedContent(
            targetState = connected,
            transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
            label = "door-center",
        ) { open ->
            if (open) {
                Text(
                    text = LiteFormat.toPersian(LiteFormat.formatElapsed(elapsed)),
                    style = TextStyle(
                        fontFamily = Vazirmatn,
                        fontWeight = FontWeight.Bold,
                        fontSize = with(density) { (doorSize * 0.15f).toSp() },
                    ),
                    color = ink,
                    maxLines = 1,
                    softWrap = false,
                )
            } else {
                Icon(
                    painterResource(R.drawable.ic_portal_glyph),
                    contentDescription = null,
                    tint = ink,
                    modifier = Modifier.size(doorSize * 0.36f),
                )
            }
        }
    }
}

@Composable
private fun PortalCaption(state: ConnState, compact: Boolean, onStatusTap: () -> Unit) {
    AnimatedContent(
        targetState = state,
        transitionSpec = {
            (fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 3 }) togetherWith
                (fadeOut(tween(180)) + slideOutVertically(tween(220)) { -it / 4 })
        },
        label = "portal-caption",
    ) { s ->
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            val headline = TextStyle(
                fontFamily = Vazirmatn,
                fontWeight = FontWeight.Bold,
                fontSize = if (compact) 28.sp else 36.sp,
            )
            Text(
                text = stringResource(
                    when (s) {
                        ConnState.CONNECTED -> R.string.status_connected
                        ConnState.CONNECTING -> R.string.status_connecting
                        ConnState.ERROR -> R.string.status_error
                        ConnState.DISCONNECTED -> R.string.status_disconnected
                    },
                ),
                style = if (s == ConnState.CONNECTED) {
                    headline.copy(
                        brush = Brush.linearGradient(listOf(LiteColors.accent, LiteColors.green)),
                    )
                } else {
                    headline
                },
                color = if (s == ConnState.ERROR) LiteColors.error else LiteColors.text,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onStatusTap,
                ),
            )
            if (s == ConnState.DISCONNECTED) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.ring_tap_to_connect),
                    style = TextStyle(
                        fontFamily = Vazirmatn,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    ),
                    color = LiteColors.accent,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun FlowMeter(
    live: Boolean,
    downloadBps: Long,
    uploadBps: Long,
    totalDownload: Long,
    totalUpload: Long,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        FlowHalf(
            icon = R.drawable.ic_download,
            label = stringResource(R.string.home_download),
            bps = downloadBps,
            total = totalDownload,
            live = live,
            badge = LiteColors.green.copy(alpha = 0.18f),
            onBadge = LiteColors.green,
            shape = RoundedCornerShape(topStart = 26.dp, bottomStart = 26.dp, topEnd = 6.dp, bottomEnd = 6.dp),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        FlowHalf(
            icon = R.drawable.ic_upload,
            label = stringResource(R.string.home_upload),
            bps = uploadBps,
            total = totalUpload,
            live = live,
            badge = LiteColors.accent.copy(alpha = 0.18f),
            onBadge = LiteColors.accent,
            shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 26.dp, bottomEnd = 26.dp),
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun FlowHalf(
    icon: Int,
    label: String,
    bps: Long,
    total: Long,
    live: Boolean,
    badge: Color,
    onBadge: Color,
    shape: RoundedCornerShape,
    modifier: Modifier,
) {
    val (value, unit) = LiteFormat.formatRate(if (live) bps else 0L)
    val busy = live && bps > 0L
    val badgeTurn by animateFloatAsState(if (busy) 45f else 0f, tween(400), label = "flow-turn")
    Surface(shape = shape, color = LiteColors.surface2, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { rotationZ = badgeTurn }
                        .clip(MaterialShapes.Cookie4Sided.toShape())
                        .background(badge),
                )
                Icon(painterResource(icon), contentDescription = null, tint = onBadge, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    style = TextStyle(
                        fontFamily = Vazirmatn,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                    ),
                    color = LiteColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        LiteFormat.toPersian(value),
                        style = TextStyle(
                            fontFamily = Vazirmatn,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp,
                        ),
                        color = if (busy) LiteColors.text else LiteColors.textSecondary,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        unit,
                        style = TextStyle(
                            fontFamily = Vazirmatn,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                        ),
                        color = LiteColors.textSecondary,
                        maxLines = 1,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                Text(
                    LiteFormat.toPersian(LiteFormat.formatVolume(total)),
                    style = TextStyle(
                        fontFamily = Vazirmatn,
                        fontWeight = FontWeight.Normal,
                        fontSize = 11.sp,
                    ),
                    color = LiteColors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PortalTicket(
    name: String?,
    detail: String?,
    locked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val corner by animateDpAsState(
        if (pressed) 18.dp else 30.dp,
        spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "ticket-corner",
    )
    val turn by animateFloatAsState(
        if (pressed) 60f else 0f,
        spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "ticket-turn",
    )
    Surface(
        onClick = onClick,
        interactionSource = source,
        shape = RoundedCornerShape(corner),
        color = LiteColors.accent.copy(alpha = 0.16f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { rotationZ = turn }
                        .clip(MaterialShapes.Cookie9Sided.toShape())
                        .background(LiteColors.accent),
                )
                Icon(
                    painterResource(if (locked) R.drawable.ic_encrypted else R.drawable.ic_public),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = name ?: stringResource(R.string.home_select_config),
                    style = TextStyle(
                        fontFamily = Vazirmatn,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    ),
                    color = LiteColors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // Lite has no vault, so the vault branch of v1 never
                    // fires here; the text is "tap to change" whenever a
                    // config is selected, "no config yet" when none is.
                    text = detail ?: stringResource(
                        if (name == null) R.string.home_no_config_body else R.string.home_tap_to_change,
                    ),
                    style = TextStyle(
                        fontFamily = Vazirmatn,
                        fontWeight = FontWeight.Normal,
                        fontSize = 12.sp,
                    ),
                    color = LiteColors.text.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(LiteColors.text.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = LiteColors.text,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

internal const val STAGE_ID = "home-stage"

private val STAGE_MIN = 148.dp

private fun Modifier.scrollFade(state: ScrollState, length: Dp = 28.dp): Modifier = this
    .graphicsLayer {
        compositingStrategy = if (state.canScrollForward || state.canScrollBackward) {
            CompositingStrategy.Offscreen
        } else {
            CompositingStrategy.Auto
        }
    }
    .drawWithContent {
        drawContent()
        val fade = length.toPx()
        if (state.canScrollForward) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Black, Color.Transparent),
                    startY = size.height - fade,
                    endY = size.height,
                ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (state.canScrollBackward) {
            drawRect(
                brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black), startY = 0f, endY = fade),
                blendMode = BlendMode.DstIn,
            )
        }
    }

private const val MAX_STAGE_PX = 16_384

@Composable
private fun StageColumn(
    viewport: Dp,
    stageMin: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content, modifier) { measurables, constraints ->
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth

        val natural = Constraints(maxWidth = width)
        val placeables = arrayOfNulls<Placeable>(measurables.size)
        var stageIndex = -1
        var fixed = 0
        measurables.forEachIndexed { i, child ->
            if (child.layoutId == STAGE_ID) {
                stageIndex = i
            } else {
                placeables[i] = child.measure(natural).also { fixed += it.height }
            }
        }

        val viewportPx = if (viewport.isFinite) viewport.roundToPx().coerceIn(0, MAX_STAGE_PX) else 0
        val stagePx = if (stageIndex < 0) 0 else maxOf(stageMin.roundToPx(), viewportPx - fixed).coerceIn(0, MAX_STAGE_PX)
        if (stageIndex >= 0) {
            placeables[stageIndex] = measurables[stageIndex].measure(Constraints.fixed(width, stagePx))
        }
        layout(width, maxOf(viewportPx, fixed + stagePx)) {
            var y = 0
            placeables.forEach { placeable ->
                if (placeable == null) return@forEach
                placeable.placeRelative((width - placeable.width) / 2, y)
                y += placeable.height
            }
        }
    }
}

private fun rateFraction(bytesPerSecond: Long): Float {
    if (bytesPerSecond <= 0L) return 0f
    val kb = bytesPerSecond / 1024.0
    return (ln(1.0 + kb) / FULL_SCALE_LN).toFloat().coerceIn(0f, 1f)
}

private val FULL_SCALE_LN = ln(1.0 + 10 * 1024.0)

private class BurstParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val radius: Float,
    val square: Boolean,
    var life: Float = 1f,
)

@Composable
private fun ParticleBurst(trigger: Int) {
    val colors = listOf(LiteColors.accent, LiteColors.green, LiteColors.error, LiteColors.amber)
    val particles = remember { mutableStateListOf<BurstParticle>() }
    var tick by remember { mutableLongStateOf(0L) }
    var canvas by remember { mutableStateOf(Size.Zero) }

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect

        var guard = 0
        while (canvas == Size.Zero && guard < 5) {
            withInfiniteAnimationFrameNanos { }
            guard++
        }
        if (canvas == Size.Zero) return@LaunchedEffect
        val cx = canvas.width / 2f
        val cy = canvas.height * 0.42f
        repeat(56) {
            val angle = Random.nextFloat() * (2f * Math.PI.toFloat())
            val speed = 8f + Random.nextFloat() * 20f
            particles += BurstParticle(
                x = cx,
                y = cy,
                vx = cos(angle) * speed,
                vy = sin(angle) * speed - 8f,
                color = colors[Random.nextInt(colors.size)],
                radius = 7f + Random.nextFloat() * 11f,
                square = Random.nextBoolean(),
            )
        }
        var last = 0L
        while (particles.isNotEmpty()) {
            withInfiniteAnimationFrameNanos { now ->
                val step = if (last == 0L) 1f else ((now - last) / 16_666_666f).coerceIn(0f, 3f)
                last = now
                particles.forEach { p ->
                    p.vy += 0.9f * step
                    p.vx *= 1f - 0.02f * step
                    p.x += p.vx * step
                    p.y += p.vy * step
                    p.life -= 0.014f * step
                }
                particles.removeAll { it.life <= 0f || it.y > canvas.height + 60f }
                tick = now
            }
        }
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .onSizeChanged { canvas = Size(it.width.toFloat(), it.height.toFloat()) },
    ) {
        tick
        particles.forEach { p ->
            val color = p.color.copy(alpha = p.life.coerceIn(0f, 1f))
            if (p.square) {
                drawRect(
                    color = color,
                    topLeft = Offset(p.x - p.radius / 2f, p.y - p.radius / 2f),
                    size = Size(p.radius, p.radius),
                )
            } else {
                drawCircle(color = color, radius = p.radius / 2f, center = Offset(p.x, p.y))
            }
        }
    }
}

/**
 * The Damavand / Portal switcher — shared chrome on the connect page,
 * identical above both home interfaces. The choice is persisted via
 * [com.amyrmo.damavand.lite.ui.LiteViewModel.setHomeDesign].
 */
@Composable
fun HomeDesignSwitch(selected: String, onSelect: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = LiteColors.surface2,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(Modifier.padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            HomeDesignChoice(
                label = stringResource(R.string.app_name),
                selected = selected == HOME_DESIGN_DAMAVAND,
                onClick = { onSelect(HOME_DESIGN_DAMAVAND) },
            )
            HomeDesignChoice(
                label = stringResource(R.string.home_design_portal),
                selected = selected == HOME_DESIGN_PORTAL,
                onClick = { onSelect(HOME_DESIGN_PORTAL) },
            )
        }
    }
}

@Composable
private fun HomeDesignChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) LiteColors.accent else Color.Transparent,
        modifier = Modifier.weight(1f),
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = Vazirmatn,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            ),
            color = if (selected) Color.White else LiteColors.textSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}
