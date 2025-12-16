package com.yjj202305100205.myapplication

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize

@Parcelize // 添加Parcelize注解，自动生成Parcelable代码
@Entity(
    tableName = "input_records", // 保持你的表名不变
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
    //val imagePaths: List<String> = emptyList(), // 改为列表
    val imagePath: String? = null, // 确保存在该字
    val isTop: Boolean = false
) : Parcelable // 实现Parcelable接口