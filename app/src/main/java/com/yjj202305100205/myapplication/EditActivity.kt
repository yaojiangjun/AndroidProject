package com.yjj202305100205.myapplication

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
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
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.Base64
import java.util.concurrent.TimeUnit

import java.io.BufferedInputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class EditActivity : AppCompatActivity() {
    // 控件（修复空指针：OCR按钮改为可空）
    private lateinit var inputEditText: TextInputEditText
    private lateinit var noteImageIv: ImageView
    private lateinit var noImageHintTv: TextView
    private lateinit var deleteImageIv: ImageView
    private lateinit var categorySpinner: Spinner
    private lateinit var addImageButton: Button
    private lateinit var cancelButton: Button
    private lateinit var saveButton: Button
    private var ocrImageButton: Button? = null // 可空类型，避免闪退

    // 数据（修复图片显示：存储Uri字符串）
    private var currentRecord: Record? = null
    private var selectedImagePath: String? = null // 存储Uri字符串，而非真实路径
    private val categoryList = mutableListOf<Category>()
    private var selectedImageUri: Uri? = null // OCR用的Uri

    // 数据库
    private lateinit var recordDao: RecordDao
    private lateinit var categoryDao: CategoryDao

    // 图片选择器
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>

    // 百度云OCR配置（替换为你的API Key/Secret Key）
    private val BAIDU_API_KEY = "IEPGGn1VVpPcFzjDWwUjSMev"
    private val BAIDU_SECRET_KEY = "LeQuNw20ReAl7mP1PsQEE07nmNK260uc"
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    private val gson = Gson()

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

        // 初始化百度云OCR按钮
        initOcrButton()

        // 获取传递的记录
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

    // 初始化控件（修复空指针：绑定OCR按钮）
    private fun initViews() {
        inputEditText = findViewById(R.id.inputEditText)
        noteImageIv = findViewById(R.id.noteImageIv)
        noImageHintTv = findViewById(R.id.noImageHintTv)
        deleteImageIv = findViewById(R.id.deleteImageIv)
        categorySpinner = findViewById(R.id.categorySpinner)
        addImageButton = findViewById(R.id.addImageButton)
        cancelButton = findViewById(R.id.cancelButton)
        saveButton = findViewById(R.id.saveButton)
        ocrImageButton = findViewById(R.id.ocrImageButton) // 布局已添加，不会null

        // 原有按钮逻辑保留
        cancelButton.setOnClickListener { finish() }
        saveButton.setOnClickListener { saveEditedRecord() }
        addImageButton.setOnClickListener { checkImagePermissionAndPick() }
        deleteImageIv.setOnClickListener {
            selectedImagePath = null
            selectedImageUri = null
            ocrImageButton?.isEnabled = false
            updateImageDisplay()
        }
    }

    // 初始化图片选择器（修复图片显示：存储Uri字符串）
    private fun initImagePicker() {
        pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                uri?.let {
                    // 1. 强制赋值（确保Uri不为空）
                    selectedImagePath = it.toString()
                    selectedImageUri = it
                    // 2. 打印日志（验证Uri是否获取到）
                    Log.d("OCR_DEBUG", "选择图片成功，Uri：$it")
                    // 3. 刷新图片显示
                    updateImageDisplay()
                    // 4. 强制启用按钮（兜底，不管之前状态）
                    ocrImageButton?.isEnabled = true
                    // 额外验证：弹提示确认代码执行到这里
                    Toast.makeText(this, "图片选择成功，可识别文字", Toast.LENGTH_SHORT).show()
                } ?: run {
                    Log.d("OCR_DEBUG", "图片Uri为空")
                    Toast.makeText(this, "图片Uri获取失败", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.d("OCR_DEBUG", "图片选择取消/失败，resultCode：${result.resultCode}")
            }
        }
    }

    // 回显数据（修复图片显示：解析Uri）
    private fun showRecordData(record: Record) {
        inputEditText.setText(record.content)
        selectedImagePath = record.imagePath
        selectedImageUri = if (!selectedImagePath.isNullOrEmpty()) {
            Uri.parse(selectedImagePath)
        } else {
            null
        }
        ocrImageButton?.isEnabled = selectedImageUri != null
        updateImageDisplay()
    }

    // 更新图片显示（修复图片显示：加载Uri）
    private fun updateImageDisplay() {
        if (!selectedImagePath.isNullOrEmpty()) {
            noteImageIv.visibility = View.VISIBLE
            noImageHintTv.visibility = View.GONE
            deleteImageIv.visibility = View.VISIBLE
            Glide.with(this).load(Uri.parse(selectedImagePath)).into(noteImageIv)
        } else {
            noteImageIv.visibility = View.GONE
            noImageHintTv.visibility = View.VISIBLE
            deleteImageIv.visibility = View.GONE
        }
    }

    // 初始化百度云OCR按钮
    private fun initOcrButton() {
        ocrImageButton?.isEnabled = false // 初始禁用
        ocrImageButton?.setOnClickListener {
            selectedImageUri?.let { uri ->
                // 协程执行OCR（网络请求需在子线程）
                lifecycleScope.launch(Dispatchers.IO) {
                    val ocrResult = recognizeTextWithBaiduOCR(uri)
                    // 切回主线程更新UI
                    withContext(Dispatchers.Main) {
                        if (ocrResult.isNotEmpty()) {
                            inputEditText.append("\n$ocrResult")
                            Toast.makeText(this@EditActivity, "OCR识别完成！", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@EditActivity, "未识别到文字", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } ?: run {
                Toast.makeText(this, "请先选择图片", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------------------- 百度云OCR核心逻辑 ----------------------
    /**
     * 调用百度云通用文字识别API
     */
    private suspend fun recognizeTextWithBaiduOCR(uri: Uri): String {
        // 日志标记
        val TAG = "BAIDU_OCR"
        Log.d(TAG, "开始调用百度OCR，图片Uri：$uri")

        // 步骤1：获取百度云AccessToken
        val accessToken = getBaiduAccessToken()
        Log.d(TAG, "AccessToken获取结果：${if (accessToken.isNullOrEmpty()) "失败" else "成功"}")
        if (accessToken.isNullOrEmpty()) {
            Log.e(TAG, "AccessToken为空，OCR调用终止")
            return ""
        }

        // 步骤2：将图片Uri转为Base64编码（已修复数据流读取问题）
        val imageBase64 = imageUriToBase64(uri)
        Log.d(TAG, "图片Base64编码结果：${if (imageBase64.isNullOrEmpty()) "失败" else "成功（长度：${imageBase64.length}）"}")
        if (imageBase64.isNullOrEmpty()) {
            Log.e(TAG, "图片Base64编码失败，OCR调用终止")
            return ""
        }

        // 步骤3：构建百度OCR API请求（核心修复：Base64 URL编码）
        val ocrApiUrl = "https://aip.baidubce.com/rest/2.0/ocr/v1/general_basic?access_token=$accessToken"
        // 关键：对Base64字符串做URL编码（解决216201 image format error）
        val encodedImageBase64 = URLEncoder.encode(imageBase64, StandardCharsets.UTF_8.name())
        // 构建请求体（application/x-www-form-urlencoded格式）
        val requestBody = "image=$encodedImageBase64".toRequestBody(
            "application/x-www-form-urlencoded".toMediaTypeOrNull()
        )
        // 构建OkHttp请求
        val request = Request.Builder()
            .url(ocrApiUrl)
            .post(requestBody)
            .build()

        // 步骤4：发送请求并处理响应
        return try {
            Log.d(TAG, "发送OCR请求，API地址：$ocrApiUrl")
            val response = okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string()

            Log.d(TAG, "OCR响应状态码：${response.code}，响应内容：$responseBody")

            // 响应成功且有返回内容
            if (response.isSuccessful && !responseBody.isNullOrEmpty()) {
                // 解析JSON响应
                val responseMap = gson.fromJson(responseBody, Map::class.java)

                // 检查是否有错误码（百度OCR返回错误）
                val errorCode = responseMap["error_code"]
                if (errorCode != null) {
                    val errorMsg = responseMap["error_msg"] ?: "未知错误"
                    Log.e(TAG, "百度OCR返回错误：$errorMsg（错误码：$errorCode）")
                    return ""
                }

                // 解析识别结果
                val wordsResult = responseMap["words_result"] as? List<Map<String, String>> ?: emptyList()
                Log.d(TAG, "识别到文字块数量：${wordsResult.size}")

                // 拼接所有文字
                val resultBuilder = StringBuilder()
                for (wordItem in wordsResult) {
                    val word = wordItem["words"] ?: ""
                    Log.d(TAG, "识别到单段文字：$word")
                    resultBuilder.append(word)
                }

                val finalResult = resultBuilder.toString()
                Log.d(TAG, "OCR识别最终结果：$finalResult")
                return finalResult
            } else {
                // 响应失败
                Log.e(TAG, "OCR请求失败，响应码：${response.code}，返回内容：$responseBody")
                ""
            }
        } catch (e: Exception) {
            // 捕获所有异常（网络/解析等）
            Log.e(TAG, "OCR请求异常：${e.message}", e)
            ""
        }
    }

    /**
     * 辅助方法：获取百度云AccessToken（完整版）
     */
    private suspend fun getBaiduAccessToken(): String? {
        val TAG = "BAIDU_OCR"
        val tokenUrl = "https://aip.baidubce.com/oauth/2.0/token?grant_type=client_credentials&client_id=$BAIDU_API_KEY&client_secret=$BAIDU_SECRET_KEY"

        return try {
            val request = Request.Builder().url(tokenUrl).build()
            val response = okHttpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                val tokenMap = gson.fromJson(responseBody, Map::class.java)
                tokenMap["access_token"]?.toString()
            } else {
                Log.e(TAG, "获取AccessToken失败，响应码：${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取AccessToken异常：${e.message}", e)
            null
        }
    }

    /**
     * 辅助方法：图片Uri转Base64（兼容所有Android版本，修复数据流读取问题）
     */
    private fun imageUriToBase64(uri: Uri): String? {
        val TAG = "BAIDU_OCR"
        var inputStream: InputStream? = null
        var bufferedInputStream: BufferedInputStream? = null
        val outputStream = ByteArrayOutputStream()

        try {
            // 打开图片数据流（兼容Android 10+ Uri访问规则）
            inputStream = contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "无法打开图片数据流，Uri：$uri")
                return null
            }
            bufferedInputStream = BufferedInputStream(inputStream)

            // 分块读取图片数据（避免内存溢出）
            val buffer = ByteArray(1024)
            var bytesRead: Int
            while (bufferedInputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }

            // 转换为字节数组并编码为Base64
            val imageBytes = outputStream.toByteArray()
            Log.d(TAG, "图片读取成功，字节数：${imageBytes.size}")

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Base64.getEncoder().encodeToString(imageBytes)
            } else {
                android.util.Base64.encodeToString(imageBytes, android.util.Base64.NO_WRAP)
            }
        } catch (e: IOException) {
            Log.e(TAG, "读取图片数据流失败：${e.message}", e)
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Base64编码异常：${e.message}", e)
            return null
        } finally {
            // 关闭所有数据流（避免内存泄漏）
            try {
                outputStream.close()
                bufferedInputStream?.close()
                inputStream?.close()
            } catch (e: IOException) {
                Log.e(TAG, "关闭数据流失败：${e.message}", e)
            }
        }
    }

    // ---------------------- 原有方法保留不变 ----------------------
    private fun loadCategories() {
        lifecycleScope.launch {
            categoryDao.getAllCategories().collect { categories ->
                categoryList.clear()
                categoryList.addAll(categories)
                val adapter = ArrayAdapter(
                    this@EditActivity,
                    android.R.layout.simple_spinner_item,
                    categoryList.map { it.categoryName }
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                categorySpinner.adapter = adapter
                currentRecord?.let { record ->
                    val position = categoryList.indexOfFirst { it.categoryId == record.categoryId }
                    if (position != -1) {
                        categorySpinner.setSelection(position)
                    }
                }
            }
        }
    }

    private fun saveEditedRecord() {
        val content = inputEditText.text.toString().trim()
        if (content.isEmpty()) {
            Toast.makeText(this, "请输入笔记内容", Toast.LENGTH_SHORT).show()
            return
        }
        val selectedCategory = categoryList[categorySpinner.selectedItemPosition]
        val updatedRecord = currentRecord!!.copy(
            content = content,
            time = System.currentTimeMillis(),
            categoryId = selectedCategory.categoryId,
            imagePath = selectedImagePath
        )
        lifecycleScope.launch {
            recordDao.updateRecord(updatedRecord)
            Toast.makeText(this@EditActivity, "保存成功", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun checkImagePermissionAndPick() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_IMAGES
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            pickImage()
        } else {
            requestPermissions(arrayOf(permission), 1001)
        }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        pickImageLauncher.launch(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            pickImage()
        } else {
            Toast.makeText(this, "需要存储权限才能选择图片", Toast.LENGTH_SHORT).show()
        }
    }
}