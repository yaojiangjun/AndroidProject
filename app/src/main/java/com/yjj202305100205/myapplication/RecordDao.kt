package com.yjj202305100205.myapplication

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    // 插入单条记录（重复主键会替换）
    @Insert
    suspend fun insertRecord(record: Record)

    // 删除单条记录
    @Delete
    suspend fun deleteRecord(record: Record)

    // 删除所有记录
    @Query("DELETE FROM input_records")
    suspend fun deleteAllRecords()

    // 查询所有记录（按时间倒序，Flow自动监听数据变化）
    @Query("SELECT * FROM input_records ORDER BY time DESC")
    fun getAllRecords(): Flow<List<Record>>
}