package com.yjj202305100205.myapplication

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SecondActivity : AppCompatActivity() {
    private lateinit var showTextView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 直接加载布局，不使用视图绑定
        setContentView(R.layout.activity_second)

        // 手动获取控件
        showTextView = findViewById(R.id.showTextView)

        // 显示数据
        val inputText = intent.getStringExtra("input_data")
        showTextView.text = inputText
    }

    fun onBackClick(view: View) {
        finish()
    }
}