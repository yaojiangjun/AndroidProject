package com.yjj202305100205.myapplication

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
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
import android.view.LayoutInflater
import androidx.activity.result.ActivityResultLauncher


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
    // 数据
    val categoryList = mutableListOf<Category>() // 供Adapter访问
    private var selectedCategoryId = "default_category"
    private var selectedImagePath: String? = null // 选中的图片路径
    // 图片选择器
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>

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
        //排序
        initSortButton()
    }



    private fun initImagePicker() {
        // 使用旧版契约处理图片选择
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.data
                uri?.let {
                    selectedImagePath = getRealPathFromUri(it)
                    Toast.makeText(this, "图片已选择", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 初始化所有控件
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
        recordAdapter = RecordAdapter(
            onDeleteClick = { record ->
                lifecycleScope.launch {
                    recordDao.deleteRecord(record)
                    Toast.makeText(this@MainActivity, "删除成功", Toast.LENGTH_SHORT).show()
                }
            },
            onEditClick = { record ->
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
            },
            onTopClick = { record ->
                val updatedRecord = record.copy(isTop = !record.isTop)
                lifecycleScope.launch {
                    recordDao.updateRecord(updatedRecord)
                    val tip = if (updatedRecord.isTop) "置顶成功" else "取消置顶成功"
                    Toast.makeText(this@MainActivity, tip, Toast.LENGTH_SHORT).show()
                }
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
                val allRecords = recordDao.getAllRecords().first()
                Log.d("RoomTest", "当前记录数：${allRecords.size}")
            }

            val intent = Intent(this, SecondActivity::class.java)
            intent.putExtra("input_data", inputText)
            startActivity(intent)
            inputEditText.text.clear()
            selectedImagePath = null
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
            if (keyword.isEmpty()) {
                loadAllRecords()
                Toast.makeText(this, "请输入搜索关键词", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10及以上不需要存储权限
                exportRecordsToTxt()
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
                ) {
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

    // 修改 checkImagePermissionAndPick() 中的打开相册部分
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

        // 改用 ACTION_PICK 意图打开相册
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        pickImageLauncher.launch(intent)
    }


    // 工具方法：Uri转文件路径
    private fun getRealPathFromUri(uri: Uri): String? {
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        val cursor: Cursor? = contentResolver.query(uri, projection, null, null, null)
        return cursor?.let {
            val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
            it.moveToFirst()
            val path = it.getString(columnIndex)
            it.close()
            path
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

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
            val sb = StringBuilder()
            sb.append("我的笔记导出 - ${sdf.format(Date())}\n")
            sb.append("==============================\n\n")

            // 在循环内部添加topStr的定义
            records.forEachIndexed { index, record ->
                val categoryName = this@MainActivity.categoryList
                    .find { it.categoryId == record.categoryId }?.categoryName ?: "未分类"
                val timeStr = sdf.format(Date(record.time))
                // 修复：定义topStr变量，判断记录是否置顶
                val topStr = if (record.isTop) "【置顶】" else ""  // 假设你的Record类有isTop属性
                val imageStr = if (!(record.imagePath.isNullOrEmpty())) "（含图片）" else ""

                sb.append("${index + 1}. ${topStr}分类：${categoryName}${imageStr}\n")
                sb.append("   内容：${record.content}\n")
                sb.append("   时间：${timeStr}\n\n")
            }

            // 保存文件
            try {
                val fileName = "笔记记录_${System.currentTimeMillis()}.txt"
                val file = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10及以上使用媒体目录
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
                        "$fileName 已保存到文档/我的笔记目录"
                    } ?: "导出失败"
                } else {
                    // Android 10以下使用传统方式
                    val directory = File(Environment.getExternalStorageDirectory(), "我的笔记")
                    if (!directory.exists()) directory.mkdirs()
                    val file = File(directory, fileName)
                    file.writeText(sb.toString(), Charsets.UTF_8)
                    "$fileName 已保存到SD卡/我的笔记目录"
                }
                Toast.makeText(this@MainActivity, file, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    "导出失败：${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    // MainActivity 中添加排序逻辑
    private var isSortByNewest = true // 默认最新在前

    private fun initSortButton() {
        findViewById<Button>(R.id.sortButton).setOnClickListener {
            // 添加日志：打印切换前的状态
            Log.d("SortDebug", "切换前：isSortByNewest=$isSortByNewest")

            isSortByNewest = !isSortByNewest // 取反

            // 添加日志：打印切换后的状态
            Log.d("SortDebug", "切换后：isSortByNewest=$isSortByNewest")

            loadRecordsWithSort()
        }
    }

    // 带排序的查询
    private fun loadRecordsWithSort() {
        lifecycleScope.launch {
            val sortedRecords = if (isSortByNewest) {
                recordDao.getAllSortedByTimeDesc() // 降序（最新在前）
            } else {
                recordDao.getAllSortedByTimeAsc() // 升序（最早在前）
            }
            recordAdapter.submitList(sortedRecords)
        }
    }

    // 权限请求回调
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

// 记录适配器
class RecordAdapter(
    private val onDeleteClick: (Record) -> Unit,
    private val onEditClick: (Record) -> Unit,
    private val onTopClick: (Record) -> Unit
) : RecyclerView.Adapter<RecordAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    private var records = emptyList<Record>()

    fun submitList(newList: List<Record>) {
        records = newList
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val topTagTv: TextView = itemView.findViewById(R.id.topTagTextView)
        val noteImageIv: ImageView = itemView.findViewById(R.id.noteImageIv)
        val categoryTagTv: TextView = itemView.findViewById(R.id.categoryTagTextView)
        val contentTv: TextView = itemView.findViewById(R.id.contentTextView)
        val timeTv: TextView = itemView.findViewById(R.id.timeTextView)
        val topBtn: Button = itemView.findViewById(R.id.topButton)
        val editBtn: Button = itemView.findViewById(R.id.editButton)
        val deleteBtn: Button = itemView.findViewById(R.id.deleteButton)

        fun bind(record: Record, categoryName: String) {
            // 置顶标识
            if (record.isTop) {
                topTagTv.visibility = View.VISIBLE
                topBtn.text = itemView.context.getString(R.string.cancel_top)
            } else {
                topTagTv.visibility = View.GONE
                topBtn.text = itemView.context.getString(R.string.top)
            }

            // 图片显示
            if (!record.imagePath.isNullOrEmpty()) {
                noteImageIv.visibility = View.VISIBLE
                noteImageIv.setImageURI(Uri.parse(record.imagePath))
            } else {
                noteImageIv.visibility = View.GONE
            }

            // 分类+内容+时间
            categoryTagTv.text = "分类：$categoryName"
            contentTv.text = record.content
            timeTv.text = dateFormat.format(Date(record.time))

            // 点击事件
            topBtn.setOnClickListener { onTopClick(record) }
            editBtn.setOnClickListener { onEditClick(record) }
            deleteBtn.setOnClickListener { onDeleteClick(record) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_record, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val record = records[position]
        val context = holder.itemView.context
        val categoryName = if (context is MainActivity) {
            context.categoryList.find { it.categoryId == record.categoryId }?.categoryName ?: "未知分类"
        } else {
            "未知分类"
        }
        holder.bind(record, categoryName)
    }

    override fun getItemCount(): Int = records.size

}