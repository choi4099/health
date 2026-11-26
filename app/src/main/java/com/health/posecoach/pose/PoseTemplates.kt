package com.health.posecoach.pose

import kotlin.math.cos
import kotlin.math.sin

/** 동작 패턴별 기준 자세 ID */
enum class TemplateId {
    SQUAT, FRONT_SQUAT, LUNGE, HINGE, DEADLIFT, SUMO_DEADLIFT, HIP_THRUST, GLUTE_KICKBACK,
    LEG_PRESS, LEG_EXTENSION, LYING_LEG_CURL, SEATED_LEG_CURL, NORDIC, SISSY, STEP_UP,
    CALF_RAISE, SEATED_CALF, SEATED_MACHINE_LEG,
    BENCH_PRESS, MACHINE_PRESS, PUSH_UP, INVERTED_ROW, DIPS, FLY, PULLOVER,
    PULL_VERTICAL, BENT_ROW, SEATED_ROW, STRAIGHT_ARM, BACK_EXTENSION,
    OVERHEAD_PRESS, LATERAL_RAISE, FRONT_RAISE, REAR_DELT, FACE_PULL, UPRIGHT_ROW, SHRUG,
    CURL, WRIST_CURL, TRICEP_PUSHDOWN, OVERHEAD_EXTENSION, SKULL_CRUSHER, KICKBACK_ARM,
    PLANK, HIGH_PLANK, SIDE_PLANK, CRUNCH, CABLE_CRUNCH, LEG_RAISE, HANGING_LEG_RAISE, V_UP,
    RUSSIAN_TWIST, ROLLOUT, DEAD_BUG, BIRD_DOG, PALLOF, HOLLOW,
    OVERHEAD_LOCKOUT, ONE_ARM_LOCKOUT, FRONT_RACK, KB_SWING, FARMER, SLED_PUSH, BATTLE_ROPE,
    BOX_JUMP_LAND, ROWER, BIKE
}

data class PoseTemplate(
    val id: TemplateId,
    val view: CameraView,
    /** 어느 순간의 자세를 기준으로 평가하는지 */
    val keyPosition: String,
    /** 정규화된 기준 자세: 골반 중심 (0,0), 몸통 길이 1, y 는 아래가 + */
    val reference: Skeleton,
    val rules: List<Rule>
)

/**
 * 기준 자세 생성기.
 * 각 마디의 '절대 방향'을 각도로 받아 관절 좌표를 만든다.
 * 0° = 위, 90° = 앞(화면 오른쪽), 180° = 아래, 270° = 뒤(화면 왼쪽)
 */
object Ref {
    private const val TORSO = 1.0f
    private const val UPPER_ARM = 0.55f
    private const val FOREARM = 0.5f
    private const val THIGH = 0.9f
    private const val SHIN = 0.85f

    private fun dir(deg: Float, sign: Float = 1f): Pair<Float, Float> {
        val r = Math.toRadians(deg.toDouble())
        return Pair(sign * sin(r).toFloat(), -cos(r).toFloat())
    }

    private fun P.go(deg: Float, len: Float, sign: Float = 1f): P {
        val (dx, dy) = dir(deg, sign)
        return P(x + dx * len, y + dy * len)
    }

    /** 옆면 기준 자세 (사람이 화면 오른쪽을 바라봄). '2' 값은 반대쪽 팔·다리 */
    fun side(
        torso: Float, ua: Float, fa: Float, th: Float, sh: Float,
        ua2: Float = ua, fa2: Float = fa, th2: Float = th, sh2: Float = sh,
        foot: Float = 90f, foot2: Float = foot,
        hand: Float? = null
    ): Skeleton {
        val hip = P(0f, 0f)
        val sho = hip.go(torso, TORSO)
        val nose = sho.go(torso, 0.32f).go(torso + 90f, 0.12f)
        val m = mutableMapOf<Joint, P>()
        m[Joint.NOSE] = nose
        fun arm(s: Joint, e: Joint, w: Joint, i: Joint, a: Float, f: Float) {
            m[s] = sho
            val el = sho.go(a, UPPER_ARM); m[e] = el
            val wr = el.go(f, FOREARM); m[w] = wr
            m[i] = wr.go(hand ?: f, 0.12f)
        }
        fun leg(h: Joint, k: Joint, an: Joint, fo: Joint, t: Float, s: Float, ft: Float) {
            m[h] = hip
            val kn = hip.go(t, THIGH); m[k] = kn
            val ak = kn.go(s, SHIN); m[an] = ak
            m[fo] = ak.go(ft, 0.25f)
        }
        arm(Joint.L_SHOULDER, Joint.L_ELBOW, Joint.L_WRIST, Joint.L_INDEX, ua, fa)
        arm(Joint.R_SHOULDER, Joint.R_ELBOW, Joint.R_WRIST, Joint.R_INDEX, ua2, fa2)
        leg(Joint.L_HIP, Joint.L_KNEE, Joint.L_ANKLE, Joint.L_FOOT, th, sh, foot)
        leg(Joint.R_HIP, Joint.R_KNEE, Joint.R_ANKLE, Joint.R_FOOT, th2, sh2, foot2)
        return m
    }

    /**
     * 정면/뒷면 기준 자세. 팔·다리 각도는 '바깥쪽 = 90°' 기준 (양쪽 동일 규칙)
     * 사람의 왼쪽이 화면 +x 쪽.
     */
    fun front(
        torso: Float = 0f,
        lua: Float = 180f, lfa: Float = 180f, rua: Float = lua, rfa: Float = lfa,
        lth: Float = 180f, lsh: Float = 180f, rth: Float = lth, rsh: Float = lsh
    ): Skeleton {
        val (px, py) = dir(torso + 90f)
        val hipC = P(0f, 0f)
        val shoC = hipC.go(torso, TORSO)
        val m = mutableMapOf<Joint, P>()
        m[Joint.NOSE] = shoC.go(torso, 0.35f)
        fun limbs(sign: Float, s: Joint, e: Joint, w: Joint, i: Joint, h: Joint, k: Joint, a: Joint, f: Joint,
                 ua: Float, fa: Float, th: Float, sh: Float) {
            val sho = P(shoC.x + sign * px * 0.4f, shoC.y + sign * py * 0.4f); m[s] = sho
            val el = sho.go(ua, UPPER_ARM, sign); m[e] = el
            val wr = el.go(fa, FOREARM, sign); m[w] = wr
            m[i] = wr.go(fa, 0.12f, sign)
            val hp = P(hipC.x + sign * px * 0.22f, hipC.y + sign * py * 0.22f); m[h] = hp
            val kn = hp.go(th, THIGH, sign); m[k] = kn
            val an = kn.go(sh, SHIN, sign); m[a] = an
            m[f] = P(an.x + sign * 0.08f, an.y + 0.06f)
        }
        limbs(1f, Joint.L_SHOULDER, Joint.L_ELBOW, Joint.L_WRIST, Joint.L_INDEX, Joint.L_HIP, Joint.L_KNEE, Joint.L_ANKLE, Joint.L_FOOT, lua, lfa, lth, lsh)
        limbs(-1f, Joint.R_SHOULDER, Joint.R_ELBOW, Joint.R_WRIST, Joint.R_INDEX, Joint.R_HIP, Joint.R_KNEE, Joint.R_ANKLE, Joint.R_FOOT, rua, rfa, rth, rsh)
        return m
    }
}

object Templates {
    private val A = SideMode.AUTO
    private val BOTH = SideMode.BOTH
    private val EACH = SideMode.EACH
    private val BEST = SideMode.BEST

    // ---- 공통 규칙 헬퍼 ----
    private fun knee(t: Float, tol: Float, low: String, high: String, m: SideMode = A) =
        Rule.Angle(BodyPart.LEG, "무릎 각도", G.HIP, G.KNEE, G.ANKLE, t, tol, low, high, m)
    private fun ankle(t: Float, tol: Float, low: String, high: String) =
        Rule.Angle(BodyPart.LEG, "발목 각도", G.KNEE, G.ANKLE, G.FOOT, t, tol, low, high)
    private fun hip(t: Float, tol: Float, low: String, high: String, m: SideMode = A) =
        Rule.Angle(BodyPart.BODY, "고관절 각도", G.SHOULDER, G.HIP, G.KNEE, t, tol, low, high, m)
    private fun line(low: String, t: Float = 177f, tol: Float = 8f, m: SideMode = A) =
        Rule.Angle(BodyPart.BODY, "몸 일직선", G.SHOULDER, G.HIP, G.ANKLE, t, tol, low, "", m)
    private fun torso(t: Float, tol: Float, low: String, high: String) =
        Rule.Tilt(BodyPart.BODY, "상체 기울기", G.HIP, G.SHOULDER, t, tol, low, high)
    private fun elbow(t: Float, tol: Float, low: String, high: String, m: SideMode = A) =
        Rule.Angle(BodyPart.ARM, "팔꿈치 각도", G.SHOULDER, G.ELBOW, G.WRIST, t, tol, low, high, m)
    private fun shoulder(t: Float, tol: Float, low: String, high: String, m: SideMode = A) =
        Rule.Angle(BodyPart.ARM, "어깨 각도", G.HIP, G.SHOULDER, G.ELBOW, t, tol, low, high, m)
    private fun wrist(t: Float = 168f, tol: Float = 15f) =
        Rule.Angle(BodyPart.HAND, "손목 정렬", G.ELBOW, G.WRIST, G.INDEX, t, tol,
            "손목이 꺾였어요. 손등과 전완이 일직선이 되도록 손목을 세우세요.", "")
    private fun forearm(t: Float, tol: Float, low: String, high: String) =
        Rule.Tilt(BodyPart.HAND, "손 위치", G.ELBOW, G.WRIST, t, tol, low, high)
    private fun handUnderShoulder(adv: String, tol: Float = 15f) =
        Rule.Tilt(BodyPart.HAND, "손 위치", G.SHOULDER, G.WRIST, 180f, tol, adv, adv)
    private fun sym(part: BodyPart, label: String, a: G, b: G, c: G, tol: Float, adv: String) =
        Rule.Symmetry(part, label, a, b, c, tol, adv)
    private fun symArm(adv: String = "양팔 높이와 속도를 똑같이 맞추세요.") =
        sym(BodyPart.ARM, "양팔 균형", G.HIP, G.SHOULDER, G.ELBOW, 12f, adv)
    private fun symElbow(adv: String = "양쪽 팔꿈치를 같은 각도로 움직이세요.") =
        sym(BodyPart.ARM, "양팔 균형", G.SHOULDER, G.ELBOW, G.WRIST, 12f, adv)

    private const val TOO_UPRIGHT = "엉덩이를 뒤로 빼면서 상체를 조금 더 숙이세요."
    private const val TOO_LEAN = "상체가 너무 숙여졌어요. 가슴을 펴고 시선을 정면으로 두세요."
    private const val NO_SWING = "반동으로 몸을 흔들지 말고 상체를 고정하세요."
    private const val BRACE = "복부와 엉덩이에 힘을 주어 머리-골반-발목이 일직선이 되게 하세요."

    private val list: List<PoseTemplate> = listOf(
        // ======================= 하체 =======================
        PoseTemplate(TemplateId.SQUAT, CameraView.SIDE, "가장 깊이 앉은 자세",
            Ref.side(torso = 40f, ua = 215f, fa = 350f, th = 100f, sh = 200f),
            listOf(
                knee(85f, 15f, "너무 깊게 앉았어요. 허벅지가 바닥과 평행한 지점에서 멈추세요.", "더 깊이 앉으세요. 허벅지가 바닥과 평행해질 때까지 내려가세요."),
                hip(70f, 15f, TOO_LEAN, "엉덩이를 뒤로 더 빼며 앉으세요."),
                torso(40f, 15f, TOO_UPRIGHT, TOO_LEAN),
                ankle(75f, 20f, "무릎이 발끝보다 과하게 나갔어요. 체중을 발 중앙에 두세요.", "발목이 굳어 있어요. 무릎을 발끝 방향으로 조금 더 보내세요.")
            )),
        PoseTemplate(TemplateId.FRONT_SQUAT, CameraView.SIDE, "가장 깊이 앉은 자세",
            Ref.side(torso = 20f, ua = 90f, fa = 315f, th = 100f, sh = 210f),
            listOf(
                knee(80f, 15f, "너무 깊게 앉았어요.", "더 깊이 앉으세요. 엉덩이가 무릎 높이까지 내려가게 하세요."),
                hip(80f, 15f, TOO_LEAN, "엉덩이를 수직으로 더 내리세요."),
                torso(20f, 12f, "상체가 너무 뒤로 서 있어요. 무게중심을 발 중앙에 두세요.", "상체를 더 세우세요. 바(덤벨)가 앞으로 쏠려요."),
                shoulder(100f, 20f, "팔꿈치를 너무 높이 들었어요. 편하게 가슴 앞에 고정하세요.", "팔꿈치가 처졌어요. 팔꿈치를 앞으로 높이 들어 가슴을 세우세요.")
            )),
        PoseTemplate(TemplateId.LUNGE, CameraView.SIDE, "가장 낮게 내려간 자세",
            Ref.side(torso = 5f, ua = 180f, fa = 180f, th = 90f, sh = 180f, th2 = 200f, sh2 = 290f),
            listOf(
                knee(90f, 15f, "무릎을 너무 많이 굽혔어요. 90° 근처에서 멈추세요.", "더 깊이 내려가 양쪽 무릎이 90°가 되게 하세요.", EACH),
                torso(5f, 10f, "", "상체를 곧게 세우세요. 앞으로 숙이면 허리에 부담이 가요.")
            )),
        PoseTemplate(TemplateId.HINGE, CameraView.SIDE, "가장 깊이 숙인 자세",
            Ref.side(torso = 75f, ua = 180f, fa = 180f, th = 165f, sh = 180f),
            listOf(
                knee(160f, 10f, "무릎을 너무 굽혔어요. 살짝만 굽힌 상태를 유지하세요.", "무릎을 완전히 잠그지 말고 살짝 굽히세요."),
                hip(90f, 15f, "너무 깊이 숙였어요. 햄스트링이 당기는 지점에서 멈추세요.", "엉덩이를 뒤로 더 빼면서 숙이세요."),
                torso(70f, 15f, "상체를 더 숙이세요. 등은 곧게 유지합니다.", "너무 숙였어요. 허리가 말리기 전에 멈추세요."),
                elbow(172f, 8f, "팔을 곧게 펴고 바를 다리에 붙여 내리세요.", ""),
                handUnderShoulder("바(덤벨)가 어깨 바로 아래로 지나가도록 몸에 붙이세요.")
            )),
        PoseTemplate(TemplateId.DEADLIFT, CameraView.SIDE, "바를 들기 직전 시작 자세",
            Ref.side(torso = 60f, ua = 180f, fa = 180f, th = 120f, sh = 200f),
            listOf(
                knee(110f, 15f, "엉덩이가 너무 낮아요. 스쿼트처럼 앉지 마세요.", "무릎을 조금 더 굽혀 정강이를 바에 붙이세요."),
                hip(65f, 15f, "상체가 너무 숙여졌어요. 가슴을 들고 등을 펴세요.", "엉덩이를 조금 더 뒤로 빼세요."),
                torso(55f, 15f, "엉덩이가 너무 낮아 상체가 서 있어요.", "엉덩이가 너무 높아요. 등이 말리지 않게 가슴을 드세요."),
                elbow(172f, 8f, "팔꿈치를 굽히지 말고 팔로 당기지 마세요.", ""),
                handUnderShoulder("바가 어깨 바로 아래, 발 중앙 위에 오도록 맞추세요.")
            )),
        PoseTemplate(TemplateId.SUMO_DEADLIFT, CameraView.SIDE, "바를 들기 직전 시작 자세",
            Ref.side(torso = 40f, ua = 180f, fa = 180f, th = 115f, sh = 190f),
            listOf(
                knee(110f, 15f, "너무 낮게 앉았어요.", "무릎을 바깥으로 밀며 더 굽히세요."),
                hip(80f, 15f, "상체가 숙여졌어요. 가슴을 드세요.", "엉덩이를 조금 더 뒤로 빼세요."),
                torso(40f, 15f, "", "상체를 더 세우세요. 스모는 등이 더 서 있어야 해요."),
                elbow(172f, 8f, "팔을 곧게 펴세요.", ""),
                handUnderShoulder("바가 어깨 바로 아래에 오게 하세요.")
            )),
        PoseTemplate(TemplateId.HIP_THRUST, CameraView.SIDE, "엉덩이를 끝까지 올린 자세",
            Ref.side(torso = 270f, ua = 200f, fa = 90f, th = 90f, sh = 180f),
            listOf(
                hip(175f, 10f, "엉덩이를 끝까지 들어 몸통과 허벅지가 일직선이 되게 하세요.", ""),
                knee(90f, 12f, "발을 몸에서 조금 더 멀리 두세요.", "발을 엉덩이 쪽으로 당기세요. 정강이가 수직이 되게 합니다.")
            )),
        PoseTemplate(TemplateId.GLUTE_KICKBACK, CameraView.SIDE, "다리를 뒤로 끝까지 찬 자세",
            Ref.side(torso = 30f, ua = 120f, fa = 100f, th = 180f, sh = 180f, th2 = 220f, sh2 = 210f),
            listOf(
                hip(165f, 12f, "다리를 뒤로 더 차 올리세요.", "허리가 꺾일 만큼 과하게 차지 마세요.", BEST),
                torso(30f, 15f, "상체를 조금 숙여 고정하세요.", "상체를 너무 숙였어요.")
            )),
        PoseTemplate(TemplateId.LEG_PRESS, CameraView.SIDE, "발판을 가장 깊이 내린 자세",
            Ref.side(torso = 315f, ua = 180f, fa = 120f, th = 45f, sh = 135f),
            listOf(
                knee(90f, 15f, "너무 깊이 내렸어요. 엉덩이가 들리기 전에 멈추세요.", "발판을 더 깊이 내려 무릎을 90°까지 굽히세요."),
                hip(90f, 20f, "엉덩이가 패드에서 떨어질 수 있어요. 가동범위를 줄이세요.", "조금 더 깊이 내리세요.")
            )),
        PoseTemplate(TemplateId.LEG_EXTENSION, CameraView.SIDE, "다리를 끝까지 편 자세",
            Ref.side(torso = 350f, ua = 170f, fa = 150f, th = 90f, sh = 90f),
            listOf(
                knee(172f, 8f, "다리를 끝까지 펴서 허벅지 앞쪽을 수축하세요.", ""),
                hip(95f, 20f, "", "엉덩이를 시트 안쪽까지 붙이세요.")
            )),
        PoseTemplate(TemplateId.LYING_LEG_CURL, CameraView.SIDE, "발꿈치를 끝까지 당긴 자세",
            Ref.side(torso = 270f, ua = 180f, fa = 270f, th = 90f, sh = 330f),
            listOf(
                knee(60f, 15f, "", "발꿈치를 엉덩이 쪽으로 더 당기세요."),
                hip(175f, 10f, "엉덩이가 들렸어요. 골반을 패드에 붙이세요.", "")
            )),
        PoseTemplate(TemplateId.SEATED_LEG_CURL, CameraView.SIDE, "다리를 끝까지 굽힌 자세",
            Ref.side(torso = 0f, ua = 180f, fa = 150f, th = 90f, sh = 210f),
            listOf(
                knee(70f, 15f, "", "다리를 더 깊이 굽혀 햄스트링을 수축하세요."),
                torso(0f, 12f, "", "등을 등받이에 붙이고 상체를 세우세요.")
            )),
        PoseTemplate(TemplateId.NORDIC, CameraView.SIDE, "몸을 앞으로 기울인 자세",
            Ref.side(torso = 30f, ua = 150f, fa = 150f, th = 210f, sh = 270f),
            listOf(
                hip(172f, 10f, "엉덩이를 접지 말고 무릎부터 어깨까지 일직선을 유지하세요.", ""),
                knee(120f, 25f, "너무 깊어요. 버틸 수 있는 범위까지만 내려가세요.", "천천히 더 내려가며 햄스트링으로 버티세요.")
            )),
        PoseTemplate(TemplateId.SISSY, CameraView.SIDE, "가장 낮게 내려간 자세",
            Ref.side(torso = 330f, ua = 90f, fa = 90f, th = 150f, sh = 260f),
            listOf(
                knee(75f, 15f, "너무 깊어요. 무릎 부담을 줄이세요.", "무릎을 앞으로 더 보내며 내려가세요."),
                hip(170f, 12f, "엉덩이를 빼지 말고 무릎-골반-어깨를 일직선으로 유지하세요.", "")
            )),
        PoseTemplate(TemplateId.STEP_UP, CameraView.SIDE, "박스 위에 완전히 선 자세",
            Ref.side(torso = 5f, ua = 180f, fa = 180f, th = 180f, sh = 180f, th2 = 110f, sh2 = 180f),
            listOf(
                knee(172f, 8f, "디딘 다리로 끝까지 일어서세요.", "", BEST),
                torso(5f, 10f, "", "상체를 숙이지 말고 다리 힘으로 올라가세요.")
            )),
        PoseTemplate(TemplateId.CALF_RAISE, CameraView.SIDE, "발뒤꿈치를 가장 높이 든 자세",
            Ref.side(torso = 0f, ua = 180f, fa = 180f, th = 180f, sh = 180f, foot = 140f),
            listOf(
                ankle(140f, 12f, "발뒤꿈치를 최대한 높이 들어 올리세요.", ""),
                knee(172f, 8f, "무릎을 펴고 종아리로만 올리세요.", ""),
                torso(0f, 8f, "", "몸을 앞으로 기울이지 말고 수직으로 올라가세요.")
            )),
        PoseTemplate(TemplateId.SEATED_CALF, CameraView.SIDE, "발뒤꿈치를 가장 높이 든 자세",
            Ref.side(torso = 0f, ua = 160f, fa = 120f, th = 90f, sh = 180f, foot = 140f),
            listOf(
                ankle(140f, 12f, "발뒤꿈치를 끝까지 들어 올리세요.", ""),
                knee(90f, 15f, "무릎 각도를 90°로 맞춰 앉으세요.", "무릎 각도를 90°로 맞춰 앉으세요.")
            )),
        PoseTemplate(TemplateId.SEATED_MACHINE_LEG, CameraView.FRONT, "다리를 끝까지 벌리거나 모은 자세",
            Ref.front(lua = 175f, lfa = 160f, lth = 160f, lsh = 180f),
            listOf(
                torso(0f, 10f, "", "등을 등받이에 붙이고 상체가 한쪽으로 기울지 않게 하세요."),
                sym(BodyPart.LEG, "양다리 균형", G.HIP, G.KNEE, G.ANKLE, 15f, "양다리를 같은 속도와 범위로 움직이세요.")
            )),

        // ======================= 가슴 / 등 =======================
        PoseTemplate(TemplateId.BENCH_PRESS, CameraView.SIDE, "팔을 끝까지 편 자세 (락아웃)",
            Ref.side(torso = 270f, ua = 0f, fa = 0f, th = 90f, sh = 180f),
            listOf(
                elbow(170f, 10f, "팔을 끝까지 펴서 마무리하세요.", ""),
                shoulder(88f, 15f, "바가 배 쪽으로 내려가 있어요. 어깨 바로 위로 미세요.", "바가 얼굴 쪽으로 넘어갔어요. 어깨 바로 위에서 멈추세요."),
                forearm(0f, 12f, "", "손목이 팔꿈치 바로 위에 오도록 그립 위치를 맞추세요."),
                wrist()
            )),
        PoseTemplate(TemplateId.MACHINE_PRESS, CameraView.SIDE, "팔을 끝까지 민 자세",
            Ref.side(torso = 0f, ua = 90f, fa = 90f, th = 90f, sh = 180f),
            listOf(
                elbow(170f, 10f, "팔을 끝까지 미세요(팔꿈치는 잠그지 않을 정도).", ""),
                shoulder(85f, 15f, "시트 높이를 낮춰 손잡이가 가슴 높이에 오게 하세요.", "시트 높이를 높여 손잡이가 가슴 높이에 오게 하세요."),
                torso(0f, 10f, "", "등을 등받이에 붙이세요."),
                wrist()
            )),
        PoseTemplate(TemplateId.PUSH_UP, CameraView.SIDE, "가장 낮게 내려간 자세",
            Ref.side(torso = 270f, ua = 120f, fa = 205f, th = 90f, sh = 90f),
            listOf(
                elbow(90f, 15f, "너무 깊어요. 가슴이 바닥 바로 위에서 멈추세요.", "가슴이 바닥 가까이 오도록 더 내려가세요."),
                line(BRACE),
                shoulder(40f, 15f, "팔꿈치를 너무 붙였어요. 몸과 45° 정도로 벌리세요.", "팔꿈치가 너무 벌어졌어요. 몸과 45° 정도로 붙이세요."),
                handUnderShoulder("손을 어깨 바로 아래(살짝 바깥)에 두세요.", 25f)
            )),
        PoseTemplate(TemplateId.INVERTED_ROW, CameraView.SIDE, "가슴을 바에 붙인 자세",
            Ref.side(torso = 270f, ua = 120f, fa = 20f, th = 90f, sh = 90f),
            listOf(
                line(BRACE),
                elbow(80f, 20f, "", "가슴이 바에 닿을 때까지 더 당기세요."),
                wrist()
            )),
        PoseTemplate(TemplateId.DIPS, CameraView.SIDE, "가장 낮게 내려간 자세",
            Ref.side(torso = 20f, ua = 210f, fa = 120f, th = 175f, sh = 200f),
            listOf(
                elbow(90f, 15f, "너무 깊어요. 어깨 부담을 줄이려면 90°에서 멈추세요.", "팔꿈치가 90°가 될 때까지 더 내려가세요."),
                torso(20f, 12f, "상체를 살짝 앞으로 기울이면 가슴 자극이 커져요.", "상체를 너무 숙였어요."),
                wrist()
            )),
        PoseTemplate(TemplateId.FLY, CameraView.FRONT, "팔을 가장 넓게 벌린 자세",
            Ref.front(lua = 105f, lfa = 85f),
            listOf(
                shoulder(85f, 15f, "팔을 어깨 높이까지 벌리세요.", "팔이 어깨보다 높아요. 가슴 높이로 낮추세요.", BOTH),
                elbow(160f, 12f, "팔꿈치를 너무 굽혔어요. 살짝만 굽히세요.", "팔꿈치를 완전히 펴지 말고 살짝 굽혀 관절을 보호하세요.", BOTH),
                symArm(),
                torso(0f, 10f, "", "몸이 한쪽으로 기울었어요.")
            )),
        PoseTemplate(TemplateId.PULLOVER, CameraView.SIDE, "팔을 머리 뒤로 가장 멀리 넘긴 자세",
            Ref.side(torso = 270f, ua = 300f, fa = 290f, th = 90f, sh = 180f),
            listOf(
                shoulder(150f, 15f, "팔을 머리 뒤로 더 넘겨 늘려 주세요.", "너무 넘겼어요. 허리가 뜨지 않는 범위에서 멈추세요."),
                elbow(160f, 12f, "팔꿈치를 너무 굽혔어요.", "팔꿈치를 살짝 굽힌 상태로 고정하세요.")
            )),
        PoseTemplate(TemplateId.PULL_VERTICAL, CameraView.BACK, "끝까지 당긴 자세",
            Ref.front(lua = 140f, lfa = 15f),
            listOf(
                elbow(65f, 20f, "", "더 당기세요. 바가 턱(쇄골) 높이까지 오게 합니다.", BOTH),
                shoulder(50f, 20f, "", "팔꿈치를 옆구리 쪽으로 끌어내리세요.", BOTH),
                symElbow(),
                forearm(0f, 15f, "", "손이 팔꿈치 바로 위에 오도록 그립 너비를 조정하세요."),
                torso(0f, 12f, "", NO_SWING)
            )),
        PoseTemplate(TemplateId.BENT_ROW, CameraView.SIDE, "끝까지 당긴 자세",
            Ref.side(torso = 50f, ua = 250f, fa = 180f, th = 165f, sh = 190f),
            listOf(
                torso(50f, 15f, "상체를 조금 더 숙이세요(45° 내외).", "상체를 조금 세우세요. 허리 부담이 커요."),
                knee(155f, 15f, "무릎을 너무 굽혔어요.", "무릎을 살짝 굽혀 허리를 보호하세요."),
                elbow(100f, 20f, "", "팔꿈치를 몸 뒤로 끝까지 당기세요."),
                forearm(180f, 20f, "전완이 바닥과 수직이 되게 당기세요.", "전완이 바닥과 수직이 되게 당기세요.")
            )),
        PoseTemplate(TemplateId.SEATED_ROW, CameraView.SIDE, "끝까지 당긴 자세",
            Ref.side(torso = 0f, ua = 195f, fa = 90f, th = 80f, sh = 100f),
            listOf(
                torso(5f, 12f, "", NO_SWING),
                elbow(80f, 20f, "", "손잡이를 배꼽 쪽으로 더 당기고 견갑을 모으세요."),
                knee(160f, 15f, "무릎을 너무 굽혔어요.", "무릎을 살짝 굽히세요."),
                wrist()
            )),
        PoseTemplate(TemplateId.STRAIGHT_ARM, CameraView.SIDE, "바를 허벅지까지 내린 자세",
            Ref.side(torso = 20f, ua = 175f, fa = 160f, th = 180f, sh = 180f),
            listOf(
                elbow(165f, 12f, "팔꿈치를 굽히지 말고 각도를 고정하세요.", ""),
                shoulder(25f, 15f, "", "바를 허벅지까지 끝까지 끌어내리세요."),
                torso(20f, 12f, "상체를 살짝 숙이세요.", "상체를 너무 숙였어요.")
            )),
        PoseTemplate(TemplateId.BACK_EXTENSION, CameraView.SIDE, "상체를 끝까지 올린 자세",
            Ref.side(torso = 45f, ua = 180f, fa = 60f, th = 225f, sh = 225f),
            listOf(
                hip(175f, 10f, "상체를 다리와 일직선이 될 때까지 올리세요.", "")
            )),

        // ======================= 어깨 =======================
        PoseTemplate(TemplateId.OVERHEAD_PRESS, CameraView.FRONT, "머리 위로 끝까지 민 자세",
            Ref.front(lua = 10f, lfa = 5f),
            listOf(
                elbow(170f, 10f, "팔을 머리 위로 끝까지 펴세요.", "", BOTH),
                shoulder(168f, 12f, "팔이 앞으로 기울었어요. 머리 바로 위로 미세요.", "", BOTH),
                symElbow(),
                forearm(0f, 12f, "", "손목이 팔꿈치 바로 위에 오게 하세요."),
                torso(0f, 8f, "", "몸이 한쪽으로 기울었어요. 복부에 힘을 주세요."),
                wrist()
            )),
        PoseTemplate(TemplateId.LATERAL_RAISE, CameraView.FRONT, "팔을 가장 높이 든 자세",
            Ref.front(lua = 102f, lfa = 92f),
            listOf(
                shoulder(88f, 12f, "팔을 어깨 높이까지 올리세요.", "어깨보다 높이 들면 승모근이 개입해요. 어깨 높이에서 멈추세요.", BOTH),
                elbow(165f, 12f, "팔꿈치를 너무 굽혔어요.", "", BOTH),
                symArm(),
                torso(0f, 8f, "", NO_SWING)
            )),
        PoseTemplate(TemplateId.FRONT_RAISE, CameraView.SIDE, "팔을 가장 높이 든 자세",
            Ref.side(torso = 0f, ua = 90f, fa = 90f, th = 180f, sh = 180f),
            listOf(
                shoulder(90f, 12f, "팔을 어깨 높이까지 올리세요.", "어깨 높이에서 멈추세요."),
                elbow(170f, 10f, "팔을 곧게 펴세요.", ""),
                torso(0f, 8f, "", "반동으로 몸을 뒤로 젖히지 마세요.")
            )),
        PoseTemplate(TemplateId.REAR_DELT, CameraView.BACK, "팔을 가장 넓게 벌린 자세",
            Ref.front(lua = 95f, lfa = 90f),
            listOf(
                shoulder(85f, 15f, "팔을 옆으로 더 벌려 어깨 높이까지 올리세요.", "너무 높이 들었어요.", BOTH),
                elbow(160f, 15f, "팔꿈치를 너무 굽혔어요.", "", BOTH),
                symArm()
            )),
        PoseTemplate(TemplateId.FACE_PULL, CameraView.FRONT, "끝까지 당긴 자세",
            Ref.front(lua = 90f, lfa = 10f),
            listOf(
                shoulder(90f, 15f, "팔꿈치를 어깨 높이까지 올리세요.", "팔꿈치가 너무 높아요.", BOTH),
                elbow(90f, 20f, "", "로프를 얼굴 쪽으로 더 당기세요.", BOTH),
                forearm(0f, 20f, "", "손이 팔꿈치보다 위에 오도록 어깨를 바깥으로 돌리세요."),
                symArm()
            )),
        PoseTemplate(TemplateId.UPRIGHT_ROW, CameraView.FRONT, "바를 가장 높이 당긴 자세",
            Ref.front(lua = 110f, lfa = 230f),
            listOf(
                shoulder(80f, 12f, "팔꿈치를 어깨 높이 가까이 올리세요.", "어깨보다 높이 올리면 어깨 충돌 위험이 있어요.", BOTH),
                elbow(60f, 20f, "", "바를 가슴 위쪽까지 더 당기세요.", BOTH),
                symArm()
            )),
        PoseTemplate(TemplateId.SHRUG, CameraView.FRONT, "어깨를 가장 높이 올린 자세",
            Ref.front(),
            listOf(
                elbow(172f, 8f, "팔은 편 채로 어깨만 으쓱 올리세요.", "", BOTH),
                torso(0f, 6f, "", "몸이 한쪽으로 기울었어요."),
                symArm("양쪽 어깨를 같은 높이로 올리세요.")
            )),

        // ======================= 팔 =======================
        PoseTemplate(TemplateId.CURL, CameraView.SIDE, "끝까지 말아 올린 자세",
            Ref.side(torso = 0f, ua = 175f, fa = 30f, th = 180f, sh = 180f),
            listOf(
                elbow(45f, 15f, "", "끝까지 말아 올려 이두근을 수축하세요."),
                shoulder(15f, 12f, "", "팔꿈치가 앞으로 나왔어요. 옆구리에 고정하세요."),
                torso(0f, 8f, "", NO_SWING),
                wrist()
            )),
        PoseTemplate(TemplateId.WRIST_CURL, CameraView.SIDE, "손목을 끝까지 말아 올린 자세",
            Ref.side(torso = 10f, ua = 160f, fa = 90f, th = 90f, sh = 180f, hand = 50f),
            listOf(
                Rule.Angle(BodyPart.HAND, "손목 굽힘", G.ELBOW, G.WRIST, G.INDEX, 140f, 25f,
                    "손목을 너무 꺾었어요.", "손목을 끝까지 말아 올리세요."),
                elbow(100f, 20f, "전완을 허벅지 위에 편하게 올리세요.", "전완을 허벅지 위에 편하게 올리세요.")
            )),
        PoseTemplate(TemplateId.TRICEP_PUSHDOWN, CameraView.SIDE, "팔을 끝까지 편 자세",
            Ref.side(torso = 10f, ua = 175f, fa = 180f, th = 180f, sh = 180f),
            listOf(
                elbow(170f, 10f, "팔을 끝까지 펴서 삼두를 수축하세요.", ""),
                shoulder(15f, 12f, "", "팔꿈치를 옆구리에 고정하세요."),
                torso(10f, 12f, "", "상체를 너무 숙이면 체중으로 누르게 돼요."),
                wrist()
            )),
        PoseTemplate(TemplateId.OVERHEAD_EXTENSION, CameraView.SIDE, "팔을 머리 위로 편 자세",
            Ref.side(torso = 0f, ua = 10f, fa = 5f, th = 180f, sh = 180f),
            listOf(
                elbow(170f, 10f, "팔을 머리 위로 끝까지 펴세요.", ""),
                shoulder(165f, 15f, "팔꿈치가 앞으로 벌어졌어요. 머리 옆에 고정하세요.", ""),
                torso(0f, 10f, "", "허리가 젖혀졌어요. 복부에 힘을 주세요.")
            )),
        PoseTemplate(TemplateId.SKULL_CRUSHER, CameraView.SIDE, "가장 낮게 내린 자세",
            Ref.side(torso = 270f, ua = 340f, fa = 250f, th = 90f, sh = 180f),
            listOf(
                elbow(80f, 20f, "너무 깊어요.", "이마 근처까지 더 내리세요."),
                shoulder(105f, 15f, "상완을 수직보다 살짝 머리 쪽으로 기울여 고정하세요.", "팔꿈치가 머리 쪽으로 너무 넘어갔어요.")
            )),
        PoseTemplate(TemplateId.KICKBACK_ARM, CameraView.SIDE, "팔을 뒤로 끝까지 편 자세",
            Ref.side(torso = 70f, ua = 250f, fa = 250f, th = 150f, sh = 190f),
            listOf(
                elbow(170f, 10f, "팔을 뒤로 끝까지 펴세요.", ""),
                shoulder(10f, 15f, "", "상완을 몸통과 평행하게 고정하세요."),
                torso(70f, 15f, "상체를 더 숙여 바닥과 거의 평행하게 하세요.", "")
            )),

        // ======================= 코어 =======================
        PoseTemplate(TemplateId.PLANK, CameraView.SIDE, "버티는 자세",
            Ref.side(torso = 270f, ua = 180f, fa = 270f, th = 90f, sh = 90f),
            listOf(
                line(BRACE),
                hip(175f, 8f, "엉덩이가 들렸어요. 골반을 내려 일직선을 만드세요.", ""),
                elbow(90f, 12f, "팔꿈치를 어깨 바로 아래에 두세요.", "팔꿈치를 어깨 바로 아래에 두세요."),
                shoulder(90f, 12f, "어깨가 팔꿈치보다 뒤에 있어요.", "어깨가 팔꿈치보다 앞으로 나갔어요.")
            )),
        PoseTemplate(TemplateId.HIGH_PLANK, CameraView.SIDE, "팔을 편 플랭크 자세",
            Ref.side(torso = 270f, ua = 180f, fa = 180f, th = 90f, sh = 90f, th2 = 130f, sh2 = 220f),
            listOf(
                line(BRACE, m = BEST, tol = 10f),
                elbow(170f, 10f, "팔을 곧게 펴세요.", ""),
                shoulder(85f, 15f, "어깨를 손목 바로 위로 옮기세요.", "어깨를 손목 바로 위로 옮기세요.")
            )),
        PoseTemplate(TemplateId.SIDE_PLANK, CameraView.FRONT, "옆으로 버티는 자세",
            Ref.front(torso = 290f, lua = 180f, lfa = 200f, rua = 160f, rfa = 160f, lth = 110f, lsh = 110f, rth = 250f, rsh = 250f),
            listOf(
                line("골반이 처졌어요. 엉덩이를 들어 머리부터 발까지 일직선을 만드세요.", m = BEST),
                hip(175f, 8f, "골반을 앞으로 밀어 몸이 접히지 않게 하세요.", "", BEST)
            )),
        PoseTemplate(TemplateId.CRUNCH, CameraView.SIDE, "상체를 가장 높이 말아 올린 자세",
            Ref.side(torso = 300f, ua = 330f, fa = 200f, th = 45f, sh = 135f),
            listOf(
                hip(110f, 15f, "상체를 너무 올렸어요. 복근 긴장이 풀려요.", "어깨를 바닥에서 더 말아 올리세요."),
                knee(90f, 15f, "무릎을 90°로 세우세요.", "무릎을 90°로 세우세요.")
            )),
        PoseTemplate(TemplateId.CABLE_CRUNCH, CameraView.SIDE, "몸을 가장 깊이 말아 내린 자세",
            Ref.side(torso = 120f, ua = 140f, fa = 20f, th = 180f, sh = 270f),
            listOf(
                hip(65f, 15f, "너무 깊어요.", "골반은 고정하고 상체를 더 둥글게 말아 내리세요."),
                knee(90f, 15f, "무릎을 90°로 꿇고 엉덩이를 고정하세요.", "무릎을 90°로 꿇고 엉덩이를 고정하세요.")
            )),
        PoseTemplate(TemplateId.LEG_RAISE, CameraView.SIDE, "다리를 가장 높이 든 자세",
            Ref.side(torso = 270f, ua = 90f, fa = 90f, th = 0f, sh = 0f),
            listOf(
                hip(90f, 15f, "너무 넘겼어요. 수직에서 멈추세요.", "다리를 수직까지 더 들어 올리세요."),
                knee(170f, 10f, "무릎을 펴세요.", "")
            )),
        PoseTemplate(TemplateId.HANGING_LEG_RAISE, CameraView.SIDE, "다리를 가장 높이 든 자세",
            Ref.side(torso = 0f, ua = 0f, fa = 0f, th = 90f, sh = 90f),
            listOf(
                hip(90f, 15f, "", "다리를 수평 이상으로 들어 올리세요."),
                knee(165f, 15f, "무릎을 펴세요(어려우면 굽혀도 됩니다).", ""),
                elbow(170f, 10f, "팔을 곧게 펴고 매달리세요.", ""),
                torso(0f, 12f, "", "몸이 흔들려요. 반동 없이 천천히 올리세요.")
            )),
        PoseTemplate(TemplateId.V_UP, CameraView.SIDE, "몸을 V자로 접은 자세",
            Ref.side(torso = 330f, ua = 30f, fa = 30f, th = 30f, sh = 30f),
            listOf(
                hip(65f, 15f, "", "상체와 다리를 동시에 더 접어 V자를 만드세요."),
                knee(170f, 10f, "무릎을 펴세요.", ""),
                elbow(165f, 15f, "팔을 펴서 발끝으로 뻗으세요.", "")
            )),
        PoseTemplate(TemplateId.RUSSIAN_TWIST, CameraView.SIDE, "몸을 뒤로 기울인 자세",
            Ref.side(torso = 320f, ua = 120f, fa = 60f, th = 60f, sh = 150f),
            listOf(
                torso(40f, 12f, "상체를 조금 더 뒤로 기울이세요.", "너무 기울였어요. 허리가 말리지 않게 하세요."),
                hip(100f, 15f, "", ""),
                knee(90f, 15f, "무릎을 90°로 굽히세요.", "무릎을 90°로 굽히세요.")
            )),
        PoseTemplate(TemplateId.ROLLOUT, CameraView.SIDE, "가장 멀리 굴려 나간 자세",
            Ref.side(torso = 60f, ua = 85f, fa = 85f, th = 225f, sh = 270f),
            listOf(
                hip(165f, 12f, "엉덩이가 접혔어요. 골반을 앞으로 밀어 일직선을 만드세요.", ""),
                shoulder(155f, 15f, "팔을 머리 앞으로 더 뻗으세요.", "너무 멀리 나갔어요. 허리가 처지기 전에 멈추세요."),
                elbow(170f, 10f, "팔을 곧게 펴세요.", "")
            )),
        PoseTemplate(TemplateId.DEAD_BUG, CameraView.SIDE, "테이블탑 자세 (팔·다리 90°)",
            Ref.side(torso = 270f, ua = 0f, fa = 0f, th = 0f, sh = 90f, ua2 = 290f, fa2 = 290f, th2 = 70f, sh2 = 75f),
            listOf(
                hip(90f, 15f, "다리를 몸 쪽으로 너무 당겼어요.", "허벅지를 수직으로 세우세요.", BEST),
                knee(90f, 15f, "무릎을 90°로 굽히세요.", "무릎을 90°로 굽히세요.", BEST),
                shoulder(90f, 15f, "팔을 천장으로 수직으로 뻗으세요.", "팔을 천장으로 수직으로 뻗으세요.", BEST)
            )),
        PoseTemplate(TemplateId.BIRD_DOG, CameraView.SIDE, "팔과 다리를 뻗은 자세",
            Ref.side(torso = 270f, ua = 180f, fa = 180f, th = 180f, sh = 90f, ua2 = 270f, fa2 = 270f, th2 = 90f, sh2 = 90f),
            listOf(
                hip(175f, 10f, "다리를 몸통과 일직선이 되게 뒤로 뻗으세요.", "", BEST),
                shoulder(170f, 12f, "팔을 머리 앞으로 일직선이 되게 뻗으세요.", "", BEST),
                torso(90f, 10f, "등이 수평이 되도록 하세요.", "등이 수평이 되도록 하세요.")
            )),
        PoseTemplate(TemplateId.PALLOF, CameraView.FRONT, "팔을 앞으로 민 자세",
            Ref.front(lua = 190f, lfa = 250f, lth = 175f, lsh = 185f),
            listOf(
                torso(0f, 6f, "", "케이블에 끌려 몸이 기울었어요. 코어로 버티세요."),
                knee(165f, 12f, "무릎을 살짝만 굽히세요.", "", BOTH),
                sym(BodyPart.LEG, "양다리 균형", G.HIP, G.KNEE, G.ANKLE, 12f, "체중을 양발에 고르게 실으세요.")
            )),
        PoseTemplate(TemplateId.HOLLOW, CameraView.SIDE, "버티는 자세",
            Ref.side(torso = 285f, ua = 285f, fa = 285f, th = 75f, sh = 75f),
            listOf(
                hip(150f, 12f, "다리를 너무 들었어요. 더 낮추세요.", "다리와 어깨를 바닥에서 더 띄우세요."),
                knee(170f, 10f, "무릎을 펴세요.", ""),
                shoulder(165f, 15f, "팔을 귀 옆으로 뻗으세요.", "")
            )),

        // ======================= 전신 =======================
        PoseTemplate(TemplateId.OVERHEAD_LOCKOUT, CameraView.FRONT, "머리 위로 완전히 고정한 자세",
            Ref.front(lua = 25f, lfa = 25f),
            listOf(
                elbow(172f, 8f, "팔꿈치를 완전히 잠그세요.", "", BOTH),
                shoulder(160f, 15f, "바를 머리 바로 위로 더 밀어 올리세요.", "", BOTH),
                knee(172f, 10f, "완전히 일어서서 마무리하세요.", "", BOTH),
                symElbow(),
                torso(0f, 8f, "", "몸이 한쪽으로 기울었어요.")
            )),
        PoseTemplate(TemplateId.ONE_ARM_LOCKOUT, CameraView.FRONT, "한 팔을 머리 위로 고정한 자세",
            Ref.front(lua = 10f, lfa = 5f, rua = 180f, rfa = 180f),
            listOf(
                elbow(172f, 8f, "팔꿈치를 완전히 잠그세요.", "", BEST),
                shoulder(165f, 15f, "팔을 머리 바로 위로 수직으로 세우세요.", "", BEST),
                knee(170f, 12f, "완전히 일어서세요.", "", BOTH),
                torso(0f, 10f, "", "몸이 한쪽으로 기울었어요.")
            )),
        PoseTemplate(TemplateId.FRONT_RACK, CameraView.SIDE, "바를 받는 캐치 자세",
            Ref.side(torso = 15f, ua = 90f, fa = 330f, th = 130f, sh = 200f),
            listOf(
                knee(120f, 20f, "너무 깊이 받았어요.", "무릎을 굽혀 충격을 흡수하며 받으세요."),
                torso(15f, 12f, "", "상체를 세우세요. 바가 앞으로 쏠려요."),
                shoulder(100f, 20f, "", "팔꿈치를 빠르게 앞으로 높이 들어 바를 받치세요.")
            )),
        PoseTemplate(TemplateId.KB_SWING, CameraView.SIDE, "케틀벨이 가슴 높이에 온 자세",
            Ref.side(torso = 0f, ua = 90f, fa = 90f, th = 180f, sh = 180f),
            listOf(
                hip(175f, 8f, "엉덩이를 끝까지 앞으로 밀어 몸을 일직선으로 세우세요.", ""),
                knee(172f, 8f, "무릎을 펴서 마무리하세요.", ""),
                shoulder(90f, 15f, "힙의 힘으로 가슴 높이까지 보내세요.", "팔로 들어 올리지 말고 가슴 높이에서 멈추세요."),
                elbow(170f, 10f, "팔은 힘을 빼고 곧게 두세요.", "")
            )),
        PoseTemplate(TemplateId.FARMER, CameraView.FRONT, "걷는 자세",
            Ref.front(),
            listOf(
                torso(0f, 5f, "", "한쪽으로 기울지 않게 몸통을 세우세요."),
                elbow(172f, 8f, "팔을 곧게 펴고 들고 가세요.", "", BOTH),
                symArm("양쪽 어깨 높이를 같게 유지하세요."),
                knee(170f, 12f, "무릎을 너무 굽히지 마세요.", "", BOTH)
            )),
        PoseTemplate(TemplateId.SLED_PUSH, CameraView.SIDE, "미는 자세",
            Ref.side(torso = 50f, ua = 80f, fa = 70f, th = 130f, sh = 200f, th2 = 200f, sh2 = 210f),
            listOf(
                torso(50f, 15f, "몸을 45° 정도로 더 기울여 미세요.", "너무 숙였어요."),
                elbow(150f, 20f, "팔을 조금 더 펴세요.", "")
            )),
        PoseTemplate(TemplateId.BATTLE_ROPE, CameraView.SIDE, "하프 스쿼트 자세",
            Ref.side(torso = 20f, ua = 135f, fa = 110f, th = 130f, sh = 200f),
            listOf(
                knee(120f, 20f, "너무 낮게 앉았어요.", "무릎을 살짝 굽혀 하프 스쿼트 자세를 만드세요."),
                torso(20f, 12f, "상체를 살짝 숙이세요.", "상체를 너무 숙였어요."),
                elbow(140f, 25f, "", "")
            )),
        PoseTemplate(TemplateId.BOX_JUMP_LAND, CameraView.SIDE, "박스 위 착지 자세",
            Ref.side(torso = 35f, ua = 130f, fa = 110f, th = 125f, sh = 200f),
            listOf(
                knee(110f, 20f, "너무 깊이 앉았어요.", "착지할 때 무릎을 부드럽게 굽혀 충격을 흡수하세요."),
                hip(95f, 20f, TOO_LEAN, "엉덩이를 뒤로 빼며 착지하세요."),
                torso(35f, 15f, "", TOO_LEAN)
            )),
        PoseTemplate(TemplateId.ROWER, CameraView.SIDE, "끝까지 당긴 피니시 자세",
            Ref.side(torso = 345f, ua = 200f, fa = 90f, th = 90f, sh = 90f),
            listOf(
                knee(170f, 10f, "다리를 끝까지 미세요.", ""),
                torso(15f, 10f, "", "상체를 너무 많이 젖혔어요."),
                elbow(75f, 20f, "", "손잡이를 명치 아래까지 당기세요.")
            )),
        PoseTemplate(TemplateId.BIKE, CameraView.SIDE, "페달을 밟는 자세",
            Ref.side(torso = 30f, ua = 110f, fa = 90f, th = 120f, sh = 180f),
            listOf(
                torso(30f, 15f, "", "상체를 너무 숙였어요."),
                elbow(155f, 20f, "", "")
            ))
    )

    private val map = list.associateBy { it.id }

    fun get(id: TemplateId): PoseTemplate = map[id] ?: error("템플릿 없음: $id")
    val all: List<PoseTemplate> get() = list
}
