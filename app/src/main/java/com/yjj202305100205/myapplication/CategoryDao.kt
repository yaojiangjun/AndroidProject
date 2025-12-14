package com.yjj202305100205.myapplication

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    // 插入分类
    @Insert
    suspend fun insertCategory(category: Category)

    // 查询所有分类
    @Query("SELECT * FROM categories")
    fun getAllCategories(): Flow<List<Category>>

    // 根据分类ID查询记录
    @Query("SELECT * FROM input_records WHERE categoryId = :categoryId ORDER BY time DESC")
    fun getRecordsByCategory(categoryId: String): Flow<List<Record>>
}