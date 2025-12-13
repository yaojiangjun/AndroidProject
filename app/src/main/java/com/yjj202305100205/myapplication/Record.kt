package com.yjj202305100205.myapplication

import androidx.room.Entity
import androidx.room.PrimaryKey

// 定义数据库表名：input_records
@Entity(tableName = "input_records")
data class Record(
    @PrimaryKey val id: String, // 主键（用UUID生成，保证唯一）
    val content: String, // 输入内容
    val time: Long // 时间戳（用于排序）
)