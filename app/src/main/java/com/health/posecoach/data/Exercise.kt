package com.health.posecoach.data

import com.health.posecoach.pose.PoseTemplate
import com.health.posecoach.pose.TemplateId
import com.health.posecoach.pose.Templates

enum class Category(val label: String) { UPPER("상체"), LOWER("하체"), CORE("코어"), FULL("전신") }

enum class Difficulty(val label: String, val stars: Int) {
    BEGINNER("초급", 1), INTERMEDIATE("중급", 2), ADVANCED("고급", 3)
}

data class Exercise(
    val id: Int,
    val name: String,
    val category: Category,
    val subGroup: String,
    val difficulty: Difficulty,
    val primaryMuscles: String,
    val secondaryMuscles: String,
    /** 어떤 근육이 어떻게 발달하는지 설명 */
    val description: String,
    val templateId: TemplateId
) {
    val template: PoseTemplate get() = Templates.get(templateId)
}
