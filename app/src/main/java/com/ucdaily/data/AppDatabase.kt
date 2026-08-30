package com.ucdaily.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import org.json.JSONArray
import org.json.JSONObject

/** Room 类型转换器：枚举与照片列表（JSON 存储） */
class Converters {
    @TypeConverter
    fun fromMealType(type: MealType?): Int = type?.ordinal ?: MealType.DINNER.ordinal

    @TypeConverter
    fun toMealType(value: Int): MealType =
        MealType.entries.getOrElse(value) { MealType.DINNER }

    @TypeConverter
    fun fromPhotoPaths(photos: List<String>): String =
        JSONObject().put("paths", JSONArray(photos)).toString()

    @TypeConverter
    fun toPhotoPaths(json: String): List<String> = try {
        val arr = JSONObject(json).optJSONArray("paths") ?: JSONArray()
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }
    // 注意：食物标签不再用类型转换器——它的 JSON 签名与照片列表相同（List<String>↔String），
    // Room 按 (from,to) 签名匹配会报 "Multiple functions define the same conversion"。
    // 改为 MealRecord.tagsJson 普通 String 列 + MealRecord.tags 计算属性。
}

@Database(
    entities = [MealRecord::class, DailySymptom::class, MedRecord::class, DailyNote::class, FoodTag::class],
    version = 1
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealRecordDao(): MealRecordDao
    abstract fun dailySymptomDao(): DailySymptomDao
    abstract fun medRecordDao(): MedRecordDao
    abstract fun dailyNoteDao(): DailyNoteDao
    abstract fun foodTagDao(): FoodTagDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "uc_daily_db"
                )
                    // 开发迭代期的安全兜底（正式发布前建议移除）：
                    // 版本不匹配（如开发期手动改 schema 未写迁移）时销毁旧库重建，而不是崩溃。
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
