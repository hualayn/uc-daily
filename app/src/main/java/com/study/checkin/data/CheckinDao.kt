package com.study.checkin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CheckinDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: CheckinEntity)

    @Query("SELECT * FROM checkin_records ORDER BY date DESC LIMIT :limit")
    suspend fun getRecords(limit: Int): List<CheckinEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM checkin_records WHERE date = :date)")
    suspend fun isCheckinToday(date: String): Boolean

    @Query("SELECT COUNT(DISTINCT date) FROM checkin_records")
    suspend fun getTotalCheckinCount(): Int
}
