package com.yjj202305100205.myapplication

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

// 定义数据库版本、包含的实体类
@Database(entities = [Record::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    // 提供Dao接口的实现（Room自动生成）
    abstract fun recordDao(): RecordDao

    // 单例模式
    companion object {
        // 双重校验锁，保证线程安全
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext, // 用应用上下文，避免内存泄漏
                    AppDatabase::class.java,
                    "input_record_db" // 数据库文件名
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}