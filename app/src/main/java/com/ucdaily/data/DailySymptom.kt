package com.ucdaily.data

import androidx.annotation.StringRes
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 排便/症状记录，同一天可新增多条（每次记录一条，按 id 倒序展示）。
 *
 * 字段取值说明：
 * - bristolType：布里斯托大便分类 1~7，0 表示未记录
 * - blood：便血 0=无 1=少量 2=明显 3=血块
 * - painScore：腹痛 0~10 分
 * - painLocation：0=无 1=右下腹 2=左下腹 3=脐周 4=全腹
 */
@Entity(tableName = "daily_symptoms", indices = [Index("date")])
data class DailySymptom(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    /** yyyy-MM-dd（普通索引，不再唯一） */
    val date: String,
    /** 记录时间 HH:mm（补录时可调整；空 = 用 createdAt 时间） */
    @ColumnInfo(defaultValue = "''")
    val time: String = "",
    /** 当日排便次数（白天） */
    val bowelCount: Int = 0,
    /** 是否有夜间腹泻 */
    val nightDiarrhea: Boolean = false,
    /** 布里斯托便级 1~7，0 未记录 */
    val bristolType: Int = 0,
    /** 便血 0=无 1=少量 2=明显 3=血块 */
    val blood: Int = 0,
    /** 是否带黏液 */
    val mucus: Boolean = false,
    /** 腹痛 0~10 分 */
    val painScore: Int = 0,
    /** 腹痛部位 0=无 1=右下腹 2=左下腹 3=脐周 4=全腹 */
    val painLocation: Int = 0,
    /** 是否有急迫感 */
    val urgency: Boolean = false,
    /** 其他不适 */
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** 病情活动度分级（患者自评分的展示；labelRes 为多语言文案资源） */
enum class ActivityLevel(@StringRes val labelRes: Int) {
    REMISSION(com.ucdaily.R.string.activity_remission),
    MILD(com.ucdaily.R.string.activity_mild),
    MODERATE(com.ucdaily.R.string.activity_moderate),
    SEVERE(com.ucdaily.R.string.activity_severe);

    companion object {
        fun fromScore(score: Int): ActivityLevel = when {
            score <= 0 -> REMISSION
            score <= 3 -> MILD
            score <= 5 -> MODERATE
            else -> SEVERE
        }
    }
}

/**
 * 参考活动度评分（简化 UCDAI 患者自评部分，0~8 分，仅供自我监测参考，不替代医生诊断）：
 *
 * - 排便次数得分（UCDAI 官方频度量表）：
 *   ≤4 次=0，5~6 次=1，7~10 次=2，11~14 次=3，≥15 次=4
 * - 便血得分（对齐 UCDAI 直肠出血量表 0~4）：
 *   无=0，少量=1，明显=3，血块=4
 */
fun activityScore(s: DailySymptom): Int {
    val freqScore = when {
        s.bowelCount <= 4 -> 0
        s.bowelCount <= 6 -> 1
        s.bowelCount <= 10 -> 2
        s.bowelCount <= 14 -> 3
        else -> 4
    }
    val bloodScore = when (s.blood) {
        1 -> 1
        2 -> 3
        3 -> 4
        else -> 0
    }
    return freqScore + bloodScore
}

val DailySymptom.activityLevel: ActivityLevel
    get() = ActivityLevel.fromScore(activityScore(this))

/** 布里斯托便级多语言文案资源 id（index 1~7） */
val BRISTOL_LABELS = listOf(
    com.ucdaily.R.string.bristol_1, com.ucdaily.R.string.bristol_2,
    com.ucdaily.R.string.bristol_3, com.ucdaily.R.string.bristol_4,
    com.ucdaily.R.string.bristol_5, com.ucdaily.R.string.bristol_6,
    com.ucdaily.R.string.bristol_7
)

/** 腹痛部位多语言文案资源 id（index 0~4） */
val PAIN_LOCATION_LABELS = listOf(
    com.ucdaily.R.string.pain_loc_none, com.ucdaily.R.string.pain_loc_right_lower,
    com.ucdaily.R.string.pain_loc_left_lower, com.ucdaily.R.string.pain_loc_periumbilical,
    com.ucdaily.R.string.pain_loc_whole
)

/** 便血多语言文案资源 id（index 0~3） */
val BLOOD_LABELS = listOf(
    com.ucdaily.R.string.blood_none, com.ucdaily.R.string.blood_little,
    com.ucdaily.R.string.blood_clear, com.ucdaily.R.string.blood_clots
)
