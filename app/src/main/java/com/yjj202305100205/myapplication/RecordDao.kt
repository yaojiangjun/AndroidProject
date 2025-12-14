package com.yjj202305100205.myapplication

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update // 新增：导入Update注解（之前缺失导致报错）
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

    // 模糊搜索记录
    @Query("SELECT * FROM input_records WHERE content LIKE '%' || :keyword || '%' ORDER BY time DESC")
    fun searchRecords(keyword: String): Flow<List<Record>>

    // 更新记录
    @Update
    suspend fun updateRecord(record: Record)
}