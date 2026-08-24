package com.study.checkin.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime

/** 餐次类型 */
enum class MealType(val label: String) {
    BREAKFAST("早餐"),
    LUNCH("午餐"),
    DINNER("晚餐"),
    SNACK("加餐");

    companion object {
        /** 根据当前时间给出默认餐次 */
        fun fromTime(time: LocalTime): MealType = when (time.hour) {
            in 5..10 -> BREAKFAST
            in 11..14 -> LUNCH
            in 15..16 -> SNACK
            else -> DINNER // 17:00 ~ 次日 4:59
        }
    }
}

/** 一条饮食记录：某一餐的照片与备注 */
@Entity(
    tableName = "meal_records",
    indices = [Index("date")]
)
data class MealRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String,     // yyyy-MM-dd
    val time: String,     // HH:mm
    val mealType: MealType,
    val photos: List<String> = emptyList(),  // 照片绝对路径，JSON 存储
    /** 食物标签 JSON（存库列）；读写列表请用 [tags] 属性 */
    @ColumnInfo(defaultValue = "''")
    val tagsJson: String = "",
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    /** 食物标签（名称列表） */
    val tags: List<String>
        get() = tagsDecode(tagsJson)

    companion object {
        /** 食物标签序列化为库内 JSON：{"tags":["牛奶",...]} */
        fun tagsEncode(tags: List<String>): String =
            JSONObject().put("tags", JSONArray(tags)).toString()

        /** 解析食物标签 JSON，解析失败兜底为空列表 */
        fun tagsDecode(json: String): List<String> = try {
            val arr = JSONObject(json).optJSONArray("tags") ?: JSONArray()
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
