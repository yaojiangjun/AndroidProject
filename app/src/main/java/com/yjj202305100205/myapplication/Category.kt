package com.yjj202305100205.myapplication

import androidx.room.Entity
import androidx.room.PrimaryKey

// 分类表
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val categoryId: String, // 分类ID
    val categoryName: String, // 分类名称（如“学习笔记”）
    val userId: String = "default_user" // 简化：默认单用户，后续可扩展多用户
)