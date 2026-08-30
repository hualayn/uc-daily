package com.ucdaily.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface MedRecordDao {
    @Insert
    suspend fun insert(record: MedRecord): Long

    @Update
    suspend fun update(record: MedRecord)

    @Query("SELECT * FROM med_records WHERE id = :id")
    suspend fun getById(id: Int): MedRecord?

    @Query("SELECT * FROM med_records WHERE date = :date ORDER BY time ASC, id ASC")
    suspend fun getByDate(date: String): List<MedRecord>

    /** 日期区间（含首尾）的全部服药记录，按日期/时间升序（记录导出用） */
    @Query("SELECT * FROM med_records WHERE date BETWEEN :start AND :end ORDER BY date ASC, time ASC, id ASC")
    suspend fun getMedsBetween(start: String, end: String): List<MedRecord>

    /** 全部服药记录（日期倒序；统计页"服药记录"汇总列表用） */
    @Query("SELECT * FROM med_records ORDER BY date DESC")
    suspend fun getAllMedsDesc(): List<MedRecord>

    @Query("SELECT COUNT(*) FROM med_records")
    suspend fun getCount(): Int

    /** 某天服药记录条数（非挂起：后台闹钟/广播场景不走协程；单次 COUNT 查询，任意线程可查） */
    @Query("SELECT COUNT(*) FROM med_records WHERE date = :date")
    fun countByDate(date: String): Int

    /** 最近用过的药名（去重），供服药面板快捷选择 */
    @Query("SELECT DISTINCT name FROM med_records WHERE name != '' ORDER BY rowid DESC LIMIT 12")
    suspend fun getRecentNames(): List<String>

    @Query("DELETE FROM med_records WHERE id = :id")
    suspend fun deleteById(id: Int)
}
