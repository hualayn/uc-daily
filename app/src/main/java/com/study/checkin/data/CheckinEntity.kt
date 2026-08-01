package com.study.checkin.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "checkin_records")
data class CheckinEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String  // yyyy-MM-dd
)
