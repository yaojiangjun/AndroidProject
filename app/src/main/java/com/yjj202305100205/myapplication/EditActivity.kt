package com.yjj202305100205.myapplication

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

class EditActivity : AppCompatActivity() {
    // 控件（原有+新增OCR按钮）
    private lateinit var inputEditText: TextInputEditText
    private lateinit var noteImageIv: ImageView
    private lateinit var noImageHintTv: TextView
    private lateinit var deleteImageIv: ImageView
    private lateinit var categorySpinner: Spinner
    private lateinit var addImageButton: Button
    private lateinit var cancelButton: Button
    private lateinit var saveButton: Button
    // 新增：OCR识别按钮
    private lateinit var ocrImageButton: Button

    // 数据（原有+新增图片Uri）
    private var currentRecord: Record? = null
    private var selectedImagePath: String? = null
    private val categoryList = mutableListOf<Category>()
    // 新增：记录选中图片的Uri（OCR需要）
    private var selectedImageUri: Uri? = null

    // 数据库（原有）
    private lateinit var recordDao: RecordDao
    private lateinit var categoryDao: CategoryDao

    // 图片选择器（原有）
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        // 初始化数据库（原有）
        val db = AppDatabase.getInstance(this) ?: run {
            Toast.makeText(this, "数据库初始化失败", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        recordDao = db.recordDao()
        categoryDao = db.categoryDao()

        // 初始化控件（原有+新增OCR按钮初始化）
        initViews()

        // 初始化图片选择器（原有）
        initImagePicker()

        // 新增：初始化OCR功能
        initOcrFunction()

        // 获取传递的记录（必须非空）（原有）
        currentRecord = intent.getParcelableExtra("record")
        if (currentRecord == null) {
            Toast.makeText(this, "无法获取笔记数据", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // 加载分类和回显数据（原有）
        loadCategories()
        showRecordData(currentRecord!!)
    }

    // 初始化控件（原有+新增OCR按钮绑定）
    private fun initViews() {
        inputEditText = findViewById(R.id.inputEditText)
        noteImageIv = findViewById(R.id.noteImageIv)
        noImageHintTv = findViewById(R.id.noImageHintTv)
        deleteImageIv = findViewById(R.id.deleteImageIv)
        categorySpinner = findViewById(R.id.categorySpinner)
        addImageButton = findViewById(R.id.addImageButton)
        cancelButton = findViewById(R.id.cancelButton)
        saveButton = findViewById(R.id.saveButton)
        // 新增：绑定OCR按钮
        ocrImageButton = findViewById(R.id.ocrImageButton)

        // 取消按钮（原有）
        cancelButton.setOnClickListener { finish() }

        // 保存按钮（原有）
        saveButton.setOnClickListener { saveEditedRecord() }

        // 添加图片按钮（原有）
        addImageButton.setOnClickListener { checkImagePermissionAndPick() }

        // 删除图片按钮（原有）
        deleteImageIv.setOnClickListener {
            selectedImagePath = null
            selectedImageUri = null // 新增：清空OCR用的Uri
            updateImageDisplay()
        }
    }

    // 初始化图片选择器（原有+补充selectedImageUri赋值）
    private fun initImagePicker() {
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                uri?.let {
                    selectedImagePath = getRealPathFromUri(it)
                    // 新增：记录图片Uri（OCR需要，直接用系统返回的Uri，不转真实路径）
                    selectedImageUri = it
                    updateImageDisplay() // 刷新图片显示
                    // 新增：有图片时启用OCR按钮
                    ocrImageButton.isEnabled = true
                }
            }
        }
    }

    // 回显原笔记数据（原有+补充selectedImageUri赋值）
    private fun showRecordData(record: Record) {
        // 显示文字内容（原有）
        inputEditText.setText(record.content)

        // 显示图片（原有）
        selectedImagePath = record.imagePath
        // 新增：回显图片时，将imagePath转为Uri（供OCR使用）
        if (!selectedImagePath.isNullOrEmpty()) {
            selectedImageUri = Uri.parse(selectedImagePath)
            // 新增：编辑模式有图片时启用OCR按钮
            ocrImageButton.isEnabled = true
        } else {
            selectedImageUri = null
            ocrImageButton.isEnabled = false
        }
        updateImageDisplay()
    }

    // 更新图片显示状态（原有，无修改）
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

    // 加载分类列表（原有，无修改）
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

    // 保存编辑后的记录（原有，无修改）
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

    // 检查图片权限并选择图片（原有，无修改）
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

    // 选择图片（原有，无修改）
    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    // 处理权限请求结果（原有，无修改）
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

    // 获取图片真实路径（原有，无修改）
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

    // ---------------------- 新增OCR核心功能（完全独立，不影响原有逻辑） ----------------------
    /**
     * 初始化OCR功能
     */
    private fun initOcrFunction() {
        // 初始禁用OCR按钮（选图后启用）
        ocrImageButton.isEnabled = false
        ocrImageButton.setOnClickListener {
            selectedImageUri?.let { uri ->
                recognizeTextFromImage(uri)
            } ?: run {
                Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * 图片文字识别核心逻辑
     */
    private fun recognizeTextFromImage(uri: Uri) {
        // 显示加载提示
        Toast.makeText(this, "正在识别图片文字...", Toast.LENGTH_SHORT).show()

        try {
            // 将Uri转为Bitmap（ML Kit要求）
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            // 创建ML Kit输入图片
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            // 初始化中文文本识别器（支持中文）
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

            // 执行识别
            recognizer.process(inputImage)
                .addOnSuccessListener { textResult ->
                    // 识别成功：将文字追加到输入框
                    val extractedText = textResult.text
                    if (extractedText.isNotEmpty()) {
                        // 保留原有内容，追加识别的文字（换行分隔）
                        inputEditText.append("\n$extractedText")
                        Toast.makeText(this, "文字识别完成！", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "未识别到任何文字", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    // 识别失败提示
                    Toast.makeText(this, "识别失败：${e.message}", Toast.LENGTH_SHORT).show()
                }
                .addOnCompleteListener {
                    // 释放识别器资源
                    recognizer.close()
                }
        } catch (e: Exception) {
            // 图片读取/处理失败提示
            Toast.makeText(this, "图片处理失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}