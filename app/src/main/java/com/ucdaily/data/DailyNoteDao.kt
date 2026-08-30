package com.ucdaily.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DailyNoteDao {
    /** 同一天重复保存时替换旧记录 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: DailyNote): Long

    @Query("SELECT * FROM daily_notes WHERE date = :date")
    suspend fun getByDate(date: String): DailyNote?

    /** 日期区间（含首尾）的全部感受，按日期升序（记录导出用） */
    @Query("SELECT * FROM daily_notes WHERE date BETWEEN :start AND :end ORDER BY date ASC")
    suspend fun getBetween(start: String, end: String): List<DailyNote>

    /** 全部感受（日期倒序；统计页"感受记录"汇总列表用） */
    @Query("SELECT * FROM daily_notes ORDER BY date DESC")
    suspend fun getAllNotesDesc(): List<DailyNote>

    @Query("SELECT COUNT(*) FROM daily_notes")
    suspend fun getCount(): Int

    @Query("DELETE FROM daily_notes WHERE id = :id")
    suspend fun deleteById(id: Int)
}
