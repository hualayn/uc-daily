package com.ucdaily.data

import androidx.annotation.StringRes
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 食物耐受状态（labelRes 为多语言文案资源，展示用 stringResource / context.getString 解析） */
enum class FoodTolerance(@StringRes val labelRes: Int) {
    OK(com.ucdaily.R.string.tolerance_ok),
    CAUTION(com.ucdaily.R.string.tolerance_try),
    BAD(com.ucdaily.R.string.tolerance_bad);

    companion object {
        fun fromValue(v: Int): FoodTolerance = entries.getOrElse(v) { CAUTION }
    }
}

/** 食物标签：用于"耐受"页管理与饮食记录打标 */
@Entity(tableName = "food_tags", indices = [Index("name", unique = true)])
data class FoodTag(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    /** 食物名，唯一 */
    val name: String,
    /** 耐受状态，FoodTolerance.ordinal */
    val tolerance: Int = FoodTolerance.OK.ordinal,
    /** 用户拖动排序键（展示顺序 = sortOrder 升序；分区内相对顺序即全局顺序的相对顺序） */
    @ColumnInfo(defaultValue = "0")
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
