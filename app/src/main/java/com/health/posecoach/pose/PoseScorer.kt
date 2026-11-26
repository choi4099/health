package com.health.posecoach.pose

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 좌/우 측정 방식
 * AUTO : 옆면 촬영이면 더 잘 보이는 쪽, 정면/뒷면이면 양쪽 평균
 * BOTH : 양쪽 평균
 * EACH : 양쪽을 각각 평가 (런지처럼 두 다리 모두 중요할 때)
 * BEST : 점수가 더 좋은 쪽 (한 팔/한 다리 동작)
 */
enum class SideMode { AUTO, BOTH, EACH, BEST }

sealed class Rule(val part: BodyPart, val label: String) {
    /** 세 관절 a-b-c 가 b 에서 이루는 각도 */
    class Angle(
        part: BodyPart, label: String,
        val a: G, val b: G, val c: G,
        val target: Float, val tol: Float,
        val lowAdvice: String, val highAdvice: String,
        val mode: SideMode = SideMode.AUTO
    ) : Rule(part, label)

    /** from→to 선분(좌우 중심)이 수직 위에서 기울어진 각도 */
    class Tilt(
        part: BodyPart, label: String,
        val from: G, val to: G,
        val target: Float, val tol: Float,
        val lowAdvice: String, val highAdvice: String
    ) : Rule(part, label)

    /** 좌우 각도 차이 */
    class Symmetry(
        part: BodyPart, label: String,
        val a: G, val b: G, val c: G,
        val tol: Float, val advice: String
    ) : Rule(part, label)

    val goal: Float get() = when (this) { is Angle -> target; is Tilt -> target; is Symmetry -> 0f }
    val tolerance: Float get() = when (this) { is Angle -> tol; is Tilt -> tol; is Symmetry -> tol }

    fun describe(): String = when (this) {
        is Symmetry -> "${part.label} · $label: 좌우 차이 ${tolerance.roundToInt()}° 이내"
        else -> "${part.label} · $label: ${goal.roundToInt()}° ± ${tolerance.roundToInt()}°"
    }
}

data class RuleResult(
    val rule: Rule,
    val measured: Float?,      // null = 관절이 안 보임
    val score: Int?,
    val sideLabel: String?,
    val issue: String?,        // 문제점
    val advice: String?        // 개선 방법
)

data class Evaluation(
    val total: Int?,
    val parts: Map<BodyPart, Int?>,
    val results: List<RuleResult>
) {
    val issues get() = results.filter { it.issue != null }.sortedBy { it.score ?: 101 }
    val notVisible get() = results.filter { it.measured == null }
}

object PoseScorer {
    const val MIN_CONF = 0.5f
    private const val ISSUE_BELOW = 85

    /** 허용 오차 안이면 100점, 오차의 3배에서 0점 */
    fun scoreFor(err: Float, tol: Float): Int {
        if (err <= tol) return 100
        val s = 100f - (err - tol) / (tol * 2f) * 100f
        return s.coerceIn(0f, 100f).roundToInt()
    }

    fun evaluate(t: PoseTemplate, s: Skeleton): Evaluation {
        val autoSide: Side? = if (t.view == CameraView.SIDE) visibleSide(s) else null
        val results = t.rules.map { rule ->
            when (rule) {
                is Rule.Angle -> evalAngle(rule, s, autoSide)
                is Rule.Tilt -> evalTilt(rule, s)
                is Rule.Symmetry -> evalSym(rule, s)
            }
        }
        val parts = BodyPart.entries.associateWith { part ->
            val sc = results.filter { it.rule.part == part }.mapNotNull { it.score }
            if (sc.isEmpty()) null else sc.average().roundToInt()
        }
        val avail = parts.filterValues { it != null }
        val total = if (avail.isEmpty()) null else {
            val wSum = avail.keys.sumOf { it.weight.toDouble() }
            (avail.entries.sumOf { it.key.weight.toDouble() * it.value!! } / wSum).roundToInt()
        }
        return Evaluation(total, parts, results)
    }

    private fun get(s: Skeleton, j: Joint): P? = s[j]?.takeIf { it.conf >= MIN_CONF }

    private fun visibleSide(s: Skeleton): Side {
        fun avg(side: Side) = G.entries.map { s[it.of(side)]?.conf ?: 0f }.average()
        return if (avg(Side.LEFT) >= avg(Side.RIGHT)) Side.LEFT else Side.RIGHT
    }

    private fun angleOn(r: Rule.Angle, s: Skeleton, side: Side): Float? {
        val a = get(s, r.a.of(side)) ?: return null
        val b = get(s, r.b.of(side)) ?: return null
        val c = get(s, r.c.of(side)) ?: return null
        return Geo.angleAt(a, b, c)
    }

    private fun build(rule: Rule, m: Float?, side: Side?, low: String, high: String): RuleResult {
        if (m == null) return RuleResult(rule, null, null, side?.label, null, null)
        val err = abs(m - rule.goal)
        val sc = scoreFor(err, rule.tolerance)
        if (sc >= ISSUE_BELOW) return RuleResult(rule, m, sc, side?.label, null, null)
        val isLow = m < rule.goal
        val where = side?.let { "(${it.label}) " } ?: ""
        val issue = if (rule is Rule.Symmetry) {
            "${rule.part.label} · ${rule.label}: 좌우 차이 ${m.roundToInt()}°"
        } else {
            "${rule.part.label} · ${rule.label} $where${m.roundToInt()}° → 목표 ${rule.goal.roundToInt()}°±${rule.tolerance.roundToInt()}° (${if (isLow) "부족" else "과함"})"
        }
        val advice = (if (isLow) low else high).ifBlank { "${rule.label}을(를) 목표 각도에 맞춰 주세요." }
        return RuleResult(rule, m, sc, side?.label, issue, advice)
    }

    private fun evalAngle(r: Rule.Angle, s: Skeleton, autoSide: Side?): RuleResult {
        val mode = if (r.mode == SideMode.AUTO) (if (autoSide != null) null else SideMode.BOTH) else r.mode
        return when (mode) {
            null -> build(r, angleOn(r, s, autoSide!!), null, r.lowAdvice, r.highAdvice)
            SideMode.BOTH, SideMode.AUTO -> {
                val v = Side.entries.mapNotNull { angleOn(r, s, it) }
                build(r, if (v.isEmpty()) null else v.average().toFloat(), null, r.lowAdvice, r.highAdvice)
            }
            SideMode.EACH -> {
                val per = Side.entries.map { side -> build(r, angleOn(r, s, side), side, r.lowAdvice, r.highAdvice) }
                val ok = per.filter { it.score != null }
                if (ok.isEmpty()) per.first()
                else {
                    val worst = ok.minBy { it.score!! }
                    worst.copy(score = ok.map { it.score!! }.average().roundToInt())
                }
            }
            SideMode.BEST -> {
                val per = Side.entries.map { side -> build(r, angleOn(r, s, side), side, r.lowAdvice, r.highAdvice) }
                per.filter { it.score != null }.maxByOrNull { it.score!! } ?: per.first()
            }
        }
    }

    private fun evalTilt(r: Rule.Tilt, s: Skeleton): RuleResult {
        val f = Geo.mid(get(s, r.from.left), get(s, r.from.right))
        val t = Geo.mid(get(s, r.to.left), get(s, r.to.right))
        val m = if (f != null && t != null) Geo.tiltFromUp(f, t) else null
        return build(r, m, null, r.lowAdvice, r.highAdvice)
    }

    private fun evalSym(r: Rule.Symmetry, s: Skeleton): RuleResult {
        fun ang(side: Side): Float? {
            val a = get(s, r.a.of(side)) ?: return null
            val b = get(s, r.b.of(side)) ?: return null
            val c = get(s, r.c.of(side)) ?: return null
            return Geo.angleAt(a, b, c)
        }
        val l = ang(Side.LEFT); val rr = ang(Side.RIGHT)
        val m = if (l != null && rr != null) abs(l - rr) else null
        return build(r, m, null, "", r.advice)
    }
}

/** 실시간 점수 흔들림을 줄이는 지수 이동 평균 */
class ScoreSmoother(private val alpha: Float = 0.3f) {
    private val state = mutableMapOf<String, Float>()
    fun smooth(key: String, v: Int?): Int? {
        if (v == null) { state.remove(key); return null }
        val prev = state[key]
        val next = if (prev == null) v.toFloat() else prev + (v - prev) * alpha
        state[key] = next
        return next.roundToInt()
    }
    fun reset() = state.clear()
}
