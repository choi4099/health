package com.health.posecoach.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.health.posecoach.data.Exercise
import com.health.posecoach.pose.BodyPart
import com.health.posecoach.pose.Evaluation
import com.health.posecoach.pose.PoseAnalyzer
import com.health.posecoach.pose.PoseFrame
import com.health.posecoach.pose.PoseScorer
import com.health.posecoach.pose.ScoreSmoother
import kotlinx.coroutines.delay
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.roundToInt

/** 측정 결과를 결과 화면으로 넘기는 저장소 */
data class SessionResult(val exerciseId: Int, val best: Evaluation, val averageTotal: Int, val frames: Int)

object SessionStore { var last: SessionResult? = null }

private enum class Phase { IDLE, COUNTDOWN, RECORDING }

private const val RECORD_MS = 5000L

@Composable
fun CameraScreen(ex: Exercise, onBack: () -> Unit, onFinished: () -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val template = ex.template

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA) }

    var useFront by remember { mutableStateOf(true) }
    var frame by remember { mutableStateOf<PoseFrame?>(null) }
    var eval by remember { mutableStateOf<Evaluation?>(null) }
    var live by remember { mutableStateOf<Map<String, Int?>>(emptyMap()) }
    var phase by remember { mutableStateOf(Phase.IDLE) }
    var countdown by remember { mutableIntStateOf(0) }
    val collected = remember { mutableListOf<Evaluation>() }
    val smoother = remember { ScoreSmoother() }

    val analyzer = remember {
        PoseAnalyzer { f ->
            val e = PoseScorer.evaluate(template, f.skeleton)
            frame = f
            eval = e
            live = buildMap {
                put("total", smoother.smooth("total", e.total))
                BodyPart.entries.forEach { put(it.name, smoother.smooth(it.name, e.parts[it])) }
            }
            if (phase == Phase.RECORDING && e.total != null) collected += e
        }
    }
    DisposableEffect(Unit) { onDispose { analyzer.close() } }

    val previewView = remember {
        PreviewView(ctx).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    DisposableEffect(useFront, hasPermission) {
        val executor = Executors.newSingleThreadExecutor()
        val future = ProcessCameraProvider.getInstance(ctx)
        if (hasPermission) {
            future.addListener({
                val provider = future.get()
                val selector = ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .build()
                val preview = Preview.Builder().setResolutionSelector(selector).build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(selector)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor, analyzer)
                val cam = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycleOwner, cam, preview, analysis)
                } catch (e: Exception) {
                    Toast.makeText(ctx, "카메라를 열 수 없어요: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }, ContextCompat.getMainExecutor(ctx))
        }
        onDispose {
            runCatching { future.get().unbindAll() }
            executor.shutdown()
            smoother.reset()
        }
    }

    // 측정: 3초 카운트다운 → 5초 기록 → 가장 좋은 순간을 결과로
    LaunchedEffect(phase) {
        if (phase == Phase.COUNTDOWN) {
            for (i in 3 downTo 1) { countdown = i; delay(1000) }
            collected.clear()
            phase = Phase.RECORDING
        } else if (phase == Phase.RECORDING) {
            delay(RECORD_MS)
            val frames = collected.toList()
            phase = Phase.IDLE
            if (frames.isEmpty()) {
                Toast.makeText(ctx, "몸이 인식되지 않았어요. 몸 전체가 화면에 들어오게 해 주세요.", Toast.LENGTH_LONG).show()
            } else {
                val best = frames.maxBy { it.total!! }
                SessionStore.last = SessionResult(ex.id, best, frames.map { it.total!! }.average().roundToInt(), frames.size)
                onFinished()
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
            PoseOverlay(frame, eval, ex, mirror = useFront, modifier = Modifier.fillMaxSize())
        } else {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("자세를 분석하려면 카메라 권한이 필요해요.", color = Color.White)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) { Text("권한 허용") }
            }
        }

        // 상단 바
        Column(Modifier.fillMaxWidth().systemBarsPadding().padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로", tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(ex.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("${template.view.guide} · ${template.keyPosition}", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                }
                IconButton(onClick = { useFront = !useFront }) { Icon(Icons.Filled.Cameraswitch, "카메라 전환", tint = Color.White) }
            }
            Spacer(Modifier.height(6.dp))
            LiveScores(live)
        }

        // 카운트다운 / 기록 중 표시
        when (phase) {
            Phase.COUNTDOWN -> Text("$countdown", color = Color.White, fontSize = 96.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center))
            Phase.RECORDING -> Text("● 측정 중 – 기준 자세를 유지하세요", color = Bad, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center).background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(8.dp)).padding(12.dp))
            Phase.IDLE -> {}
        }

        // 하단: 실시간 문제점 + 측정 버튼
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().systemBarsPadding().padding(12.dp)) {
            val issues = eval?.issues?.take(2).orEmpty()
            val hidden = eval?.notVisible?.size ?: 0
            Column(
                Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                when {
                    frame == null || eval?.total == null -> Text("사람을 찾는 중… 몸 전체가 보이게 서 주세요.", color = Color.White, fontSize = 14.sp)
                    issues.isEmpty() -> Text("좋아요! 기준 자세와 잘 맞아요.", color = Good, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    else -> issues.forEach {
                        Text("⚠ ${it.issue}", color = Warn, fontSize = 13.sp)
                        Text("→ ${it.advice}", color = Color.White, fontSize = 13.sp)
                    }
                }
                if (hidden > 0 && eval?.total != null) Text("일부 관절이 화면에 잘 안 보여요 ($hidden 항목 제외)", color = Color.LightGray, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { if (phase == Phase.IDLE) phase = Phase.COUNTDOWN },
                enabled = hasPermission,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (phase == Phase.IDLE) "측정 시작 (5초)" else "측정 중…", fontSize = 16.sp) }
        }
    }
}

@Composable
private fun LiveScores(live: Map<String, Int?>) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        val total = live["total"]
        Column(
            Modifier.background(scoreColor(total), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("종합", color = Color.White, fontSize = 11.sp)
            Text(total?.toString() ?: "–", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        BodyPart.entries.forEach { p ->
            val v = live[p.name]
            Column(
                Modifier.background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(p.label, color = Color.White, fontSize = 11.sp)
                Text(v?.toString() ?: "–", color = scoreColor(v), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(0.dp))
        }
    }
}

/** 카메라 화면 위에 사용자 뼈대(부위별 색)와 기준 자세(흰색 반투명)를 겹쳐 그림 */
@Composable
private fun PoseOverlay(frame: PoseFrame?, eval: Evaluation?, ex: Exercise, mirror: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val f = frame ?: return@Canvas
        if (f.imageWidth == 0 || f.imageHeight == 0) return@Canvas
        val scale = max(size.width / f.imageWidth, size.height / f.imageHeight)
        val ox = (size.width - f.imageWidth * scale) / 2f
        val oy = (size.height - f.imageHeight * scale) / 2f
        val user = f.skeleton
            .filterValues { it.conf >= PoseScorer.MIN_CONF }
            .mapValues { (_, p) ->
                val x = p.x * scale + ox
                Offset(if (mirror) size.width - x else x, p.y * scale + oy)
            }
        RefAligner.align(ex.template, user)?.let { ref ->
            drawSkeleton(ref, 18f, { Color.White.copy(alpha = 0.45f) })
        }
        drawSkeleton(user, 9f, partColors(eval), Color.White)
    }
}
