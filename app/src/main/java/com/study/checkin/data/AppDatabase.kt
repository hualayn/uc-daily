package com.study.checkin.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE checkin_records ADD COLUMN photoPath TEXT NOT NULL DEFAULT ''")
    }
}

/** v3：从学习打卡应用改造为饮食记录应用，旧表无保留价值，删除旧表并创建新表 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 删除旧的打卡记录表
        db.execSQL("DROP TABLE IF EXISTS checkin_records")
        // 注意：Room 在迁移时不会自动建表，必须手动创建 meal_records，
        // 且列结构需与 MealRecord 实体完全一致，否则校验会抛 IllegalStateException
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `meal_records` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`time` TEXT NOT NULL, " +
                "`mealType` INTEGER NOT NULL, " +
                "`photos` TEXT NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        // 建立与实体 @Index("date") 一致的索引
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_meal_records_date` ON `meal_records` (`date`)"
        )
    }
}

/** v4：新增每日排便/症状记录表（用于活动度评分与日历热力图） */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Room 迁移不会自动建表，手动创建 daily_symptoms，列结构须与实体一致
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_symptoms` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`bowelCount` INTEGER NOT NULL, " +
                "`nightDiarrhea` INTEGER NOT NULL, " +
                "`bristolType` INTEGER NOT NULL, " +
                "`blood` INTEGER NOT NULL, " +
                "`mucus` INTEGER NOT NULL, " +
                "`painScore` INTEGER NOT NULL, " +
                "`painLocation` INTEGER NOT NULL, " +
                "`urgency` INTEGER NOT NULL, " +
                "`note` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        // 与实体 Index("date", unique = true) 一致的索引名
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_symptoms_date` ON `daily_symptoms` (`date`)"
        )
    }
}

@Database(entities = [MealRecord::class, DailySymptom::class], version = 4)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mealRecordDao(): MealRecordDao
    abstract fun dailySymptomDao(): DailySymptomDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "checkin_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    // 开发迭代期的安全兜底：遇到未提供的迁移（版本断层）时，
                    // 销毁旧库重建，而不是崩溃。正式发布前建议移除。
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
