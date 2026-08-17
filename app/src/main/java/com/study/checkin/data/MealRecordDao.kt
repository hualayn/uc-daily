package com.study.checkin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface MealRecordDao {
    @Insert
    suspend fun insert(record: MealRecord): Long

    @Update
    suspend fun update(record: MealRecord)

    @Query("SELECT * FROM meal_records WHERE id = :id")
    suspend fun getRecordById(id: Int): MealRecord?

    @Query("SELECT * FROM meal_records WHERE date = :date ORDER BY id ASC")
    suspend fun getRecordsByDate(date: String): List<MealRecord>

    @Query("SELECT DISTINCT date FROM meal_records")
    suspend fun getRecordDates(): List<String>

    @Query("SELECT COUNT(DISTINCT date) FROM meal_records")
    suspend fun getRecordDays(): Int

    @Query("SELECT COUNT(*) FROM meal_records")
    suspend fun getTotalCount(): Int

    @Query("DELETE FROM meal_records WHERE id = :id")
    suspend fun deleteById(id: Int)
}
