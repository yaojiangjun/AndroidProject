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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

// 数据类：历史记录实体
data class InputRecord(
    val id: String, // 唯一标识
    val content: String, // 输入内容
    val time: Long // 时间戳（用于排序）
)

// RecyclerView适配器
class RecordAdapter(
    private val onDeleteClick: (InputRecord) -> Unit
) : androidx.recyclerview.widget.ListAdapter<InputRecord, RecordAdapter.ViewHolder>(RecordDiffCallback()) {

    inner class ViewHolder(itemView: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val contentTextView: TextView = itemView.findViewById(R.id.contentTextView)
        val deleteButton: Button = itemView.findViewById(R.id.deleteButton)

        fun bind(record: InputRecord) {
            contentTextView.text = record.content
            deleteButton.setOnClickListener {
                onDeleteClick(record)
            }
        }
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // DiffUtil：优化列表刷新
    private class RecordDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<InputRecord>() {
        override fun areItemsTheSame(oldItem: InputRecord, newItem: InputRecord): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: InputRecord, newItem: InputRecord): Boolean {
            return oldItem == newItem
        }
    }
}

// 主Activity
class MainActivity : AppCompatActivity() {
    // SharedPreferences相关常量
    companion object {
        private const val PREF_NAME = "InputHistory"
        private const val KEY_RECORDS = "all_records" // 保存所有记录
    }

    private lateinit var recordAdapter: RecordAdapter
    private val records = mutableListOf<InputRecord>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 状态栏适配
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // 初始化RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.historyRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recordAdapter = RecordAdapter { record ->
            // 删除单条记录
            deleteRecord(record)
        }
        recyclerView.adapter = recordAdapter

        // 加载历史记录
        loadRecords()

        // 控件初始化
        val inputEditText = findViewById<EditText>(R.id.inputEditText)
        val submitButton = findViewById<Button>(R.id.submitButton)
        val resultTextView = findViewById<TextView>(R.id.resultTextView)
        val clearButton = findViewById<Button>(R.id.clearButton)

        // 提交按钮点击事件
        submitButton.setOnClickListener {
            val inputText = inputEditText.text.toString().trim()
            if (inputText.isEmpty()) {
                Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show()
            } else {
                resultTextView.text = "你输入的是：$inputText"
                // 保存新记录
                val newRecord = InputRecord(
                    id = UUID.randomUUID().toString(),
                    content = inputText,
                    time = System.currentTimeMillis()
                )
                saveRecord(newRecord)
                // 跳转第二页
                val intent = Intent(this, SecondActivity::class.java)
                intent.putExtra("input_data", inputText)
                startActivity(intent)
                inputEditText.text.clear()
            }
        }

        // 清空按钮点击事件
        clearButton.setOnClickListener {
            clearAllRecords()
            Toast.makeText(this, "已清空所有历史", Toast.LENGTH_SHORT).show()
        }
    }

    // 加载所有记录
    private fun loadRecords() {
        val sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        val json = sp.getString(KEY_RECORDS, "[]") ?: "[]"
        val type = object : TypeToken<List<InputRecord>>() {}.type
        val savedRecords = Gson().fromJson<List<InputRecord>>(json, type)
        records.clear()
        records.addAll(savedRecords.sortedByDescending { it.time }) // 按时间倒序
        recordAdapter.submitList(records.toList())
    }

    // 保存单条记录
    private fun saveRecord(record: InputRecord) {
        records.add(0, record) // 添加到头部
        updateRecordsInSP()
        recordAdapter.submitList(records.toList())
    }

    // 删除单条记录
    private fun deleteRecord(record: InputRecord) {
        records.removeAll { it.id == record.id }
        updateRecordsInSP()
        recordAdapter.submitList(records.toList())
    }

    // 清空所有记录
    private fun clearAllRecords() {
        records.clear()
        updateRecordsInSP()
        recordAdapter.submitList(emptyList())
    }

    // 更新SP中的记录
    private fun updateRecordsInSP() {
        val sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE)
        val json = Gson().toJson(records)
        sp.edit().putString(KEY_RECORDS, json).apply()
    }
}