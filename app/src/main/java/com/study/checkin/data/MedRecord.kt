package com.study.checkin.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 一条服药记录（可多条/天） */
@Entity(tableName = "med_records", indices = [Index("date")])
data class MedRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    /** yyyy-MM-dd */
    val date: String,
    /** HH:mm */
    val time: String,
    /** 药物名称 */
    val name: String,
    /** 剂量说明，如 "1 片"、"500mg" */
    val dose: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
