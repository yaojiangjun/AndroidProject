package com.yjj202305100205.myapplication

import android.content.Context
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.*

class RecordAdapter(
    private val context: Context,
    private val onDeleteClick: (Record) -> Unit,
    private val onEditClick: (Record) -> Unit,
    private val onTopClick: (Record) -> Unit,
    private val onItemClick: (Record) -> Unit
) : RecyclerView.Adapter<RecordAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
    private var records = emptyList<Record>()

    /**
     * 更新列表数据
     */
    fun submitList(newList: List<Record>) {
        records = newList
        notifyDataSetChanged()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // 列表项控件
        val topTagTv: TextView = itemView.findViewById(R.id.topTagTextView)
        val noteImageIv: ImageView = itemView.findViewById(R.id.noteImageIv)
        val categoryTagTv: TextView = itemView.findViewById(R.id.categoryTagTextView)
        val contentTv: TextView = itemView.findViewById(R.id.contentTextView)
        val timeTv: TextView = itemView.findViewById(R.id.timeTextView)
        val topBtn: Button = itemView.findViewById(R.id.topButton)
        val editBtn: Button = itemView.findViewById(R.id.editButton)
        val deleteBtn: Button = itemView.findViewById(R.id.deleteButton)

        /**
         * 绑定数据到列表项
         */
        fun bind(record: Record) {
            // 1. 处理置顶状态
            if (record.isTop) {
                topTagTv.visibility = View.VISIBLE
                topBtn.text = context.getString(R.string.cancel_top)
            } else {
                topTagTv.visibility = View.GONE
                topBtn.text = context.getString(R.string.top)
            }

            // 2. 加载图片（使用Glide加载Uri）
            if (!record.imagePath.isNullOrEmpty()) {
                noteImageIv.visibility = View.VISIBLE
                Glide.with(context)
                    .load(Uri.parse(record.imagePath)) // 加载Uri字符串
                    .centerCrop()
                    .into(noteImageIv)
            } else {
                noteImageIv.visibility = View.GONE
            }

            // 3. 显示分类名称（从MainActivity获取）
            val categoryName = (context as MainActivity).getCategoryName(record.categoryId)
            categoryTagTv.text = context.getString(R.string.category_format, categoryName)

            // 4. 显示内容和时间
            contentTv.text = record.content
            timeTv.text = dateFormat.format(Date(record.time))

            // 5. 绑定点击事件
            topBtn.setOnClickListener { onTopClick(record) }
            editBtn.setOnClickListener { onEditClick(record) }
            deleteBtn.setOnClickListener { onDeleteClick(record) }
            itemView.setOnClickListener { onItemClick(record) }
        }
    }

    /**
     * 创建列表项视图
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_record, parent, false)
        return ViewHolder(view)
    }

    /**
     * 绑定数据到视图
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(records[position])
    }

    /**
     * 获取列表项数量
     */
    override fun getItemCount(): Int = records.size
}