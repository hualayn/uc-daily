package com.study.checkin.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 每日感受/笔记，一天一条（date 唯一） */
@Entity(tableName = "daily_notes", indices = [Index("date", unique = true)])
data class DailyNote(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    /** yyyy-MM-dd，唯一 */
    val date: String,
    val text: String,
    val createdAt: Long = System.currentTimeMillis()
)
