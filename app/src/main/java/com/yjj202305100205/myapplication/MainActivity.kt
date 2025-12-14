package com.yjj202305100205.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.ViewGroup // 新增：导入ViewGroup（之前缺失导致报错）
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

// 记录适配器（包含删除、编辑回调）
class RecordAdapter(
    private val onDeleteClick: (Record) -> Unit,
    private val onEditClick: (Record) -> Unit
) : androidx.recyclerview.widget.ListAdapter<Record, RecordAdapter.ViewHolder>(RecordDiffCallback()) {

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val contentTextView: TextView = itemView.findViewById(R.id.contentTextView)
        val deleteButton: Button = itemView.findViewById(R.id.deleteButton)
        val editButton: Button = itemView.findViewById(R.id.editButton)

        fun bind(record: Record) {
            contentTextView.text = record.content
            deleteButton.setOnClickListener { onDeleteClick(record) }
            editButton.setOnClickListener { onEditClick(record) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    // DiffUtil优化列表刷新
    private class RecordDiffCallback : androidx.recyclerview.widget.DiffUtil.ItemCallback<Record>() {
        override fun areItemsTheSame(oldItem: Record, newItem: Record): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Record, newItem: Record): Boolean {
            return oldItem == newItem
        }
    }
}

// 主Activity（整合所有功能）
class MainActivity : AppCompatActivity() {
    // 权限请求常量
    private val REQUEST_STORAGE_PERMISSION = 1001
    // Room相关
    private lateinit var recordDao: RecordDao
    private lateinit var categoryDao: CategoryDao
    // 控件
    private lateinit var recordAdapter: RecordAdapter
    private lateinit var categorySpinner: Spinner
    private lateinit var inputEditText: EditText
    private lateinit var searchEditText: EditText
    // 数据
    private val categoryList = mutableListOf<Category>()
    private var selectedCategoryId = "default_category"

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

        // 初始化Room数据库
        val db = AppDatabase.getInstance(this)
        recordDao = db.recordDao()
        categoryDao = db.categoryDao()

        // 初始化控件
        initViews()
        // 初始化默认分类（首次运行）
        initDefaultCategories()
        // 初始化分类选择器
        initCategorySpinner()
        // 初始化RecyclerView列表
        initRecyclerView()
        // 加载所有记录（默认）
        loadAllRecords()
        // 设置按钮点击事件
        setButtonClickListeners()
    }

    // 初始化所有控件（移除局部函数private修饰符，Kotlin局部函数不能加private）
    fun initViews() {
        categorySpinner = findViewById(R.id.categorySpinner)
        inputEditText = findViewById(R.id.inputEditText)
        searchEditText = findViewById(R.id.searchEditText)
    }

    // 初始化默认分类
    fun initDefaultCategories() {
        lifecycleScope.launch {
            val categories = categoryDao.getAllCategories().first()
            if (categories.isEmpty()) {
                // 添加默认分类
                val defaultCategories = listOf(
                    Category("default_category", "默认分类"),
                    Category("study", "学习笔记"),
                    Category("life", "生活记录"),
                    Category("work", "工作备忘")
                )
                defaultCategories.forEach { categoryDao.insertCategory(it) }
                categoryList.addAll(defaultCategories)
            } else {
                categoryList.addAll(categories)
            }
        }
    }

    // 初始化分类Spinner
    fun initCategorySpinner() {
        lifecycleScope.launch {
            categoryDao.getAllCategories().collect { categories ->
                categoryList.clear()
                categoryList.addAll(categories)
                // 设置Spinner适配器
                val adapter = ArrayAdapter(
                    this@MainActivity,
                    android.R.layout.simple_spinner_item,
                    categoryList.map { it.categoryName }
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                categorySpinner.adapter = adapter
            }
        }
    }

    // 初始化RecyclerView
    fun initRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.historyRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        // 初始化适配器（含删除、编辑回调）
        recordAdapter = RecordAdapter(
            onDeleteClick = { record ->
                lifecycleScope.launch {
                    recordDao.deleteRecord(record)
                    Toast.makeText(this@MainActivity, "删除成功", Toast.LENGTH_SHORT).show()
                }
            },
            onEditClick = { record ->
                // 弹出编辑对话框
                val editText = EditText(this@MainActivity)
                editText.setText(record.content)
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("编辑记录")
                    .setView(editText)
                    .setPositiveButton("保存") { _, _ ->
                        val newContent = editText.text.toString().trim()
                        if (newContent.isNotEmpty()) {
                            val updatedRecord = record.copy(content = newContent)
                            lifecycleScope.launch {
                                recordDao.updateRecord(updatedRecord)
                                Toast.makeText(this@MainActivity, "编辑成功", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        )
        recyclerView.adapter = recordAdapter
    }

    // 设置所有按钮点击事件
    fun setButtonClickListeners() {
        // 提交按钮
        findViewById<Button>(R.id.submitButton).setOnClickListener {
            val inputText = inputEditText.text.toString().trim()
            if (inputText.isEmpty()) {
                Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 获取选中的分类
            val selectedCategory = categoryList[categorySpinner.selectedItemPosition]
            // 创建新记录
            val newRecord = Record(
                id = UUID.randomUUID().toString(),
                content = inputText,
                time = System.currentTimeMillis(),
                categoryId = selectedCategory.categoryId
            )

            // 插入数据库
            lifecycleScope.launch {
                recordDao.insertRecord(newRecord)
                val allRecords = recordDao.getAllRecords().first()
                Log.d("RoomTest", "当前记录数：${allRecords.size}")
            }

            // 跳转第二页
            val intent = Intent(this, SecondActivity::class.java)
            intent.putExtra("input_data", inputText)
            startActivity(intent)
            inputEditText.text.clear()
        }

        // 分类筛选按钮
        findViewById<Button>(R.id.filterButton).setOnClickListener {
            selectedCategoryId = categoryList[categorySpinner.selectedItemPosition].categoryId
            loadRecordsByCategory()
        }

        // 搜索按钮
        findViewById<Button>(R.id.searchButton).setOnClickListener {
            val keyword = searchEditText.text.toString().trim()
            if (keyword.isEmpty()) {
                loadAllRecords()
                Toast.makeText(this, "请输入搜索关键词", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 搜索记录
            lifecycleScope.launch {
                recordDao.searchRecords(keyword).collect { records ->
                    recordAdapter.submitList(records)
                    if (records.isEmpty()) {
                        Toast.makeText(this@MainActivity, "未找到相关记录", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        // 清空按钮
        findViewById<Button>(R.id.clearButton).setOnClickListener {
            lifecycleScope.launch {
                recordDao.deleteAllRecords()
                Toast.makeText(this@MainActivity, "已清空所有历史", Toast.LENGTH_SHORT).show()
            }
        }

        // 导出按钮
        findViewById<Button>(R.id.exportButton).setOnClickListener {
            // 检查存储权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    REQUEST_STORAGE_PERMISSION
                )
            } else {
                exportRecordsToTxt()
            }
        }
    }

    // 加载所有记录
    fun loadAllRecords() {
        lifecycleScope.launch {
            recordDao.getAllRecords().collect { records ->
                recordAdapter.submitList(records)
            }
        }
    }

    // 按分类加载记录
    fun loadRecordsByCategory() {
        lifecycleScope.launch {
            categoryDao.getRecordsByCategory(selectedCategoryId).collect { records ->
                recordAdapter.submitList(records)
            }
        }
    }

    // 导出记录为TXT文件
    fun exportRecordsToTxt() {
        lifecycleScope.launch {
            val records = recordDao.getAllRecords().first()
            if (records.isEmpty()) {
                Toast.makeText(this@MainActivity, "暂无记录可导出", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // 构建导出内容
            val sb = StringBuilder()
            sb.append("学习笔记记录导出 - ${Date(System.currentTimeMillis())}\n")
            sb.append("==============================\n\n")
            records.forEachIndexed { index, record ->
                val categoryName = categoryList.find { it.categoryId == record.categoryId }?.categoryName ?: "未知分类"
                val timeStr = Date(record.time).toString()
                sb.append("${index + 1}. 分类：$categoryName\n")
                sb.append("   内容：${record.content}\n")
                sb.append("   时间：$timeStr\n\n")
            }

            // 保存到下载目录
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadDir, "笔记记录_${System.currentTimeMillis()}.txt")
            try {
                file.writeText(sb.toString(), Charsets.UTF_8)
                Toast.makeText(
                    this@MainActivity,
                    "导出成功！路径：${file.absolutePath}",
                    Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "导出失败：${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // 权限请求回调
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportRecordsToTxt()
            } else {
                Toast.makeText(this, "需要存储权限才能导出文件", Toast.LENGTH_SHORT).show()
            }
        }
    }
}