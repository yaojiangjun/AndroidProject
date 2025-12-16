package com.yjj202305100205.myapplication

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
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
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    // 权限请求常量
    private val REQUEST_STORAGE_PERMISSION = 1001
    private val REQUEST_IMAGE_PICK = 1002

    // Room相关
    private lateinit var recordDao: RecordDao
    private lateinit var categoryDao: CategoryDao

    // 控件
    private lateinit var recordAdapter: RecordAdapter
    private lateinit var categorySpinner: Spinner
    private lateinit var inputEditText: EditText
    private lateinit var searchEditText: EditText
    private lateinit var emptyStateTv: TextView // 空状态提示

    // 数据（公开获取方法，供Adapter访问）
    private val categoryList = mutableListOf<Category>()
    private var selectedCategoryId = "default_category"
    private var selectedImagePath: String? = null // 选中的图片路径（保存Uri字符串）

    // 图片选择器
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>

    // 排序状态
    private var isSortByNewest = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 初始化图片选择器
        initImagePicker()

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
        // 初始化默认分类（避免重复插入）
        initDefaultCategories()
        // 初始化分类选择器
        initCategorySpinner()
        // 初始化RecyclerView列表（使用独立Adapter）
        initRecyclerView()
        // 加载所有记录（带排序）
        loadRecordsWithSort()
        // 设置按钮点击事件
        setButtonClickListeners()
        // 初始化排序按钮
        initSortButton()
    }

    /**
     * 供Adapter获取分类名称的方法
     */
    fun getCategoryName(categoryId: String): String {
        return categoryList.find { it.categoryId == categoryId }?.categoryName ?: "未知分类"
    }

    private fun initImagePicker() {
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.data
                uri?.let {
                    selectedImagePath = it.toString()
                    Toast.makeText(this, "图片已选择", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun initViews() {
        categorySpinner = findViewById(R.id.categorySpinner)
        inputEditText = findViewById(R.id.inputEditText)
        searchEditText = findViewById(R.id.searchEditText)
        emptyStateTv = findViewById(R.id.emptyStateTv)
    }

    fun initDefaultCategories() {
        lifecycleScope.launch {
            val existingCategories = categoryDao.getAllCategories().first()
            val existingIds = existingCategories.map { it.categoryId }.toSet()

            val defaultCategories = listOf(
                Category("default_category", "默认分类"),
                Category("study", "学习笔记"),
                Category("life", "生活记录"),
                Category("work", "工作备忘")
            ).filter { !existingIds.contains(it.categoryId) }

            if (defaultCategories.isNotEmpty()) {
                categoryDao.insertCategories(defaultCategories)
                categoryList.addAll(defaultCategories)
            } else {
                categoryList.addAll(existingCategories)
            }
        }
    }

    fun initCategorySpinner() {
        lifecycleScope.launch {
            categoryDao.getAllCategories().collect { categories ->
                categoryList.clear()
                categoryList.addAll(categories)
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

    fun initRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.historyRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recordAdapter = RecordAdapter(
            context = this,
            onDeleteClick = { record ->
                lifecycleScope.launch {
                    recordDao.deleteRecord(record)
                    Toast.makeText(this@MainActivity, "删除成功", Toast.LENGTH_SHORT).show()
                }
            },
            onEditClick = { record ->
                val intent = Intent(this@MainActivity, EditActivity::class.java)
                intent.putExtra("record", record)
                startActivity(intent)
            },
            onTopClick = { record ->
                val updatedRecord = record.copy(isTop = !record.isTop)
                lifecycleScope.launch {
                    recordDao.updateRecord(updatedRecord)
                    val tip = if (updatedRecord.isTop) "置顶成功" else "取消置顶成功"
                    Toast.makeText(this@MainActivity, tip, Toast.LENGTH_SHORT).show()
                }
            },
            onItemClick = { record ->
                val intent = Intent(this@MainActivity, DetailActivity::class.java)
                intent.putExtra("record", record)
                startActivity(intent)
            }
        )
        recyclerView.adapter = recordAdapter
    }

    fun setButtonClickListeners() {
        // 提交按钮
        findViewById<Button>(R.id.submitButton).setOnClickListener {
            val inputText = inputEditText.text.toString().trim()
            if (inputText.isEmpty()) {
                Toast.makeText(this, "请输入内容", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val selectedCategory = categoryList[categorySpinner.selectedItemPosition]
            val newRecord = Record(
                id = UUID.randomUUID().toString(),
                content = inputText,
                time = System.currentTimeMillis(),
                categoryId = selectedCategory.categoryId,
                imagePath = selectedImagePath,
                isTop = false
            )

            lifecycleScope.launch {
                recordDao.insertRecord(newRecord)
                inputEditText.text.clear()
                selectedImagePath = null
                loadRecordsWithSort()
            }
        }

        // 添加图片按钮
        findViewById<Button>(R.id.addImageButton).setOnClickListener {
            checkImagePermissionAndPick()
        }

        // 分类筛选按钮
        findViewById<Button>(R.id.filterButton).setOnClickListener {
            selectedCategoryId = categoryList[categorySpinner.selectedItemPosition].categoryId
            loadRecordsByCategory()
        }

        // 搜索按钮
        findViewById<Button>(R.id.searchButton).setOnClickListener {
            val keyword = searchEditText.text.toString().trim()
            lifecycleScope.launch {
                if (keyword.isEmpty()) {
                    loadRecordsWithSort()
                    Toast.makeText(this@MainActivity, "显示所有记录", Toast.LENGTH_SHORT).show()
                } else {
                    recordDao.searchRecords(keyword).collect { records ->
                        recordAdapter.submitList(records)
                        emptyStateTv.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
                        Toast.makeText(
                            this@MainActivity,
                            "找到${records.size}条匹配记录",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        // 清空按钮
        findViewById<Button>(R.id.clearButton).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("确认清空")
                .setMessage("确定要删除所有笔记吗？此操作不可恢复！")
                .setPositiveButton("确认") { _, _ ->
                    lifecycleScope.launch {
                        recordDao.deleteAllRecords()
                        Toast.makeText(this@MainActivity, "所有记录已清空", Toast.LENGTH_SHORT).show()
                        loadRecordsWithSort()
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // 导出按钮
        findViewById<Button>(R.id.exportButton).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportRecordsToTxt()
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    exportRecordsToTxt()
                } else {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        REQUEST_STORAGE_PERMISSION
                    )
                }
            }
        }

        // 统计按钮
        findViewById<Button>(R.id.statisticsButton).setOnClickListener {
            val intent = Intent(this, StatisticsActivity::class.java)
            startActivity(intent)
        }
    }

    private fun checkImagePermissionAndPick() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), REQUEST_IMAGE_PICK)
            return
        }

        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        pickImageLauncher.launch(intent)
    }

    fun loadAllRecords() {
        lifecycleScope.launch {
            recordDao.getAllRecords().collect { records ->
                recordAdapter.submitList(records)
                emptyStateTv.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    fun loadRecordsByCategory() {
        lifecycleScope.launch {
            categoryDao.getRecordsByCategory(selectedCategoryId).collect { records ->
                recordAdapter.submitList(records)
                emptyStateTv.visibility = if (records.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun loadRecordsWithSort() {
        lifecycleScope.launch {
            val sortedRecords = if (isSortByNewest) {
                recordDao.getAllSortedByTimeDesc().first()
            } else {
                recordDao.getAllSortedByTimeAsc().first()
            }
            recordAdapter.submitList(sortedRecords)
            emptyStateTv.visibility = if (sortedRecords.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun initSortButton() {
        val sortButton = findViewById<Button>(R.id.sortButton)
        sortButton.text = if (isSortByNewest) "最新在前" else "最早在前"

        sortButton.setOnClickListener {
            isSortByNewest = !isSortByNewest
            sortButton.text = if (isSortByNewest) "最新在前" else "最早在前"
            loadRecordsWithSort()
        }
    }

    fun exportRecordsToTxt() {
        lifecycleScope.launch {
            val records = recordDao.getAllRecords().first()
            if (records.isEmpty()) {
                Toast.makeText(this@MainActivity, "暂无记录可导出", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
            val sb = StringBuilder()
            sb.append("我的笔记导出 - ${sdf.format(Date())}\n")
            sb.append("==============================\n\n")

            records.forEachIndexed { index, record ->
                val categoryName = getCategoryName(record.categoryId)
                val timeStr = sdf.format(Date(record.time))
                val topStr = if (record.isTop) "【置顶】" else ""
                val imageStr = if (!record.imagePath.isNullOrEmpty()) "（含图片）" else ""

                sb.append("${index + 1}. ${topStr}分类：${categoryName}${imageStr}\n")
                sb.append("   内容：${record.content}\n")
                sb.append("   时间：${timeStr}\n\n")
            }

            try {
                val fileName = "笔记记录_${System.currentTimeMillis()}.txt"
                val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val resolver = contentResolver
                    val contentValues = android.content.ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS + "/我的笔记")
                    }
                    val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
                    uri?.let {
                        resolver.openOutputStream(it)?.use { outputStream ->
                            outputStream.write(sb.toString().toByteArray(Charsets.UTF_8))
                        }
                        "$fileName 已保存到：文档/我的笔记"
                    } ?: "导出失败"
                } else {
                    val directory = File(Environment.getExternalStorageDirectory(), "我的笔记")
                    if (!directory.exists()) directory.mkdirs()
                    val file = File(directory, fileName)
                    file.writeText(sb.toString(), Charsets.UTF_8)
                    "$fileName 已保存到：SD卡/我的笔记"
                }
                Toast.makeText(this@MainActivity, result, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "导出失败：${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_STORAGE_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    exportRecordsToTxt()
                } else {
                    Toast.makeText(this, "需要存储权限才能导出文件", Toast.LENGTH_SHORT).show()
                }
            }
            REQUEST_IMAGE_PICK -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    checkImagePermissionAndPick()
                } else {
                    Toast.makeText(this, "需要图片权限才能选择图片", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}