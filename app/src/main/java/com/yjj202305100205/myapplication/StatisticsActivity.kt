package com.yjj202305100205.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch

class StatisticsActivity : AppCompatActivity() {
    private lateinit var recordDao: RecordDao
    private lateinit var categoryCountAdapter: CategoryCountAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        // 初始化Room
        val db = AppDatabase.getInstance(this)
        recordDao = db.recordDao()

        // 初始化列表（修复控件ID引用：确保布局中RecyclerView的id是categoryRv）
        val categoryRv = findViewById<RecyclerView>(R.id.categoryRv)
        categoryRv.layoutManager = LinearLayoutManager(this)
        categoryCountAdapter = CategoryCountAdapter()
        categoryRv.adapter = categoryCountAdapter

        // 加载总记录数（修复控件ID引用：确保布局中TextView的id是totalCountTv）
        loadTotalCount()

        // 加载分类统计
        loadCategoryCount()

        // 返回按钮
        findViewById<Button>(R.id.backButton).setOnClickListener {
            finish()
        }
    }

    // 加载总记录数（修复字符串拼接）
    private fun loadTotalCount() {
        lifecycleScope.launch {
            val total = recordDao.getTotalRecordCount()
            findViewById<TextView>(R.id.totalCountTv).text = getString(R.string.total_count, total)
        }
    }

    // 加载分类统计
    private fun loadCategoryCount() {
        lifecycleScope.launch {
            recordDao.getCategoryCount().collect { list ->
                categoryCountAdapter.submitList(list)
            }
        }
    }

    // 分类统计适配器
    inner class CategoryCountAdapter : RecyclerView.Adapter<CategoryCountAdapter.ViewHolder>() {
        private var countList = emptyList<CategoryCount>()

        fun submitList(newList: List<CategoryCount>) {
            countList = newList
            notifyDataSetChanged()
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val nameTv: TextView = itemView.findViewById(R.id.categoryNameTv)
            private val countTv: TextView = itemView.findViewById(R.id.categoryCountTv)
            private val ratioTv: TextView = itemView.findViewById(R.id.categoryRatioTv)

            fun bind(item: CategoryCount) {
                nameTv.text = item.categoryName
                countTv.text = getString(R.string.count_format, item.count)
                ratioTv.text = if (item.count == 0) "0%" else "${item.count}%"
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category_count, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(countList[position])
        }

        override fun getItemCount(): Int = countList.size
    }
}