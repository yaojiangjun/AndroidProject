package com.yjj202305100205.myapplication

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

// 关联分类表（外键）
@Entity(
    tableName = "input_records",
    foreignKeys = [ForeignKey(
        entity = Category::class,
        parentColumns = ["categoryId"],
        childColumns = ["categoryId"],
        onDelete = ForeignKey.CASCADE // 分类删除时，关联记录也删除
    )]
)
data class Record(
    @PrimaryKey val id: String,
    val content: String,
    val time: Long,
    val categoryId: String = "default_category" // 默认分类ID
)