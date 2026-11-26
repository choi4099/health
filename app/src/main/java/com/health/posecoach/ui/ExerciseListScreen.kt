package com.health.posecoach.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.posecoach.data.Category
import com.health.posecoach.data.Difficulty
import com.health.posecoach.data.Exercise
import com.health.posecoach.data.ExerciseRepository

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ExerciseListScreen(onOpen: (Int) -> Unit, onSettings: () -> Unit) {
    var cat by rememberSaveable { mutableStateOf<Category?>(null) }
    var diff by rememberSaveable { mutableStateOf<Difficulty?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(cat, diff, query) {
        ExerciseRepository.all.filter {
            (cat == null || it.category == cat) &&
                (diff == null || it.difficulty == diff) &&
                (query.isBlank() || it.name.contains(query.trim()) || it.primaryMuscles.contains(query.trim()))
        }
    }
    val groups = remember(filtered) { filtered.groupBy { it.category to it.subGroup } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("헬스", fontWeight = FontWeight.Bold) },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, contentDescription = "설정") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Brand, titleContentColor = Color.White, actionIconContentColor = Color.White
                )
            )
        }
    ) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad)) {
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp, 16.dp, 16.dp, 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile("전체", ExerciseRepository.all.size, Modifier.weight(1f))
                    Category.entries.forEach { StatTile(it.label, ExerciseRepository.count(it), Modifier.weight(1f)) }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = cat == null, onClick = { cat = null }, label = { Text("전체") })
                        Category.entries.forEach { c ->
                            FilterChip(selected = cat == c, onClick = { cat = if (cat == c) null else c }, label = { Text(c.label) })
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = diff == null, onClick = { diff = null }, label = { Text("모든 난이도") })
                        Difficulty.entries.forEach { d ->
                            FilterChip(selected = diff == d, onClick = { diff = if (diff == d) null else d }, label = { Text(d.label) })
                        }
                    }
                    OutlinedTextField(
                        value = query, onValueChange = { query = it },
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        placeholder = { Text("동작 또는 근육 이름 (예: 스쿼트, 광배근)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                    Text("${filtered.size}개 동작", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            if (filtered.isEmpty()) {
                item { Text("조건에 맞는 동작이 없어요. 필터를 바꿔 보세요.", Modifier.padding(24.dp)) }
            }
            groups.forEach { (key, group) ->
                stickyHeader(key = "${key.first}-${key.second}") {
                    Text(
                        "${key.first.label} · ${key.second}  (${group.size})",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Brand,
                        modifier = Modifier.fillMaxWidth().background(Color(0xFFEFF6F3)).padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                items(group, key = { it.id }) { ex -> ExerciseRow(ex) { onOpen(ex.id) } }
            }
            item { Spacer(Modifier.padding(24.dp)) }
        }
    }
}

@Composable
private fun StatTile(label: String, n: Int, modifier: Modifier) {
    Column(
        modifier.background(Color(0xFFF2F4F3), RoundedCornerShape(10.dp)).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, fontSize = 12.sp, color = Color(0xFF666666))
        Text("$n", fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ExerciseRow(ex: Exercise, onClick: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ex.name, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(8.dp))
                    DifficultyBadge(ex.difficulty)
                }
                Text(
                    "주동근: ${ex.primaryMuscles}  ·  촬영: ${ex.template.view.label}",
                    fontSize = 13.sp, color = Color(0xFF666666)
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = Color(0xFF999999))
        }
        HorizontalDivider(color = Color(0xFFEDEDED))
    }
}
