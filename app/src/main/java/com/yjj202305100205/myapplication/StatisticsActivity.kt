package com.yjj202305100205.myapplication

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// 数据类：用于存储分类统计信息（分类名称+记录数）


class StatisticsActivity : AppCompatActivity() {
    private lateinit var categoryDao: CategoryDao
    private lateinit var adapter: CategoryCountAdapter
    private var totalRecordCount = 0 // 总记录数（用于计算占比）

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_statistics)

        // 初始化数据库
        val db = AppDatabase.getInstance(this)
        categoryDao = db.categoryDao()

        // 初始化RecyclerView
        val recyclerView = findViewById<RecyclerView>(R.id.statisticsRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = CategoryCountAdapter()
        recyclerView.adapter = adapter

        // 加载统计数据
        loadStatisticsData()

        // 返回按钮点击事件
        findViewById<TextView>(R.id.backTv).setOnClickListener {
            finish() // 关闭当前页面，返回上一级
        }
    }

    /**
     * 加载分类统计数据
     */
    private fun loadStatisticsData() {
        lifecycleScope.launch {
            // 获取所有分类的记录数（从DAO层获取）
            val categoryCounts = categoryDao.getCategoryCounts().first()

            // 计算总记录数
            totalRecordCount = categoryCounts.sumOf { it.count }

            // 更新列表数据
            adapter.submitList(categoryCounts)

            // 显示总记录数
            findViewById<TextView>(R.id.totalCountTv).text = "总记录数：$totalRecordCount"

            // 空状态处理（无记录时显示）
            findViewById<TextView>(R.id.statisticsEmptyTv).visibility =
                if (totalRecordCount == 0) View.VISIBLE else View.GONE
        }
    }

    /**
     * 分类统计适配器
     */
    inner class CategoryCountAdapter : RecyclerView.Adapter<CategoryCountAdapter.ViewHolder>() {
        private var categoryCounts = emptyList<CategoryCount>()

        /**
         * 更新适配器数据
         */
        fun submitList(newList: List<CategoryCount>) {
            categoryCounts = newList
            notifyDataSetChanged()
        }

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val categoryNameTv: TextView = itemView.findViewById(R.id.categoryNameTv)
            private val countTv: TextView = itemView.findViewById(R.id.countTv)
            private val percentageTv: TextView = itemView.findViewById(R.id.percentageTv)

            /**
             * 绑定数据到列表项
             */
            fun bind(item: CategoryCount) {
                // 显示分类名称
                categoryNameTv.text = item.categoryName

                // 显示该分类的记录数
                countTv.text = "记录数：${item.count}"

                // 计算并显示占比（保留1位小数）
                val percentage = if (totalRecordCount == 0) 0.0
                else (item.count.toDouble() / totalRecordCount) * 100
                percentageTv.text = String.format("占比：%.1f%%", percentage)
            }
        }

        /**
         * 创建列表项视图
         */
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_category_count, parent, false)
            return ViewHolder(view)
        }

        /**
         * 绑定数据到视图
         */
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(categoryCounts[position])
        }

        /**
         * 获取列表项数量
         */
        override fun getItemCount(): Int = categoryCounts.size
    }
}