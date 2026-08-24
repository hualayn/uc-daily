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
    // 注意：食物标签不再用类型转换器——它的 JSON 签名与照片列表相同（List<String>↔String），
    // Room 按 (from,to) 签名匹配会报 "Multiple functions define the same conversion"。
    // 改为 MealRecord.tagsJson 普通 String 列 + MealRecord.tags 计算属性。
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

/** v5：饮食记录增加食物标签列；新增服药、每日感受、食物标签三张表 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // tags_json 列：实体声明了 defaultValue "''"，这里必须一致，否则 Room 校验失败
        db.execSQL("ALTER TABLE meal_records ADD COLUMN `tags_json` TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `med_records` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`time` TEXT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`dose` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_med_records_date` ON `med_records` (`date`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `daily_notes` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`date` TEXT NOT NULL, " +
                "`text` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_daily_notes_date` ON `daily_notes` (`date`)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `food_tags` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, " +
                "`tolerance` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_food_tags_name` ON `food_tags` (`name`)")
    }
}

/** v6：排便记录允许一天多条——去掉 date 唯一约束，改建普通索引（原有数据每天本来就只有一条，不受影响） */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS `index_daily_symptoms_date`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_daily_symptoms_date` ON `daily_symptoms` (`date`)")
    }
}

/** v7：排便记录增加 time 列（补录时可调时间；旧数据为空，展示时回退到 createdAt） */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE daily_symptoms ADD COLUMN `time` TEXT NOT NULL DEFAULT ''")
    }
}

/** v8：食物标签增加 sortOrder 列（拖动换序/跨区移动的持久化排序键），存量数据按 id 顺序初始化 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE food_tags ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE food_tags SET sortOrder = id")
    }
}

@Database(
    entities = [MealRecord::class, DailySymptom::class, MedRecord::class, DailyNote::class, FoodTag::class],
    version = 8
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
                    "checkin_db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8)
                    // 开发迭代期的安全兜底（正式发布前建议移除）：
                    // 遇到未提供的迁移（版本断层）时，销毁旧库重建，而不是崩溃。
                    // 注意：它不处理“版本相同但表结构陈旧”的覆盖安装残留，
                    // 那种情况需卸载/清除应用数据（见开发流程）。
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
