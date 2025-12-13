package com.yjj202305100205.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    // SharedPreferences相关常量
    companion object {
        private const val PREF_NAME = "InputHistory"
        private const val KEY_LAST_INPUT = "last_input"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 适配系统状态栏
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 获取控件实例
        val inputEditText = findViewById<EditText>(R.id.inputEditText)
        val submitButton = findViewById<Button>(R.id.submitButton)
        val resultTextView = findViewById<TextView>(R.id.resultTextView)

        // 读取历史记录并显示
        val lastInput = getLastInputFromSP()
        if (lastInput.isNotEmpty()) {
            resultTextView.text = "上次输入：$lastInput"
        }

        // 按钮点击事件
        submitButton.setOnClickListener {
            val inputText = inputEditText.text.toString().trim()
            if (inputText.isEmpty()) {
                // 显示提示：输入为空
                Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show()
            } else {
                // 显示输入内容到结果文本
                resultTextView.text = "你输入的是：$inputText"
                // 保存到SharedPreferences
                saveInputToSP(inputText)
                // 跳转到SecondActivity并传递数据
                val intent = Intent(this, SecondActivity::class.java)
                intent.putExtra("input_data", inputText)
                startActivity(intent)
                // 清空输入框
                inputEditText.text.clear()
            }
        }
    }

    // 保存输入到SharedPreferences
    private fun saveInputToSP(text: String) {
        val sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        sp.edit().putString(KEY_LAST_INPUT, text).apply()
    }

    // 从SharedPreferences读取最近输入
    private fun getLastInputFromSP(): String {
        val sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        return sp.getString(KEY_LAST_INPUT, "") ?: ""
    }
}