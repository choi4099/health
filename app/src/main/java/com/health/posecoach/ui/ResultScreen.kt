package com.health.posecoach.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.posecoach.data.Exercise
import com.health.posecoach.pose.BodyPart
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(ex: Exercise, onRetry: () -> Unit, onList: () -> Unit) {
    val result = SessionStore.last?.takeIf { it.exerciseId == ex.id }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${ex.name} 결과") },
                navigationIcon = { IconButton(onClick = onList) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "목록") } }
            )
        }
    ) { pad ->
        if (result == null) {
            Box(Modifier.fillMaxSize().padding(pad), contentAlignment = Alignment.Center) { Text("측정 결과가 없어요.") }
        } else {
        val e = result.best
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 종합 점수
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(110.dp).background(scoreColor(e.total), CircleShape), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${e.total ?: 0}", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                        Text("종합 점수", color = Color.White, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(grade(e.total), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("5초 평균 ${result.averageTotal}점 · 분석 ${result.frames}프레임", fontSize = 13.sp, color = Color(0xFF666666))
                    Text("가장 좋았던 순간 기준", fontSize = 13.sp, color = Color(0xFF666666))
                }
            }

            // 부위별 점수
            Section("부위별 점수") {
                BodyPart.entries.forEach { p ->
                    val v = e.parts[p]
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(p.label, Modifier.width(44.dp), fontWeight = FontWeight.Medium)
                        LinearProgressIndicator(
                            progress = { (v ?: 0) / 100f },
                            color = scoreColor(v), trackColor = Color(0xFFE6E6E6),
                            modifier = Modifier.weight(1f).height(10.dp)
                        )
                        Text(v?.let { "$it" } ?: "해당 없음", Modifier.width(72.dp).padding(start = 10.dp), fontSize = 14.sp)
                    }
                }
            }

            // 문제점과 개선 방법
            val issues = e.issues
            Section("문제점과 개선 방법") {
                if (issues.isEmpty()) {
                    Text("문제점이 발견되지 않았어요. 지금 자세를 유지하세요! 👍", color = Good)
                } else {
                    issues.forEachIndexed { i, r ->
                        Column(
                            Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(8.dp)).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text("${i + 1}. 문제점", fontWeight = FontWeight.Bold, color = Bad, fontSize = 13.sp)
                            Text(r.issue ?: "", fontSize = 14.sp)
                            Text("개선 방법", fontWeight = FontWeight.Bold, color = Brand, fontSize = 13.sp)
                            Text(r.advice ?: "", fontSize = 14.sp)
                        }
                    }
                }
                if (e.notVisible.isNotEmpty()) {
                    Text(
                        "화면에 잘 안 보여 평가하지 못한 항목: " + e.notVisible.joinToString { it.rule.label },
                        fontSize = 12.sp, color = Color(0xFF888888)
                    )
                }
            }

            // 세부 측정값
            Section("세부 측정값") {
                e.results.forEach { r ->
                    Row(Modifier.fillMaxWidth()) {
                        Text("${r.rule.part.label} · ${r.rule.label}${r.sideLabel?.let { " ($it)" } ?: ""}", Modifier.weight(1f), fontSize = 13.sp)
                        Text(r.measured?.let { "${it.roundToInt()}°" } ?: "–", Modifier.width(56.dp), fontSize = 13.sp)
                        Text(r.score?.let { "${it}점" } ?: "제외", color = scoreColor(r.score), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onList, modifier = Modifier.weight(1f).height(50.dp)) { Text("목록으로") }
                Button(onClick = onRetry, modifier = Modifier.weight(1f).height(50.dp)) { Text("다시 측정") }
            }
        }
        }
    }
}

private fun grade(total: Int?): String = when {
    total == null -> "측정 불가"
    total >= 90 -> "아주 좋아요"
    total >= 75 -> "좋아요"
    total >= 60 -> "조금만 다듬어요"
    else -> "자세 교정이 필요해요"
}
