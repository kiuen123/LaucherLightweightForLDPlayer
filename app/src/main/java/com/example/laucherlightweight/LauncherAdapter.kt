package com.example.laucherlightweight

import android.content.ClipData
import android.graphics.Color
import android.util.TypedValue
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LauncherAdapter(
    private var items: List<LauncherItem>,
    private val onItemClick: (LauncherItem) -> Unit,
    private val onDragEvent: (LauncherItem, DragEvent, View) -> Boolean,
    private val onStartDrag: (LauncherItem, View) -> Unit,
    private var iconSizeDp: Int = StorageHelper.DEFAULT_ICON_SIZE_DP,
    private var labelTextSizeSp: Int = StorageHelper.DEFAULT_LABEL_TEXT_SIZE_SP,
    private val labelTextColor: Int = Color.WHITE
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_APP = 1
        private const val TYPE_FOLDER = 2
    }

    fun updateItems(newItems: List<LauncherItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun setIconSizeDp(sizeDp: Int) {
        iconSizeDp = sizeDp
    }

    fun setLabelTextSizeSp(sizeSp: Int) {
        labelTextSizeSp = sizeSp
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is AppInfo -> TYPE_APP
            is FolderInfo -> TYPE_FOLDER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_APP) {
            val view = inflater.inflate(R.layout.item_app, parent, false)
            AppViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_folder, parent, false)
            FolderViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is AppViewHolder && item is AppInfo) {
            holder.bind(item)
        } else if (holder is FolderViewHolder && item is FolderInfo) {
            holder.bind(item)
        }
        
        holder.itemView.setOnClickListener { onItemClick(item) }
        holder.itemView.setOnLongClickListener { view ->
            onStartDrag(item, view)
            true
        }
        holder.itemView.setOnDragListener { view, event ->
            onDragEvent(item, event, view)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.appIcon)
        private val nameView: TextView = itemView.findViewById(R.id.appName)

        fun bind(app: AppInfo) {
            iconView.setImageDrawable(app.icon)
            nameView.text = app.label
            nameView.setTextColor(labelTextColor)
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, labelTextSizeSp.toFloat())
            itemView.tag = app.id

            val sizePx = iconSizeDp.dpToPx(itemView.context)
            iconView.layoutParams = iconView.layoutParams.apply {
                width = sizePx
                height = sizePx
            }
        }
    }

    inner class FolderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameView: TextView = itemView.findViewById(R.id.folderName)
        private val folderIconContainer: View = itemView.findViewById(R.id.folderIconContainer)
        private val gridLayout: GridLayout = itemView.findViewById(R.id.folderIconGrid)

        fun bind(folder: FolderInfo) {
            nameView.text = folder.label
            nameView.setTextColor(labelTextColor)
            nameView.setTextSize(TypedValue.COMPLEX_UNIT_SP, labelTextSizeSp.toFloat())
            itemView.tag = folder.id

            val folderSizePx = iconSizeDp.dpToPx(itemView.context)
            folderIconContainer.layoutParams = folderIconContainer.layoutParams.apply {
                width = folderSizePx
                height = folderSizePx
            }

            val containerPaddingPx = 4.dpToPx(itemView.context)
            val iconMarginPx = 2.dpToPx(itemView.context)
            val miniIconPx = ((folderSizePx - containerPaddingPx * 2 - iconMarginPx * 4) / 2)
                .coerceAtLeast(1)
            
            gridLayout.removeAllViews()
            val limit = minOf(folder.apps.size, 4)
            for (i in 0 until limit) {
                val app = folder.apps[i]
                val img = ImageView(itemView.context)
                img.setImageDrawable(app.icon)
                val params = GridLayout.LayoutParams()
                params.width = miniIconPx
                params.height = miniIconPx
                params.setMargins(iconMarginPx, iconMarginPx, iconMarginPx, iconMarginPx)
                img.layoutParams = params
                img.scaleType = ImageView.ScaleType.FIT_CENTER
                gridLayout.addView(img)
            }
        }
    }
}

fun Int.dpToPx(context: android.content.Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}
