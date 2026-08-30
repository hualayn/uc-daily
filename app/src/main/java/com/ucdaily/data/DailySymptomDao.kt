package com.ucdaily.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface DailySymptomDao {
    /** 新增一条排便记录（同一天可多条） */
    @Insert
    suspend fun insert(s: DailySymptom): Long

    /** 编辑已有记录 */
    @Update
    suspend fun update(s: DailySymptom)

    @Query("SELECT * FROM daily_symptoms WHERE id = :id")
    suspend fun getById(id: Int): DailySymptom?

    /** 某一天的全部排便记录（新记录在前） */
    @Query("SELECT * FROM daily_symptoms WHERE date = :date ORDER BY id DESC")
    suspend fun getByDate(date: String): List<DailySymptom>

    /** 日期区间（含首尾）的全部排便记录，按日期/时间升序（记录导出用） */
    @Query("SELECT * FROM daily_symptoms WHERE date BETWEEN :start AND :end ORDER BY date ASC, time ASC, id ASC")
    suspend fun getBetween(start: String, end: String): List<DailySymptom>

    @Query("SELECT * FROM daily_symptoms ORDER BY date DESC, id DESC")
    suspend fun getAll(): List<DailySymptom>

    @Query("DELETE FROM daily_symptoms WHERE id = :id")
    suspend fun deleteById(id: Int)
}
