package com.study.checkin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DailySymptomDao {
    /** 同一天重复保存时替换旧记录 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(s: DailySymptom): Long

    @Query("SELECT * FROM daily_symptoms WHERE date = :date")
    suspend fun getByDate(date: String): DailySymptom?

    @Query("SELECT * FROM daily_symptoms ORDER BY date ASC")
    suspend fun getAll(): List<DailySymptom>

    @Query("DELETE FROM daily_symptoms WHERE id = :id")
    suspend fun deleteById(id: Int)
}
