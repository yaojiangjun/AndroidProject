package com.yjj202305100205.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SecondActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_second)

        // 获取传递的数据
        val receivedData = intent.getStringExtra("input_data") ?: "无数据"

        // 显示数据
        findViewById<TextView>(R.id.receivedDataTextView).text = "收到：$receivedData"

        // 返回按钮点击事件
        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish() // 关闭当前页面，返回上一页
        }
    }
}