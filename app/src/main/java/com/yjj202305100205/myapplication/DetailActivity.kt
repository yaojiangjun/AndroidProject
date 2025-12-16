package com.yjj202305100205.myapplication

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.*

class DetailActivity : AppCompatActivity() {
    private lateinit var record: Record

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detail)

        // 获取传递的记录
        record = intent.getParcelableExtra("record")!!

        // 显示记录数据
        showRecordData()

        // 编辑按钮
        findViewById<Button>(R.id.editButton).setOnClickListener {
            val intent = Intent(this, EditActivity::class.java)
            intent.putExtra("record", record)
            startActivity(intent)
            finish()
        }

        // 返回按钮
        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }
    }

    private fun showRecordData() {
        // 显示内容
        findViewById<TextView>(R.id.contentTv).text = record.content

        // 显示分类
        findViewById<TextView>(R.id.categoryTv).text = record.categoryId

        // 显示时间
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        findViewById<TextView>(R.id.timeTv).text = sdf.format(Date(record.time))

        // 显示图片
        val imageView = findViewById<ImageView>(R.id.noteImageIv)
        if (!record.imagePath.isNullOrEmpty()) {
            imageView.visibility = View.VISIBLE
            Glide.with(this)
                .load(record.imagePath)
                .into(imageView)
        } else {
            imageView.visibility = View.GONE
        }
    }
}