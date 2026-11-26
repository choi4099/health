package com.health.posecoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.posecoach.data.Difficulty
import com.health.posecoach.pose.BONES
import com.health.posecoach.pose.BodyPart
import com.health.posecoach.pose.Evaluation
import com.health.posecoach.pose.Joint
import com.health.posecoach.pose.PoseTemplate
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

val Brand = Color(0xFF1F6F5C)
val Good = Color(0xFF2E9E5B)
val Warn = Color(0xFFE0A100)
val Bad = Color(0xFFD9483B)

@Composable
fun HealthTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Brand, secondary = Color(0xFF3C8D7A)),
        content = content
    )
}

fun scoreColor(score: Int?): Color = when {
    score == null -> Color(0xFFB0B0B0)
    score >= 85 -> Good
    score >= 60 -> Warn
    else -> Bad
}

@Composable
fun DifficultyBadge(d: Difficulty) {
    val c = when (d) { Difficulty.BEGINNER -> Good; Difficulty.INTERMEDIATE -> Warn; Difficulty.ADVANCED -> Bad }
    Text(
        "${d.label} " + "★".repeat(d.stars),
        color = c, fontSize = 12.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier
            .background(c.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** 뼈대 그리기. colorOf 로 부위별 색 지정 */
fun DrawScope.drawSkeleton(
    pts: Map<Joint, Offset>,
    width: Float,
    colorOf: (BodyPart) -> Color,
    jointColor: Color? = null
) {
    for ((a, b, part) in BONES) {
        val p1 = pts[a] ?: continue
        val p2 = pts[b] ?: continue
        drawLine(colorOf(part), p1, p2, strokeWidth = width, cap = StrokeCap.Round)
    }
    jointColor?.let { c -> pts.values.forEach { drawCircle(c, radius = width * 0.7f, center = it) } }
    pts[Joint.NOSE]?.let { n ->
        val sl = pts[Joint.L_SHOULDER]; val sr = pts[Joint.R_SHOULDER]
        val r = if (sl != null && sr != null) max(hypot(sl.x - sr.x, sl.y - sr.y) * 0.35f, width * 2f) else width * 2.5f
        drawCircle(colorOf(BodyPart.BODY), radius = r, center = n, style = androidx.compose.ui.graphics.drawscope.Stroke(width * 0.8f))
    }
}

/** 부위 점수 → 색 */
fun partColors(e: Evaluation?): (BodyPart) -> Color = { part -> scoreColor(e?.parts?.get(part)) }

/** 기준 자세 미리보기 (상세 화면용) */
@Composable
fun ReferencePosePreview(t: PoseTemplate, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val ref = t.reference
        val minX = ref.values.minOf { it.x }; val maxX = ref.values.maxOf { it.x }
        val minY = ref.values.minOf { it.y } - 0.25f; val maxY = ref.values.maxOf { it.y } + 0.1f
        val s = min(size.width / (maxX - minX + 0.4f), size.height / (maxY - minY + 0.2f))
        val ox = (size.width - (maxX - minX) * s) / 2 - minX * s
        val oy = (size.height - (maxY - minY) * s) / 2 - minY * s
        val pts = ref.mapValues { (_, p) -> Offset(p.x * s + ox, p.y * s + oy) }
        drawSkeleton(pts, 10f, { Brand }, Brand)
    }
}

/**
 * 기준 자세(정규화 좌표)를 화면 속 사람 위에 겹치기.
 * 골반 중심과 몸통 길이로 위치·크기를 맞추고, 좌우 반전 중 더 잘 맞는 쪽을 고른다.
 */
object RefAligner {
    private fun center(m: Map<Joint, Offset>, a: Joint, b: Joint): Offset? {
        val p = m[a]; val q = m[b]
        return when {
            p != null && q != null -> Offset((p.x + q.x) / 2, (p.y + q.y) / 2)
            else -> p ?: q
        }
    }

    private val PAIRS = listOf(
        Joint.L_SHOULDER to Joint.R_SHOULDER, Joint.L_ELBOW to Joint.R_ELBOW, Joint.L_WRIST to Joint.R_WRIST,
        Joint.L_KNEE to Joint.R_KNEE, Joint.L_ANKLE to Joint.R_ANKLE
    )

    fun align(t: PoseTemplate, user: Map<Joint, Offset>): Map<Joint, Offset>? {
        val hip = center(user, Joint.L_HIP, Joint.R_HIP) ?: return null
        val sho = center(user, Joint.L_SHOULDER, Joint.R_SHOULDER) ?: return null
        val torso = hypot(hip.x - sho.x, hip.y - sho.y)
        if (torso < 30f) return null
        val candidates = listOf(1f, -1f).map { flip ->
            t.reference.mapValues { (_, p) -> Offset(hip.x + flip * p.x * torso, hip.y + p.y * torso) }
        }
        return candidates.minByOrNull { cand ->
            var cost = 0f
            for ((a, b) in PAIRS) {
                val u = center(user, a, b) ?: continue
                val r = center(cand, a, b) ?: continue
                cost += (u.x - r.x) * (u.x - r.x) + (u.y - r.y) * (u.y - r.y)
            }
            val un = user[Joint.NOSE]; val rn = cand[Joint.NOSE]
            if (un != null && rn != null) cost += (un.x - rn.x) * (un.x - rn.x) + (un.y - rn.y) * (un.y - rn.y)
            cost
        }
    }
}
