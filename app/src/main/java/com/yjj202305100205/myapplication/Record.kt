package com.yjj202305100205.myapplication

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "input_records",
    foreignKeys = [ForeignKey(
        entity = Category::class,
        parentColumns = ["categoryId"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class Record(
    @PrimaryKey val id: String,
    val content: String,
    val time: Long,
    val categoryId: String = "default_category",
    val imagePath: String? = null, // 新增：图片路径
    val isTop: Boolean = false // 新增：是否置顶（为功能6准备）
)