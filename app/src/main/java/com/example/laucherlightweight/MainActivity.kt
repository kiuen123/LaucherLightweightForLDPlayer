package com.example.laucherlightweight

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.role.RoleManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ComponentName
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.provider.Settings
import android.view.DragEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: LauncherAdapter
    private lateinit var repository: AppRepository
    private lateinit var storageHelper: StorageHelper
    private lateinit var animationOverlay: FrameLayout
    private lateinit var wallpaperView: ImageView
    private var insertionIndicator: View? = null
    
    private val allLauncherItems = mutableListOf<LauncherItem>()
    private val allApps = mutableListOf<AppInfo>()
    private val allFolders = mutableListOf<FolderInfo>()

    private val homeRoleRequest = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { }

    private val wallpaperPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { selectedUri ->
            try {
                contentResolver.takePersistableUriPermission(
                    selectedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                storageHelper.saveWallpaperUri(selectedUri.toString())
                applyWallpaper(selectedUri)
            } catch (_: SecurityException) {
                Toast.makeText(this, R.string.wallpaper_selection_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Track what is being dragged: "app:<packageName>" or "folder:<folderId>"
    private var draggedItemId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        repository = AppRepository(this)
        storageHelper = StorageHelper(this)

        recyclerView = findViewById(R.id.recyclerView)
        wallpaperView = findViewById(R.id.wallpaperView)
        animationOverlay = findViewById(R.id.animationOverlay)
        animationOverlay.isClickable = false
        animationOverlay.isFocusable = false

        findViewById<ImageButton>(R.id.btnSettings).apply {
            bringToFront()
            setOnClickListener { showGridSettingsDialog() }
        }

        adapter = LauncherAdapter(
            items = allLauncherItems,
            onItemClick = { item -> handleItemClick(item) },
            onDragEvent = { item, event, view -> handleDragEvent(item, event, view) },
            onStartDrag = { item, view -> startDrag(item, view) },
            iconSizeDp = storageHelper.getIconSizeDp(),
            labelTextSizeSp = storageHelper.getLabelTextSizeSp()
        )
        recyclerView.adapter = adapter
        applyGridLayout()
        applySavedWallpaper()

        loadApps()
    }

    private fun applyGridLayout() {
        val columns = storageHelper.getGridColumns()
        recyclerView.layoutManager = GridLayoutManager(this, columns)
    }

    private fun showGridSettingsDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_grid_settings, null)
        val columnsSlider = view.findViewById<Slider>(R.id.columnsSlider)
        val columnsValue = view.findViewById<TextView>(R.id.columnsValue)
        val iconSizeSlider = view.findViewById<Slider>(R.id.iconSizeSlider)
        val iconSizeValue = view.findViewById<TextView>(R.id.iconSizeValue)
        val labelTextSizeSlider = view.findViewById<Slider>(R.id.labelTextSizeSlider)
        val labelTextSizeValue = view.findViewById<TextView>(R.id.labelTextSizeValue)
        val folderColumnsSlider = view.findViewById<Slider>(R.id.folderColumnsSlider)
        val folderColumnsValue = view.findViewById<TextView>(R.id.folderColumnsValue)
        val folderIconSizeSlider = view.findViewById<Slider>(R.id.folderIconSizeSlider)
        val folderIconSizeValue = view.findViewById<TextView>(R.id.folderIconSizeValue)
        val folderLabelTextSizeSlider = view.findViewById<Slider>(R.id.folderLabelTextSizeSlider)
        val folderLabelTextSizeValue = view.findViewById<TextView>(R.id.folderLabelTextSizeValue)
        val selectWallpaperButton = view.findViewById<View>(R.id.selectWallpaperButton)
        val clearWallpaperButton = view.findViewById<View>(R.id.clearWallpaperButton)
        val setDefaultLauncherButton = view.findViewById<View>(R.id.setDefaultLauncherButton)

        columnsSlider.value = storageHelper.getGridColumns().toFloat()
        iconSizeSlider.value = storageHelper.iconSizePickerIndex().toFloat()
        labelTextSizeSlider.value = storageHelper.getLabelTextSizeSp().toFloat()
        folderColumnsSlider.value = storageHelper.getFolderGridColumns().toFloat()
        folderIconSizeSlider.value = storageHelper.folderIconSizePickerIndex().toFloat()
        folderLabelTextSizeSlider.value = storageHelper.getFolderLabelTextSizeSp().toFloat()

        fun updateColumnsValue(value: Float) {
            columnsValue.text = value.toInt().toString()
        }
        fun updateIconSizeValue(value: Float) {
            val sizeDp = storageHelper.iconSizeFromPickerIndex(value.toInt())
            iconSizeValue.text = getString(R.string.icon_size_value, sizeDp)
        }
        fun updateLabelTextSizeValue(value: Float) {
            labelTextSizeValue.text = getString(R.string.label_text_size_value, value.toInt())
        }
        fun updateFolderColumnsValue(value: Float) {
            folderColumnsValue.text = value.toInt().toString()
        }
        fun updateFolderIconSizeValue(value: Float) {
            folderIconSizeValue.text = getString(
                R.string.icon_size_value,
                storageHelper.iconSizeFromPickerIndex(value.toInt())
            )
        }
        fun updateFolderLabelTextSizeValue(value: Float) {
            folderLabelTextSizeValue.text = getString(R.string.label_text_size_value, value.toInt())
        }
        updateColumnsValue(columnsSlider.value)
        updateIconSizeValue(iconSizeSlider.value)
        updateLabelTextSizeValue(labelTextSizeSlider.value)
        updateFolderColumnsValue(folderColumnsSlider.value)
        updateFolderIconSizeValue(folderIconSizeSlider.value)
        updateFolderLabelTextSizeValue(folderLabelTextSizeSlider.value)

        columnsSlider.addOnChangeListener { _, value, _ -> updateColumnsValue(value) }
        iconSizeSlider.addOnChangeListener { _, value, _ -> updateIconSizeValue(value) }
        labelTextSizeSlider.addOnChangeListener { _, value, _ -> updateLabelTextSizeValue(value) }
        folderColumnsSlider.addOnChangeListener { _, value, _ -> updateFolderColumnsValue(value) }
        folderIconSizeSlider.addOnChangeListener { _, value, _ -> updateFolderIconSizeValue(value) }
        folderLabelTextSizeSlider.addOnChangeListener { _, value, _ -> updateFolderLabelTextSizeValue(value) }
        selectWallpaperButton.setOnClickListener { wallpaperPicker.launch(arrayOf("image/*")) }
        clearWallpaperButton.setOnClickListener {
            storageHelper.clearWallpaperUri()
            applyDefaultWallpaper()
        }
        setDefaultLauncherButton.setOnClickListener { requestHomeRole() }

        val homeSettingsPage = view.findViewById<View>(R.id.homeSettingsPage)
        val folderSettingsPage = view.findViewById<View>(R.id.folderSettingsPage)
        view.findViewById<TabLayout>(R.id.settingsTabs).addOnTabSelectedListener(
            object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab) {
                    homeSettingsPage.visibility = if (tab.position == 0) View.VISIBLE else View.GONE
                    folderSettingsPage.visibility = if (tab.position == 1) View.VISIBLE else View.GONE
                }

                override fun onTabUnselected(tab: TabLayout.Tab) = Unit
                override fun onTabReselected(tab: TabLayout.Tab) = Unit
            }
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.grid_settings_title)
            .setView(view)
            .setPositiveButton(R.string.save) { _, _ ->
                storageHelper.saveGridColumns(columnsSlider.value.toInt())
                storageHelper.saveIconSizeDp(
                    storageHelper.iconSizeFromPickerIndex(iconSizeSlider.value.toInt())
                )
                storageHelper.saveLabelTextSizeSp(labelTextSizeSlider.value.toInt())
                storageHelper.saveFolderGridColumns(folderColumnsSlider.value.toInt())
                storageHelper.saveFolderIconSizeDp(
                    storageHelper.iconSizeFromPickerIndex(folderIconSizeSlider.value.toInt())
                )
                storageHelper.saveFolderLabelTextSizeSp(folderLabelTextSizeSlider.value.toInt())
                applyGridLayout()
                adapter.setIconSizeDp(storageHelper.getIconSizeDp())
                adapter.setLabelTextSizeSp(storageHelper.getLabelTextSizeSp())
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun requestHomeRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    Toast.makeText(this, R.string.already_default_launcher, Toast.LENGTH_SHORT).show()
                } else {
                    homeRoleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
                }
                return
            }
        }

        startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    }

    private fun applySavedWallpaper() {
        storageHelper.getWallpaperUri()?.let { uriString ->
            applyWallpaper(Uri.parse(uriString))
        } ?: applyDefaultWallpaper()
    }

    private fun applyDefaultWallpaper() {
        wallpaperView.setImageResource(R.drawable.default_wallpaper)
        wallpaperView.visibility = View.VISIBLE
    }

    private fun applyWallpaper(uri: Uri) {
        try {
            wallpaperView.setImageURI(uri)
            wallpaperView.visibility = View.VISIBLE
        } catch (_: SecurityException) {
            storageHelper.clearWallpaperUri()
            applyDefaultWallpaper()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyGridLayout()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            recyclerView.post { recyclerView.scrollToPosition(0) }
        }
    }

    private fun loadApps() {
        lifecycleScope.launch {
            val installedApps = storageHelper.sortAppsBySavedOrder(repository.getInstalledApps())
            allApps.clear()
            allApps.addAll(installedApps)
            
            val savedFolders = storageHelper.loadFolders(installedApps)
            allFolders.clear()
            allFolders.addAll(savedFolders)

            rebuildLauncherItems()
        }
    }

    private fun rebuildLauncherItems() {
        allLauncherItems.clear()
        allLauncherItems.addAll(allFolders)
        
        val appsInFolders = allFolders.flatMap { it.apps }.map { it.packageName }.toSet()
        val standaloneApps = allApps.filter { it.packageName !in appsInFolders }
        allLauncherItems.addAll(standaloneApps)
        
        adapter.updateItems(allLauncherItems)
    }

    private fun handleItemClick(item: LauncherItem) {
        when (item) {
            is AppInfo -> launchApp(item)
            is FolderInfo -> openFolderDialog(item)
        }
    }

    private fun launchApp(app: AppInfo) {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_LAUNCHER)
            intent.component = ComponentName(app.packageName, app.className)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể mở ứng dụng", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startDrag(item: LauncherItem, view: View) {
        val dragId = when (item) {
            is AppInfo -> "app:${item.packageName}"
            is FolderInfo -> "folder:${item.id}"
        }
        draggedItemId = dragId

        val clipData = ClipData(
            item.label,
            arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN),
            ClipData.Item(dragId)
        )
        val shadow = View.DragShadowBuilder(view)
        view.startDragAndDrop(clipData, shadow, view, 0)
    }

    private fun handleDragEvent(targetItem: LauncherItem, event: DragEvent, view: View): Boolean {
        when (event.action) {
            DragEvent.ACTION_DRAG_STARTED -> {
                return true
            }
            DragEvent.ACTION_DRAG_ENTERED -> {
                updateDropPreview(targetItem, event, view)
                return true
            }
            DragEvent.ACTION_DRAG_LOCATION -> {
                updateDropPreview(targetItem, event, view)
                return true
            }
            DragEvent.ACTION_DRAG_EXITED -> {
                clearInsertionIndicator()
                view.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                return true
            }
            DragEvent.ACTION_DROP -> {
                val insertionAfter = insertionSide(targetItem, event, view)
                clearInsertionIndicator()
                view.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                
                val dragId = draggedItemId ?: return false
                if (dragId == "app:${targetItem.id}" || dragId == "folder:${targetItem.id}") {
                    return false // dropped on itself
                }

                val draggedView = event.localState as? View ?: return false

                if (dragId.startsWith("folder:")) {
                    // Dragging a folder -> swap positions with target
                    val folderId = dragId.removePrefix("folder:")
                    val draggedFolder = allFolders.find { it.id == folderId } ?: return false
                    swapItems(draggedFolder, targetItem)
                    return true
                }
                
                if (dragId.startsWith("app:")) {
                    val packageName = dragId.removePrefix("app:")
                    val draggedApp = allApps.find { it.packageName == packageName } ?: return false
                    if (targetItem is AppInfo) {
                        if (insertionAfter != null) {
                            moveAppPosition(draggedApp, targetItem, insertionAfter)
                        } else {
                            animateDrop(draggedView, view, draggedApp, targetItem)
                        }
                        return true
                    }
                    animateDrop(draggedView, view, draggedApp, targetItem)
                    return true
                }

                return false
            }
            DragEvent.ACTION_DRAG_ENDED -> {
                clearInsertionIndicator()
                view.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                draggedItemId = null
                return true
            }
        }
        return false
    }

    private fun updateDropPreview(targetItem: LauncherItem, event: DragEvent, view: View) {
        val insertionAfter = insertionSide(targetItem, event, view)
        if (insertionAfter != null) {
            view.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
            showInsertionIndicator(view, insertionAfter)
        } else {
            clearInsertionIndicator()
            view.animate().alpha(1.0f).scaleX(1.15f).scaleY(1.15f).setDuration(150).start()
        }
    }

    private fun insertionSide(targetItem: LauncherItem, event: DragEvent, view: View): Boolean? {
        if (draggedItemId?.startsWith("app:") != true || targetItem !is AppInfo) return null

        val edgeZonePx = 24.dpToPx(this).toFloat()
        return when {
            event.x <= edgeZonePx -> false
            event.x >= view.width - edgeZonePx -> true
            else -> null
        }
    }

    private fun showInsertionIndicator(targetView: View, insertAfter: Boolean) {
        val indicatorWidth = 3.dpToPx(this)
        val targetLocation = IntArray(2)
        val overlayLocation = IntArray(2)
        targetView.getLocationInWindow(targetLocation)
        animationOverlay.getLocationInWindow(overlayLocation)

        val indicator = insertionIndicator ?: View(this).also {
            it.setBackgroundColor(Color.WHITE)
            it.alpha = 0.9f
            insertionIndicator = it
            animationOverlay.addView(it)
        }
        indicator.layoutParams = FrameLayout.LayoutParams(indicatorWidth, targetView.height)
        indicator.x = (targetLocation[0] - overlayLocation[0] + if (insertAfter) {
            targetView.width - indicatorWidth / 2
        } else {
            -indicatorWidth / 2
        }).toFloat()
        indicator.y = (targetLocation[1] - overlayLocation[1]).toFloat()
        indicator.visibility = View.VISIBLE
    }

    private fun clearInsertionIndicator() {
        insertionIndicator?.let { indicator ->
            animationOverlay.removeView(indicator)
            insertionIndicator = null
        }
    }

    private fun swapItems(draggedItem: LauncherItem, targetItem: LauncherItem) {
        val draggedIndex = allLauncherItems.indexOf(draggedItem)
        val targetIndex = allLauncherItems.indexOf(targetItem)
        
        if (draggedIndex == -1 || targetIndex == -1) return

        // Swap in main list
        allLauncherItems[draggedIndex] = targetItem
        allLauncherItems[targetIndex] = draggedItem

        // Also update folder order if both are folders
        if (draggedItem is FolderInfo && targetItem is FolderInfo) {
            val di = allFolders.indexOf(draggedItem)
            val ti = allFolders.indexOf(targetItem)
            if (di != -1 && ti != -1) {
                allFolders[di] = targetItem
                allFolders[ti] = draggedItem
            }
        }

        adapter.updateItems(allLauncherItems.toList())
        storageHelper.saveFolders(allFolders)
    }

    private fun moveAppPosition(draggedApp: AppInfo, targetApp: AppInfo, insertAfter: Boolean) {
        val oldVisualIndex = allLauncherItems.indexOf(draggedApp)
        val targetVisualIndex = allLauncherItems.indexOf(targetApp)
        if (oldVisualIndex == -1 || targetVisualIndex == -1) return

        allLauncherItems.removeAt(oldVisualIndex)
        val newTargetVisualIndex = allLauncherItems.indexOf(targetApp)
        val newVisualIndex = newTargetVisualIndex + if (insertAfter) 1 else 0
        allLauncherItems.add(newVisualIndex, draggedApp)

        allApps.remove(draggedApp)
        val newTargetAppIndex = allApps.indexOf(targetApp)
        allApps.add(newTargetAppIndex + if (insertAfter) 1 else 0, draggedApp)
        storageHelper.saveAppOrder(allApps)
        adapter.notifyItemMoved(oldVisualIndex, newVisualIndex)
    }

    private fun animateDrop(draggedView: View, targetView: View, draggedApp: AppInfo, targetItem: LauncherItem) {
        val animView = ImageView(this)
        animView.setImageDrawable(draggedApp.icon)
        val location = IntArray(2)
        draggedView.getLocationInWindow(location)
        animView.layoutParams = FrameLayout.LayoutParams(draggedView.width, draggedView.height)
        animView.x = location[0].toFloat()
        animView.y = location[1].toFloat()
        animationOverlay.addView(animView)

        val targetLocation = IntArray(2)
        targetView.getLocationInWindow(targetLocation)
        val targetX = targetLocation[0].toFloat() + (targetView.width - draggedView.width) / 2
        val targetY = targetLocation[1].toFloat() + (targetView.height - draggedView.height) / 2

        animView.animate()
            .x(targetX)
            .y(targetY)
            .scaleX(0.2f)
            .scaleY(0.2f)
            .alpha(0f)
            .setDuration(300)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animationOverlay.removeView(animView)
                    executeDropLogic(draggedApp, targetItem)
                }
            })
            .start()
    }

    private fun executeDropLogic(draggedApp: AppInfo, targetItem: LauncherItem) {
        // Remove app from any existing folder
        allFolders.forEach { it.apps.remove(draggedApp) }
        allFolders.removeAll { it.apps.isEmpty() }

        when (targetItem) {
            is FolderInfo -> {
                if (!targetItem.apps.contains(draggedApp)) {
                    targetItem.apps.add(draggedApp)
                }
            }
            is AppInfo -> {
                allFolders.forEach { it.apps.remove(targetItem) }
                allFolders.removeAll { it.apps.isEmpty() }
                
                val newFolder = FolderInfo("Thư mục mới", mutableListOf(targetItem, draggedApp))
                allFolders.add(newFolder)
            }
        }
        
        storageHelper.saveFolders(allFolders)
        rebuildLauncherItems()
    }

    private fun showFolderOptionsDialog(folder: FolderInfo) {
        val options = arrayOf("Đổi tên", "Xóa thư mục (giữ lại ứng dụng)")
        AlertDialog.Builder(this)
            .setTitle("Tùy chọn cho ${folder.label}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showRenameFolderDialog(folder)
                    1 -> {
                        allFolders.remove(folder)
                        storageHelper.saveFolders(allFolders)
                        rebuildLauncherItems()
                    }
                }
            }
            .show()
    }

    private fun showRenameFolderDialog(folder: FolderInfo) {
        val editText = EditText(this)
        editText.setText(folder.label)
        editText.setSelection(folder.label.length)

        AlertDialog.Builder(this)
            .setTitle("Đổi tên thư mục")
            .setView(editText)
            .setPositiveButton("Lưu") { _, _ ->
                val newName = editText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    val index = allFolders.indexOf(folder)
                    if (index != -1) {
                        allFolders[index] = folder.copy(label = newName)
                        storageHelper.saveFolders(allFolders)
                        rebuildLauncherItems()
                    }
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun openFolderDialog(folder: FolderInfo) {
        val view = layoutInflater.inflate(R.layout.dialog_folder, null)
        val dialogRecyclerView = view.findViewById<RecyclerView>(R.id.dialogRecyclerView)
        
        dialogRecyclerView.layoutManager = GridLayoutManager(this, storageHelper.getFolderGridColumns())

        val dialog = AlertDialog.Builder(this)
            .setTitle(folder.label)
            .setView(view)
            .setPositiveButton("Đóng", null)
            .setNeutralButton("Tùy chọn") { _, _ ->
                showFolderOptionsDialog(folder)
            }
            .create()

        val dialogAdapter = LauncherAdapter(
            items = folder.apps,
            onItemClick = { item -> 
                if (item is AppInfo) {
                    dialog.dismiss()
                    launchApp(item)
                }
            },
            onDragEvent = { _, _, _ -> false },
            iconSizeDp = storageHelper.getFolderIconSizeDp(),
            labelTextSizeSp = storageHelper.getFolderLabelTextSizeSp(),
            labelTextColor = Color.parseColor("#FF1E293B"),
            onStartDrag = { item, _ ->
                if (item is AppInfo) {
                    AlertDialog.Builder(this)
                        .setTitle(item.label)
                        .setItems(arrayOf("Di chuyển ra ngoài")) { _, _ ->
                            folder.apps.remove(item)
                            if (folder.apps.isEmpty()) {
                                allFolders.remove(folder)
                                dialog.dismiss()
                            }
                            storageHelper.saveFolders(allFolders)
                            rebuildLauncherItems()
                            // Refresh the dialog's list if it's still showing
                            if (dialog.isShowing) {
                                (dialogRecyclerView.adapter as? LauncherAdapter)
                                    ?.updateItems(folder.apps.toList())
                                dialog.setTitle(folder.label)
                            }
                        }
                        .show()
                }
            }
        )
        dialogRecyclerView.adapter = dialogAdapter

        dialog.show()
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Do nothing - this is a launcher
    }
}
