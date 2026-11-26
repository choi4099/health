package com.health.posecoach.pose

import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.accurate.AccuratePoseDetectorOptions

/** 한 프레임의 인식 결과. 좌표는 회전 보정된 카메라 이미지 기준 */
data class PoseFrame(val skeleton: Skeleton, val imageWidth: Int, val imageHeight: Int)

class PoseAnalyzer(private val onFrame: (PoseFrame) -> Unit) : ImageAnalysis.Analyzer {

    private val detector = PoseDetection.getClient(
        AccuratePoseDetectorOptions.Builder()
            .setDetectorMode(AccuratePoseDetectorOptions.STREAM_MODE)
            .build()
    )

    @androidx.annotation.OptIn(ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val media = image.image
        if (media == null) { image.close(); return }
        val rotation = image.imageInfo.rotationDegrees
        val swap = rotation == 90 || rotation == 270
        val w = if (swap) image.height else image.width
        val h = if (swap) image.width else image.height
        detector.process(InputImage.fromMediaImage(media, rotation))
            .addOnSuccessListener { pose -> onFrame(PoseFrame(convert(pose), w, h)) }
            .addOnCompleteListener { image.close() }
    }

    fun close() = detector.close()

    companion object {
        private val TYPES = mapOf(
            Joint.NOSE to PoseLandmark.NOSE,
            Joint.L_SHOULDER to PoseLandmark.LEFT_SHOULDER, Joint.R_SHOULDER to PoseLandmark.RIGHT_SHOULDER,
            Joint.L_ELBOW to PoseLandmark.LEFT_ELBOW, Joint.R_ELBOW to PoseLandmark.RIGHT_ELBOW,
            Joint.L_WRIST to PoseLandmark.LEFT_WRIST, Joint.R_WRIST to PoseLandmark.RIGHT_WRIST,
            Joint.L_INDEX to PoseLandmark.LEFT_INDEX, Joint.R_INDEX to PoseLandmark.RIGHT_INDEX,
            Joint.L_HIP to PoseLandmark.LEFT_HIP, Joint.R_HIP to PoseLandmark.RIGHT_HIP,
            Joint.L_KNEE to PoseLandmark.LEFT_KNEE, Joint.R_KNEE to PoseLandmark.RIGHT_KNEE,
            Joint.L_ANKLE to PoseLandmark.LEFT_ANKLE, Joint.R_ANKLE to PoseLandmark.RIGHT_ANKLE,
            Joint.L_FOOT to PoseLandmark.LEFT_FOOT_INDEX, Joint.R_FOOT to PoseLandmark.RIGHT_FOOT_INDEX
        )

        fun convert(pose: Pose): Skeleton {
            val out = mutableMapOf<Joint, P>()
            for ((j, type) in TYPES) {
                val lm = pose.getPoseLandmark(type) ?: continue
                out[j] = P(lm.position.x, lm.position.y, lm.inFrameLikelihood)
            }
            return out
        }
    }
}
