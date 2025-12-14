package com.yjj202305100205.myapplication

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

// 版本号从1→2，添加Category实体
@Database(entities = [Record::class, Category::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recordDao(): RecordDao
    abstract fun categoryDao(): CategoryDao // 新增分类Dao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "input_record_db"
                )
                    .fallbackToDestructiveMigration() // 简单升级：删除旧库重建（适合开发阶段）
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}