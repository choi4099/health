package com.health.posecoach.pose

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

/** 분석에 사용하는 관절 (ML Kit 33개 랜드마크 중 17개) */
enum class Joint {
    NOSE,
    L_SHOULDER, R_SHOULDER, L_ELBOW, R_ELBOW, L_WRIST, R_WRIST, L_INDEX, R_INDEX,
    L_HIP, R_HIP, L_KNEE, R_KNEE, L_ANKLE, R_ANKLE, L_FOOT, R_FOOT
}

enum class Side(val label: String) { LEFT("왼쪽"), RIGHT("오른쪽") }

/** 좌/우 구분 없는 관절 이름 (규칙 정의용) */
enum class G(val left: Joint, val right: Joint) {
    SHOULDER(Joint.L_SHOULDER, Joint.R_SHOULDER),
    ELBOW(Joint.L_ELBOW, Joint.R_ELBOW),
    WRIST(Joint.L_WRIST, Joint.R_WRIST),
    INDEX(Joint.L_INDEX, Joint.R_INDEX),
    HIP(Joint.L_HIP, Joint.R_HIP),
    KNEE(Joint.L_KNEE, Joint.R_KNEE),
    ANKLE(Joint.L_ANKLE, Joint.R_ANKLE),
    FOOT(Joint.L_FOOT, Joint.R_FOOT);

    fun of(side: Side) = if (side == Side.LEFT) left else right
}

/** 평가 부위 */
enum class BodyPart(val label: String, val weight: Float) {
    HAND("손", 0.10f),
    ARM("팔", 0.30f),
    LEG("다리", 0.30f),
    BODY("몸", 0.30f)
}

/** 촬영 방향 */
enum class CameraView(val label: String, val guide: String) {
    FRONT("정면", "카메라를 정면으로 바라보고 서 주세요"),
    SIDE("옆면", "몸의 옆면이 카메라를 향하게 서 주세요"),
    BACK("뒷면", "카메라에 등을 보이고 서 주세요")
}

/** 점 (x, y, 신뢰도). 신뢰도 = 화면 안에 있을 확률 */
data class P(val x: Float, val y: Float, val conf: Float = 1f)

typealias Skeleton = Map<Joint, P>

/** 그리기용 뼈대 연결선과 소속 부위 */
val BONES: List<Triple<Joint, Joint, BodyPart>> = listOf(
    Triple(Joint.L_SHOULDER, Joint.R_SHOULDER, BodyPart.BODY),
    Triple(Joint.L_HIP, Joint.R_HIP, BodyPart.BODY),
    Triple(Joint.L_SHOULDER, Joint.L_HIP, BodyPart.BODY),
    Triple(Joint.R_SHOULDER, Joint.R_HIP, BodyPart.BODY),
    Triple(Joint.L_SHOULDER, Joint.L_ELBOW, BodyPart.ARM),
    Triple(Joint.L_ELBOW, Joint.L_WRIST, BodyPart.ARM),
    Triple(Joint.R_SHOULDER, Joint.R_ELBOW, BodyPart.ARM),
    Triple(Joint.R_ELBOW, Joint.R_WRIST, BodyPart.ARM),
    Triple(Joint.L_WRIST, Joint.L_INDEX, BodyPart.HAND),
    Triple(Joint.R_WRIST, Joint.R_INDEX, BodyPart.HAND),
    Triple(Joint.L_HIP, Joint.L_KNEE, BodyPart.LEG),
    Triple(Joint.L_KNEE, Joint.L_ANKLE, BodyPart.LEG),
    Triple(Joint.R_HIP, Joint.R_KNEE, BodyPart.LEG),
    Triple(Joint.R_KNEE, Joint.R_ANKLE, BodyPart.LEG),
    Triple(Joint.L_ANKLE, Joint.L_FOOT, BodyPart.LEG),
    Triple(Joint.R_ANKLE, Joint.R_FOOT, BodyPart.LEG)
)

object Geo {
    /** b 에서의 사이각 (0~180°) */
    fun angleAt(a: P, b: P, c: P): Float {
        val x1 = a.x - b.x; val y1 = a.y - b.y
        val x2 = c.x - b.x; val y2 = c.y - b.y
        val dot = x1 * x2 + y1 * y2
        val cross = x1 * y2 - y1 * x2
        return abs(Math.toDegrees(atan2(cross, dot).toDouble())).toFloat()
    }

    /** from→to 방향이 '수직 위'에서 벌어진 각도 (0 = 위, 90 = 수평, 180 = 아래) */
    fun tiltFromUp(from: P, to: P): Float {
        val dx = to.x - from.x; val dy = to.y - from.y
        val len = sqrt(dx * dx + dy * dy)
        if (len < 1e-3f) return 0f
        val cos = (-dy / len).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos.toDouble())).toFloat()
    }

    fun mid(a: P?, b: P?): P? = when {
        a != null && b != null -> P((a.x + b.x) / 2, (a.y + b.y) / 2, minOf(a.conf, b.conf))
        else -> a ?: b
    }
}
