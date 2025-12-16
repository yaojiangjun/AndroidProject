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
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

class EditActivity : AppCompatActivity() {
    // 控件定义（与布局ID严格一致）
    private lateinit var inputEditText: TextInputEditText
    private lateinit var noteImageIv: ImageView
    private lateinit var noImageHintTv: TextView
    private lateinit var deleteImageIv: ImageView
    private lateinit var categorySpinner: Spinner
    private lateinit var addImageButton: Button
    private lateinit var cancelButton: Button
    private lateinit var saveButton: Button

    // 其他变量...
    private var currentRecord: Record? = null
    private var selectedImagePath: String? = null
    private lateinit var recordDao: RecordDao
    private lateinit var categoryDao: CategoryDao
    private lateinit var categoryList: MutableList<Category>
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit)

        // 初始化数据库
        val db = AppDatabase.getInstance(this)
        recordDao = db.recordDao()
        categoryDao = db.categoryDao()
        categoryList = mutableListOf()

        // 初始化控件（通过findViewById绑定，与布局ID一致）
        initViews()

        // 初始化图片选择器
        initImagePicker()

        // 获取传递的记录数据
        currentRecord = intent.getParcelableExtra("record")
        currentRecord?.let { showRecordData(it) }

        // 加载分类数据
        loadCategories()
    }

    private fun initViews() {
        // 绑定控件（ID必须与布局中完全一致）
        inputEditText = findViewById(R.id.inputEditText)
        noteImageIv = findViewById(R.id.noteImageIv)
        noImageHintTv = findViewById(R.id.noImageHintTv)
        deleteImageIv = findViewById(R.id.deleteImageIv)
        categorySpinner = findViewById(R.id.categorySpinner)
        addImageButton = findViewById(R.id.addImageButton)
        cancelButton = findViewById(R.id.cancelButton)
        saveButton = findViewById(R.id.saveButton)

        // 取消按钮点击事件
        cancelButton.setOnClickListener { finish() }

        // 保存按钮点击事件
        saveButton.setOnClickListener { saveEditedRecord() }

        // 添加图片按钮点击事件
        addImageButton.setOnClickListener { checkImagePermissionAndPick() }

        // 删除图片按钮点击事件
        deleteImageIv.setOnClickListener {
            selectedImagePath = null
            noteImageIv.visibility = View.GONE
            deleteImageIv.visibility = View.GONE
            noImageHintTv.visibility = View.VISIBLE
        }
    }

    // 加载分类数据（修正适配器名称拼写错误：ArrayAdapter）
    private fun loadCategories() {
        lifecycleScope.launch {
            categoryDao.getAllCategories().collect { categories ->
                categoryList.clear()
                categoryList.addAll(categories)

                // 修正：ArrayAdapter（之前拼写为ArrayAdaptor）
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
                    if (position != -1) categorySpinner.setSelection(position)
                }
            }
        }
    }

    // 图片权限检查（补充必要的导入后，Build等类会生效）
    private fun checkImagePermissionAndPick() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES),
                    1001
                )
            } else {
                pickImage()
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                    1001
                )
            } else {
                pickImage()
            }
        }
    }

    // 其他方法（省略，保持原有逻辑）...
    private fun initImagePicker() { /* ... */ }
    private fun showRecordData(record: Record) { /* ... */ }
    private fun loadImageToView(imagePath: String?) { /* ... */ }
    private fun saveEditedRecord() { /* ... */ }
    private fun pickImage() { /* ... */ }
    private fun getRealPathFromUri(uri: Uri): String? { /* ... */ return TODO("Provide the return value")
    }
}