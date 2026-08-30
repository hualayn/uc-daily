package com.ucdaily.data

import androidx.annotation.StringRes
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalTime

/** 餐次类型（labelRes 为多语言文案资源，展示用 stringResource / context.getString 解析） */
enum class MealType(@StringRes val labelRes: Int) {
    BREAKFAST(com.ucdaily.R.string.meal_breakfast),
    LUNCH(com.ucdaily.R.string.meal_lunch),
    DINNER(com.ucdaily.R.string.meal_dinner),
    SNACK(com.ucdaily.R.string.meal_snack);

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
