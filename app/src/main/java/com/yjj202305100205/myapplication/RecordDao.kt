package com.yjj202305100205.myapplication

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Insert
    suspend fun insertRecord(record: Record)

    @Delete
    suspend fun deleteRecord(record: Record)

    @Query("DELETE FROM input_records")
    suspend fun deleteAllRecords()

    // 按置顶+时间排序（置顶的在前）
    @Query("SELECT * FROM input_records ORDER BY isTop DESC, time DESC")
    fun getAllRecords(): Flow<List<Record>>

    @Query("SELECT * FROM input_records WHERE content LIKE '%' || :keyword || '%' ORDER BY isTop DESC, time DESC")
    fun searchRecords(keyword: String): Flow<List<Record>>

    @Update
    suspend fun updateRecord(record: Record)

    // 统计：各分类记录数
    @Query("SELECT c.categoryName, COUNT(r.id) as count FROM categories c LEFT JOIN input_records r ON c.categoryId = r.categoryId GROUP BY c.categoryId")
    fun getCategoryCount(): Flow<List<CategoryCount>>

    // 统计：总记录数
    @Query("SELECT COUNT(*) FROM input_records")
    suspend fun getTotalRecordCount(): Int
    // RecordDao 中添加排序查询
    @Query("SELECT * FROM input_records ORDER BY time DESC")
    suspend fun getAllSortedByTimeDesc(): List<Record>

    @Query("SELECT * FROM input_records ORDER BY time ASC")
    suspend fun getAllSortedByTimeAsc(): List<Record>
}

// 新增：分类统计数据类
data class CategoryCount(
    val categoryName: String,
    val count: Int
)