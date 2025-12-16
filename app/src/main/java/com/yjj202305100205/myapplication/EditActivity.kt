package com.yjj202305100205.myapplication

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

class EditActivity : AppCompatActivity() {
    // 控件
    private lateinit var inputEditText: TextInputEditText
    private lateinit var noteImageIv: ImageView
    private lateinit var noImageHintTv: TextView
    private lateinit var deleteImageIv: ImageView
    private lateinit var categorySpinner: Spinner
    private lateinit var addImageButton: Button
    private lateinit var cancelButton: Button
    private lateinit var saveButton: Button

    // 数据
    private var currentRecord: Record? = null
    private var selectedImagePath: String? = null
    private val categoryList = mutableListOf<Category>()

    // 数据库
    private lateinit var recordDao: RecordDao
    private lateinit var categoryDao: CategoryDao

    // 图片选择器
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        // 初始化数据库
        val db = AppDatabase.getInstance(this) ?: run {
            Toast.makeText(this, "数据库初始化失败", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        recordDao = db.recordDao()
        categoryDao = db.categoryDao()

        // 初始化控件
        initViews()

        // 初始化图片选择器
        initImagePicker()

        // 获取传递的记录（必须非空）
        currentRecord = intent.getParcelableExtra("record")
        if (currentRecord == null) {
            Toast.makeText(this, "无法获取笔记数据", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 加载分类和回显数据
        loadCategories()
        showRecordData(currentRecord!!)
    }

    // 初始化控件
    private fun initViews() {
        inputEditText = findViewById(R.id.inputEditText)
        noteImageIv = findViewById(R.id.noteImageIv)
        noImageHintTv = findViewById(R.id.noImageHintTv)
        deleteImageIv = findViewById(R.id.deleteImageIv)
        categorySpinner = findViewById(R.id.categorySpinner)
        addImageButton = findViewById(R.id.addImageButton)
        cancelButton = findViewById(R.id.cancelButton)
        saveButton = findViewById(R.id.saveButton)

        // 取消按钮
        cancelButton.setOnClickListener { finish() }

        // 保存按钮
        saveButton.setOnClickListener { saveEditedRecord() }

        // 添加图片按钮
        addImageButton.setOnClickListener { checkImagePermissionAndPick() }

        // 删除图片按钮
        deleteImageIv.setOnClickListener {
            selectedImagePath = null
            updateImageDisplay()
        }
    }

    // 初始化图片选择器
    private fun initImagePicker() {
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                uri?.let {
                    selectedImagePath = getRealPathFromUri(it)
                    updateImageDisplay() // 刷新图片显示
                }
            }
        }
    }

    // 回显原笔记数据
    private fun showRecordData(record: Record) {
        // 显示文字内容
        inputEditText.setText(record.content)

        // 显示图片
        selectedImagePath = record.imagePath
        updateImageDisplay()
    }

    // 更新图片显示状态
    private fun updateImageDisplay() {
        if (!selectedImagePath.isNullOrEmpty() && File(selectedImagePath!!).exists()) {
            // 有图片
            noteImageIv.visibility = View.VISIBLE
            noImageHintTv.visibility = View.GONE
            deleteImageIv.visibility = View.VISIBLE
            Glide.with(this).load(selectedImagePath).into(noteImageIv)
        } else {
            // 无图片
            noteImageIv.visibility = View.GONE
            noImageHintTv.visibility = View.VISIBLE
            deleteImageIv.visibility = View.GONE
        }
    }

    // 加载分类列表
    // 加载分类列表
    private fun loadCategories() {
        lifecycleScope.launch {
            // 使用 collect 收集 Flow 中的数据
            categoryDao.getAllCategories().collect { categories ->
                categoryList.clear()
                categoryList.addAll(categories) // 此时 categories 是 List<Category> 类型

                // 设置分类适配器
                val adapter = ArrayAdapter(
                    this@EditActivity,
                    android.R.layout.simple_spinner_item,
                    categoryList.map { it.categoryName }
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                categorySpinner.adapter = adapter

                // 选中当前记录的分类
                currentRecord?.let { record ->
                    val position = categoryList.indexOfFirst { it.categoryId == record.categoryId }
                    if (position != -1) {
                        categorySpinner.setSelection(position)
                    }
                }
            }
        }
    }

    // 保存编辑后的记录
    private fun saveEditedRecord() {
        val content = inputEditText.text.toString().trim()
        if (content.isEmpty()) {
            Toast.makeText(this, "请输入笔记内容", Toast.LENGTH_SHORT).show()
            return
        }

        val selectedCategory = categoryList[categorySpinner.selectedItemPosition]
        val updatedRecord = currentRecord!!.copy(
            content = content,
            time = System.currentTimeMillis(), // 更新时间为当前时间
            categoryId = selectedCategory.categoryId,
            imagePath = selectedImagePath
        )

        lifecycleScope.launch {
            recordDao.updateRecord(updatedRecord)
            Toast.makeText(this@EditActivity, "保存成功", Toast.LENGTH_SHORT).show()
            finish() // 保存后关闭页面
        }
    }

    // 检查图片权限并选择图片
    private fun checkImagePermissionAndPick() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            pickImage() // 已有权限，直接选择图片
        } else {
            // 请求权限
            requestPermissions(arrayOf(permission), 1001)
        }
    }

    // 选择图片
    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    // 处理权限请求结果
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pickImage() // 权限通过，选择图片
        } else {
            Toast.makeText(this, "需要存储权限才能选择图片", Toast.LENGTH_SHORT).show()
        }
    }

    // 获取图片真实路径
    private fun getRealPathFromUri(uri: Uri): String? {
        var path: String? = null
        val projection = arrayOf(MediaStore.Images.Media.DATA)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
                path = cursor.getString(columnIndex)
            }
        }
        return path
    }
}