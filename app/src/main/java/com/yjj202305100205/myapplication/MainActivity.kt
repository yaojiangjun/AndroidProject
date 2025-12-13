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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.UUID

// RecyclerView适配器（无需修改，仅适配Record类）
class RecordAdapter(
    private val onDeleteClick: (Record) -> Unit
) : androidx.recyclerview.widget.ListAdapter<Record, RecordAdapter.ViewHolder>(RecordDiffCallback()) {

    inner class ViewHolder(itemView: android.view.View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView) {
        val contentTextView: TextView = itemView.findViewById(R.id.contentTextView)
        val deleteButton: Button = itemView.findViewById(R.id.deleteButton)

        fun bind(record: Record) {
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

    private class RecordDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<Record>() {
        override fun areItemsTheSame(oldItem: Record, newItem: Record): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Record, newItem: Record): Boolean {
            return oldItem == newItem
        }
    }
}

// 主Activity
class MainActivity : AppCompatActivity() {
    private lateinit var recordAdapter: RecordAdapter
    private lateinit var recordDao: RecordDao

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

        // 初始化Room数据库和Dao
        val db = AppDatabase.getInstance(this)
        recordDao = db.recordDao()

        // 初始化RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.historyRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recordAdapter = RecordAdapter { record ->
            // 删除单条记录（协程执行）
            lifecycleScope.launch {
                recordDao.deleteRecord(record)
                Toast.makeText(this@MainActivity, "删除成功", Toast.LENGTH_SHORT).show()
            }
        }
        recyclerView.adapter = recordAdapter

        // 监听数据库数据变化，自动刷新列表
        lifecycleScope.launch {
            recordDao.getAllRecords().collect { records ->
                recordAdapter.submitList(records)
            }
        }

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
                // 创建新记录并插入数据库（协程执行）
                val newRecord = Record(
                    id = UUID.randomUUID().toString(),
                    content = inputText,
                    time = System.currentTimeMillis()
                )
                lifecycleScope.launch {
                    recordDao.insertRecord(newRecord)
                }
                // 跳转第二页
                val intent = Intent(this, SecondActivity::class.java)
                intent.putExtra("input_data", inputText)
                startActivity(intent)
                inputEditText.text.clear()
            }
        }

        // 清空按钮点击事件
        clearButton.setOnClickListener {
            lifecycleScope.launch {
                recordDao.deleteAllRecords()
                Toast.makeText(this@MainActivity, "已清空所有历史", Toast.LENGTH_SHORT).show()
            }
        }
    }
}