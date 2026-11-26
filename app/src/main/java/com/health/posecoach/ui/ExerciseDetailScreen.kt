package com.health.posecoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.posecoach.data.Exercise

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExerciseDetailScreen(ex: Exercise, onBack: () -> Unit, onStart: () -> Unit) {
    val t = ex.template
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ex.name) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } }
            )
        },
        bottomBar = {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp)
            ) {
                Icon(Icons.Filled.CameraAlt, null)
                Spacer(Modifier.width(8.dp))
                Text("카메라로 자세 체크", fontSize = 16.sp)
            }
        }
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${ex.category.label} · ${ex.subGroup}", color = Brand, fontWeight = FontWeight.Medium)
                DifficultyBadge(ex.difficulty)
            }
            Section("발달 근육") {
                Text("주동근: ${ex.primaryMuscles}", fontWeight = FontWeight.Medium)
                Text("보조근: ${ex.secondaryMuscles}", color = Color(0xFF555555))
            }
            Section("운동 효과") { Text(ex.description, lineHeight = 22.sp) }
            Section("기준 자세 (${t.view.label} 촬영 · ${t.keyPosition})") {
                ReferencePosePreview(
                    t,
                    Modifier.fillMaxWidth().height(220.dp).background(Color(0xFFF2F6F4), RoundedCornerShape(12.dp))
                )
                Spacer(Modifier.height(6.dp))
                Text("촬영 방법: ${t.view.guide}. 몸 전체가 화면에 들어오게 2~3m 떨어지세요.", fontSize = 13.sp, color = Color(0xFF555555))
            }
            Section("평가 항목") {
                t.rules.forEach { Text("• ${it.describe()}", fontSize = 14.sp) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color(0xFFFAFAFA), RoundedCornerShape(12.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        content()
    }
}
