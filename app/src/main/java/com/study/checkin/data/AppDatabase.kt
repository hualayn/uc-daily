package com.study.checkin.data

import android.content.Context
import androidx.room.Room
import androidx.room.Database

@Database(entities = [CheckinEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun checkinDao(): CheckinDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "checkin_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
