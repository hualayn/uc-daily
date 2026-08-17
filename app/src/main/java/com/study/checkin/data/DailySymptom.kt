package com.study.checkin.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 每日排便/症状记录，一天最多一条（date 唯一）。
 *
 * 字段取值说明：
 * - bristolType：布里斯托大便分类 1~7，0 表示未记录
 * - blood：便血 0=无 1=少量 2=明显 3=血块
 * - painScore：腹痛 0~10 分
 * - painLocation：0=无 1=右下腹 2=左下腹 3=脐周 4=全腹
 */
@Entity(tableName = "daily_symptoms", indices = [Index("date", unique = true)])
data class DailySymptom(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    /** yyyy-MM-dd，唯一 */
    val date: String,
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

/** 病情活动度分级（患者自评分的展示） */
enum class ActivityLevel(val label: String) {
    REMISSION("缓解"),
    MILD("轻度活动"),
    MODERATE("中度活动"),
    SEVERE("重度活动");

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

/** 布里斯托便级显示文案（index 1~7） */
val BRISTOL_LABELS = listOf(
    "硬块", "块状香肠", "带裂纹香肠", "光滑香肠",
    "软块", "糊状", "水样"
)

/** 腹痛部位显示文案（index 0~4） */
val PAIN_LOCATION_LABELS = listOf("无", "右下腹", "左下腹", "脐周", "全腹")

/** 便血显示文案（index 0~3） */
val BLOOD_LABELS = listOf("无", "少量", "明显", "血块")
