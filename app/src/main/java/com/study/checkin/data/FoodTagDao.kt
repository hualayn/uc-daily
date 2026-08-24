package com.study.checkin.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FoodTagDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: FoodTag): Long

    @Query("UPDATE food_tags SET tolerance = :tolerance WHERE name = :name")
    suspend fun setTolerance(name: String, tolerance: Int)

    /** 一次性更新某标签的耐受状态与排序键（拖动落点时调用） */
    @Query("UPDATE food_tags SET tolerance = :tolerance, sortOrder = :sortOrder WHERE name = :name")
    suspend fun updateTag(name: String, tolerance: Int, sortOrder: Int)

    /** 仅更新排序键（同分区内拖动换序时调用） */
    @Query("UPDATE food_tags SET sortOrder = :sortOrder WHERE name = :name")
    suspend fun setSortOrder(name: String, sortOrder: Int)

    @Query("SELECT * FROM food_tags ORDER BY sortOrder ASC, id ASC")
    suspend fun getAll(): List<FoodTag>

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM food_tags")
    suspend fun maxSortOrder(): Int

    @Query("DELETE FROM food_tags WHERE name = :name")
    suspend fun deleteByName(name: String)
}
