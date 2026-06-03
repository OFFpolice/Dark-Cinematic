package com.example

import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.data.WallpaperItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // Cyberpunk Theme Colors
    private val NeonCyan = 0xFF00F0FF.toInt()
    private val NeonPurple = 0xFF9D4EDD.toInt()
    private val NeonPink = 0xFFFF007F.toInt()
    private val NeonMint = 0xFF00FFCC.toInt()
    private val CyberBackground = 0xFF0A0C10.toInt()
    private val CyberCard = 0xFF141A26.toInt()
    private val CyberBorder = 0xFF222B3D.toInt()
    private val CyberOnCard = 0xFFE2E8F0.toInt()
    private val CyberMuted = 0xFF94A3B8.toInt()
    private val CyberActiveBg = 0xFF1E293B.toInt()

    private lateinit var viewModel: WallpaperViewModel

    // Root overlay container
    private lateinit var rootContainer: FrameLayout
    private lateinit var loadingOverlay: FrameLayout

    // Main views
    private lateinit var listView: View
    private lateinit var editorView: View

    // List view inner components
    private lateinit var listEmptyState: LinearLayout
    private lateinit var listScrollView: NestedScrollView
    private lateinit var listContentContainer: LinearLayout
    private lateinit var activeItemCard: FrameLayout
    private lateinit var activeItemThumbnail: ImageView
    private lateinit var activeItemTitle: TextView
    private lateinit var activeItemStatus: TextView
    private lateinit var activeItemActionBtn: Button
    private lateinit var wallpaperItemsContainer: LinearLayout

    // Editor view inner components
    private var editorVideoView: AspectRatioVideoView? = null
    private lateinit var editorPreviewContainer: FrameLayout
    private lateinit var editorNightFilter: View
    private lateinit var editorNightIndicator: TextView
    private lateinit var editorTrimStartLabel: TextView
    private lateinit var editorTrimEndLabel: TextView
    private lateinit var editorTrimStartSeekBar: SeekBar
    private lateinit var editorTrimEndSeekBar: SeekBar
    private lateinit var editorLoopSwitch: Switch
    private lateinit var editorNightSwitch: Switch
    private lateinit var editorNightDimLayout: LinearLayout
    private lateinit var editorNightDimPctLabel: TextView
    private lateinit var editorNightDimSeekBar: SeekBar
    private lateinit var editorCoverTimeSeekBar: SeekBar

    // Permissions check
    private val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_VIDEO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            videoPickerLauncher.launch("video/mp4")
        } else {
            Toast.makeText(this, "Доступ к файлам видео отклонен", Toast.LENGTH_SHORT).show()
        }
    }

    private val videoPickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val title = getFileName(this, uri) ?: "Видео обои"
            viewModel.startEditingNewVideo(uri, this, title)
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val previewPlaybackCheckRunnable = object : Runnable {
        override fun run() {
            val view = editorVideoView ?: return
            val item = viewModel.editingItem.value ?: return
            try {
                if (view.isPlaying) {
                    val pos = view.currentPosition.toLong()
                    if (item.trimEndMs > 0L && pos >= item.trimEndMs) {
                        view.seekTo(item.trimStartMs.toInt())
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error checking preview play position", e)
            }
            handler.postDelayed(this, 150)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize viewmodel
        viewModel = ViewModelProvider(this)[WallpaperViewModel::class.java]

        // Create programmatic master UI
        buildMasterUI()
        setContentView(rootContainer)

        // Bind data state observers
        bindStateObservers()
    }

    private fun bindStateObservers() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Monitor wallpaper list
                launch {
                    viewModel.wallpaperList.collectLatest { list ->
                        populateWallpaperList(list)
                    }
                }

                // Monitor active wallpaper
                launch {
                    viewModel.activeWallpaper.collectLatest { active ->
                        updateActiveWallpaperCard(active)
                    }
                }

                // Monitor editor screens state changes
                launch {
                    viewModel.editingItem.collectLatest { item ->
                        refreshScreenState(item)
                    }
                }

                // Monitor background processing frames
                launch {
                    viewModel.isProcessing.collectLatest { processing ->
                        loadingOverlay.visibility = if (processing) View.VISIBLE else View.GONE
                    }
                }
            }
        }
    }

    private fun buildMasterUI() {
        rootContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(CyberBackground)
        }

        // 1. Build List Screen view
        listView = buildListScreen()
        rootContainer.addView(listView)

        // 2. Build Editor Screen view
        editorView = buildEditorScreen()
        rootContainer.addView(editorView)

        // 3. Build Loading Overlay
        loadingOverlay = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.argb(130, 0, 0, 0))
            isClickable = true
            isFocusable = true
            visibility = View.GONE

            val progressBar = ProgressBar(this@MainActivity).apply {
                layoutParams = FrameLayout.LayoutParams(
                    dp(64), dp(64)
                ).apply {
                    gravity = Gravity.CENTER
                }
                indeterminateTintList = ColorStateList.valueOf(NeonCyan)
            }
            addView(progressBar)
        }
        rootContainer.addView(loadingOverlay)
    }

    // LIST SCREEN
    private fun buildListScreen(): View {
        val layout = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        // Inner vertical LinearLayout scroll layout
        val verticalContainer = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
        }

        // Top Header Tool bar
        val headerBar = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(CyberBackground)
            padding(16, 16, 16, 12)
        }

        val titleText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "Live Wallpaper"
            textColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            letterSpacing = 0.08f
        }
        headerBar.addView(titleText)

        // Cyber Gradient bottom trim line under tool bar
        val neonLine = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(2)
            ).apply {
                topMargin = dp(12)
            }
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.TRANSPARENT, NeonCyan, NeonPurple, Color.TRANSPARENT)
            )
        }
        headerBar.addView(neonLine)
        verticalContainer.addView(headerBar)

        // Main content area scroll view
        listScrollView = NestedScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            isFillViewport = true
        }

        listContentContainer = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            padding(16, 16, 16, 96) // generous bottom padding so items aren't obscured by FAB
        }

        // Active wallpaper section frame
        activeItemCard = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(24)
            }
            visibility = View.GONE
        }
        listContentContainer.addView(activeItemCard)

        // List item lists wrapper
        wallpaperItemsContainer = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
        }
        listContentContainer.addView(wallpaperItemsContainer)

        listScrollView.addView(listContentContainer)
        verticalContainer.addView(listScrollView)

        // Empty state layout inside root
        listEmptyState = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                leftMargin = dp(32)
                rightMargin = dp(32)
            }
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE

            val emptyIcon = ImageView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(80), dp(80)).apply {
                    bottomMargin = dp(16)
                }
                setImageResource(android.R.drawable.ic_menu_slideshow)
                imageTintList = ColorStateList.valueOf(NeonCyan)
            }
            addView(emptyIcon)

            val emptyTitle = TextView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = "Библиотека пуста"
                textColor(Color.WHITE)
                textSize = 18f
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
            }
            addView(emptyTitle)

            val emptyDesc = TextView(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                    bottomMargin = dp(24)
                }
                text = "Добавьте ваше первое видео в формате MP4!"
                textColor(CyberMuted)
                textSize = 13f
                gravity = Gravity.CENTER
            }
            addView(emptyDesc)

            val firstAddBtn = Button(this@MainActivity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dp(48)
                )
                text = "Добавить видео"
                textColor(Color.BLACK)
                typeface = Typeface.DEFAULT_BOLD
                isAllCaps = false
                background = createRoundedGradientDrawable(NeonCyan, NeonCyan, 24)
                setPadding(dp(20), 0, dp(20), 0)
                setOnClickListener {
                    triggerVideoSelection()
                }
            }
            addView(firstAddBtn)
        }
        layout.addView(verticalContainer)
        layout.addView(listEmptyState)

        // Beautiful Floating Add Button
        val fab = Button(this).apply {
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                dp(56)
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(24)
            }
            layoutParams = params
            text = "+ Добавить видео"
            textColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            textSize = 15f
            setPadding(dp(24), 0, dp(24), 0)
            background = createRoundedGradientDrawable(NeonCyan, NeonCyan, 28)
            setElevationValue(6)

            setOnClickListener {
                triggerVideoSelection()
            }
        }
        layout.addView(fab)

        return layout
    }

    private fun triggerVideoSelection() {
        if (ContextCompat.checkSelfPermission(this, storagePermission) == PackageManager.PERMISSION_GRANTED) {
            videoPickerLauncher.launch("video/mp4")
        } else {
            requestPermissionLauncher.launch(storagePermission)
        }
    }

    private fun updateActiveWallpaperCard(active: WallpaperItem?) {
        if (active == null) {
            activeItemCard.visibility = View.GONE
            return
        }

        activeItemCard.visibility = View.VISIBLE
        activeItemCard.removeAllViews()

        // Root FrameLayout serving as rounded border container
        val outerBorder = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            background = createRoundedGradientDrawable(CyberCard, 10, cornerDp = 16)
            padding(1) // Simulate thin border line by nesting card inside other coloured background
        }

        // Apply a visual gradient border to active card
        val doubleGradientBg = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(NeonCyan, NeonPurple)
        ).apply {
            cornerRadius = dp(16).toFloat()
        }
        outerBorder.background = doubleGradientBg

        val cardContent = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            background = createRoundedGradientDrawable(CyberCard, 0, cornerDp = 15)
            padding(16, 16, 16, 16)
        }

        // Horizontal title row with status indicator
        val titleRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val starIndicator = TextView(this).apply {
            text = "★   АКТИВНЫЙ ПРОФИЛЬ"
            textColor(NeonCyan)
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
        }
        titleRow.addView(starIndicator)
        cardContent.addView(titleRow)

        // Details Row
        val detailsRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        activeItemThumbnail = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(68), dp(68))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = createRoundedGradientDrawable(Color.DKGRAY, CyberBorder, 12, 1)
        }
        loadThumbnailAsync(activeItemThumbnail, active.thumbnailUriStr)
        detailsRow.addView(activeItemThumbnail)

        val activeTextInfoLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(14)
                rightMargin = dp(8)
            }
            orientation = LinearLayout.VERTICAL
        }

        activeItemTitle = TextView(this).apply {
            text = active.title
            textColor(Color.WHITE)
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        activeTextInfoLayout.addView(activeItemTitle)

        activeItemStatus = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
            }
            val loopStr = if (active.isLooping) "Зациклен" else "Один раз"
            val nightStr = if (active.nightModeEnabled) " • Ночная автояркость" else ""
            text = "$loopStr$nightStr"
            textColor(CyberMuted)
            textSize = 11f
        }
        activeTextInfoLayout.addView(activeItemStatus)
        detailsRow.addView(activeTextInfoLayout)

        activeItemActionBtn = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(40)
            )
            text = "Запуск "
            textColor(Color.BLACK)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            textSize = 13f
            background = createRoundedGradientDrawable(NeonCyan, NeonCyan, 12)
            setPadding(dp(16), 0, dp(16), 0)
            setOnClickListener {
                viewModel.setActive(active.id)
                launchWallpaperSelection(this@MainActivity)
            }
        }
        detailsRow.addView(activeItemActionBtn)

        cardContent.addView(detailsRow)
        outerBorder.addView(cardContent)
        activeItemCard.addView(outerBorder)
    }

    private fun populateWallpaperList(list: List<WallpaperItem>) {
        wallpaperItemsContainer.removeAllViews()

        if (list.isEmpty()) {
            listScrollView.visibility = View.GONE
            listEmptyState.visibility = View.VISIBLE
            return
        }

        listScrollView.visibility = View.VISIBLE
        listEmptyState.visibility = View.GONE

        // Library header item inside scrolling content
        val sectionHeader = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(12)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val infoBullet = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(4), dp(16))
            background = createRoundedGradientDrawable(NeonPurple, NeonPurple, 2)
        }
        sectionHeader.addView(infoBullet)

        val headerLabel = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = dp(8)
            }
            text = "Библиотека обоев"
            textColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        sectionHeader.addView(headerLabel)
        wallpaperItemsContainer.addView(sectionHeader)

        // Iterate list to append standard non-active rows
        for (item in list) {
            val cellFrame = FrameLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(12)
                }
                background = createRoundedGradientDrawable(CyberCard, CyberBorder, 16, 1)
            }

            val cardCellLayout = LinearLayout(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                padding(12, 12, 12, 12)
            }

            // Upper contents row
            val upperRow = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(12)
                }
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            // Thumbnail
            val thumbView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(54), dp(54))
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = createRoundedGradientDrawable(Color.DKGRAY, CyberBorder, 10, 1)
            }
            loadThumbnailAsync(thumbView, item.thumbnailUriStr)
            upperRow.addView(thumbView)

            val textInfo = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = dp(12)
                }
                orientation = LinearLayout.VERTICAL
            }

            val itemTitle = TextView(this).apply {
                text = item.title
                textColor(Color.WHITE)
                textSize = 15f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
            }
            textInfo.addView(itemTitle)

            val itemMeta = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(4)
                }
                val loopText = if (item.isLooping) "Зациклен" else "Одиночный"
                val nightText = if (item.nightModeEnabled) " + Ночь" else ""
                text = "$loopText$nightText"
                textColor(CyberMuted)
                textSize = 11f
            }
            textInfo.addView(itemMeta)
            upperRow.addView(textInfo)

            // Select marker
            if (item.id != viewModel.activeWallpaper.value?.id) {
                val selectButton = Button(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        dp(36)
                    )
                    text = "Выбрать"
                    textColor(Color.BLACK)
                    typeface = Typeface.DEFAULT_BOLD
                    isAllCaps = false
                    textSize = 11f
                    background = createRoundedGradientDrawable(NeonCyan, NeonCyan, 10)
                    setPadding(dp(12), 0, dp(12), 0)
                    setOnClickListener {
                        viewModel.setActive(item.id)
                        launchWallpaperSelection(this@MainActivity)
                    }
                }
                upperRow.addView(selectButton)
            } else {
                val activeMarkerBadge = TextView(this).apply {
                    padding(8, 4, 8, 4)
                    text = "Активен"
                    textColor(NeonMint)
                    textSize = 11f
                    typeface = Typeface.DEFAULT_BOLD
                    background = createRoundedGradientDrawable(0x2200FFCC.toInt(), NeonMint, 6, 1)
                }
                upperRow.addView(activeMarkerBadge)
            }
            cardCellLayout.addView(upperRow)

            // Bottom action bar controls row
            val actionsRow = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
            }

            val editBtn = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    rightMargin = dp(16)
                }
                text = " Изменить"
                textColor(NeonCyan)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                isClickable = true
                isFocusable = true
                gravity = Gravity.CENTER
                setOnClickListener {
                    viewModel.startEditingExisting(item, this@MainActivity)
                }
            }
            actionsRow.addView(editBtn)

            val deleteBtn = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                text = " Удалить"
                textColor(0xFFFF4949.toInt()) // Red warning color
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                isClickable = true
                isFocusable = true
                gravity = Gravity.CENTER
                setOnClickListener {
                    showDeleteConfirmationDialog(item)
                }
            }
            actionsRow.addView(deleteBtn)

            cardCellLayout.addView(actionsRow)
            cellFrame.addView(cardCellLayout)
            wallpaperItemsContainer.addView(cellFrame)
        }
    }

    private fun showDeleteConfirmationDialog(item: WallpaperItem) {
        val dialog = AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_wallpaper))
            .setMessage(getString(R.string.confirm_delete))
            .setPositiveButton("Удалить") { _, _ ->
                viewModel.deleteWallpaper(item)
            }
            .setNegativeButton("Отмена", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(Color.RED)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(Color.GRAY)
        }
        dialog.show()
    }


    // EDITOR SCREEN
    private fun buildEditorScreen(): View {
        val root = NestedScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            isFillViewport = true
            visibility = View.GONE
        }

        val outerContainer = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            padding(16, 16, 16, 32)
        }

        // 1. Editor screen Header row
        val headerRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val backBtn = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply {
                rightMargin = dp(8)
            }
            text = "◀"
            textColor(Color.WHITE)
            textSize = 12f
            background = createRoundedGradientDrawable(Color.TRANSPARENT, Color.TRANSPARENT, 22)
            setOnClickListener {
                viewModel.cancelEditing()
            }
        }
        headerRow.addView(backBtn)

        val headerTitle = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = "Настройка обоев"
            textColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        headerRow.addView(headerTitle)

        val saveBtn = Button(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(38)
            )
            text = "Сохранить"
            textColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            textSize = 12f
            background = createRoundedGradientDrawable(NeonPurple, NeonPurple, 10)
            setPadding(dp(16), 0, dp(16), 0)
            setOnClickListener {
                viewModel.saveEditingItem()
            }
        }
        headerRow.addView(saveBtn)
        outerContainer.addView(headerRow)

        // 2. Video Preview player card container
        editorPreviewContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(220)
            ).apply {
                bottomMargin = dp(20)
            }
            background = createRoundedGradientDrawable(CyberCard, CyberBorder, 16, 1)
            clipToOutline = true
        }

        // Add dynamic transient dark overlay to preview auto-nightmode dim filters
        editorNightFilter = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.BLACK)
            alpha = 0f
            visibility = View.GONE
        }

        editorNightIndicator = TextView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dp(8)
                rightMargin = dp(8)
            }
            padding(8, 4, 8, 4)
            text = "Демонстрация ночной автояркости активна"
            textColor(NeonCyan)
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            background = createRoundedGradientDrawable(0xCC000000.toInt(), NeonCyan, 6, 1)
            visibility = View.GONE
        }

        outerContainer.addView(editorPreviewContainer)

        // 3. Segment Trimming Header Section
        val trimmingHeader = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val cutIcon = TextView(this).apply {
            text = "✂ "
            textColor(NeonPurple)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        trimmingHeader.addView(cutIcon)

        val trimTitle = TextView(this).apply {
            text = "Границы обрезки видео"
            textColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        trimmingHeader.addView(trimTitle)
        outerContainer.addView(trimmingHeader)

        // Trim values labels row
        val trimLabelsRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
        }

        editorTrimStartLabel = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = "Старт: 00:00"
            textColor(NeonCyan)
            textSize = 12f
        }
        trimLabelsRow.addView(editorTrimStartLabel)

        editorTrimEndLabel = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            text = "Конец: 00:10"
            textColor(NeonPurple)
            textSize = 12f
        }
        trimLabelsRow.addView(editorTrimEndLabel)
        outerContainer.addView(trimLabelsRow)

        // Start duration seeker bar
        val startSeekLabel = TextView(this).apply {
            text = "Начало видео отрезка:"
            textColor(CyberMuted)
            textSize = 11f
        }
        outerContainer.addView(startSeekLabel)

        editorTrimStartSeekBar = SeekBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
            progressTintList = ColorStateList.valueOf(NeonCyan)
            thumbTintList = ColorStateList.valueOf(NeonCyan)

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val duration = viewModel.videoDurationMs.value
                        val newStart = (progress.toFloat() / 100f * duration).toLong()
                        val currentEnd = (editorTrimEndSeekBar.progress.toFloat() / 100f * duration).toLong()

                        if (newStart >= currentEnd) {
                            val boundedStart = currentEnd - 500L
                            val restrictedStart = if (boundedStart < 0) 0L else boundedStart
                            val newProgress = (restrictedStart.toFloat() / duration * 100f).toInt()
                            progressTintList = ColorStateList.valueOf(NeonCyan)
                            seekBar?.progress = newProgress
                            viewModel.updateEditingTrim(restrictedStart, currentEnd)
                            editorVideoView?.seekTo(restrictedStart.toInt())
                        } else {
                            viewModel.updateEditingTrim(newStart, currentEnd)
                            editorVideoView?.seekTo(newStart.toInt())
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        outerContainer.addView(editorTrimStartSeekBar)

        // End duration seeker bar
        val endSeekLabel = TextView(this).apply {
            text = "Конец видео отрезка:"
            textColor(CyberMuted)
            textSize = 11f
        }
        outerContainer.addView(endSeekLabel)

        editorTrimEndSeekBar = SeekBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16)
            }
            progressTintList = ColorStateList.valueOf(NeonPurple)
            thumbTintList = ColorStateList.valueOf(NeonPurple)

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val duration = viewModel.videoDurationMs.value
                        val newEnd = (progress.toFloat() / 100f * duration).toLong()
                        val currentStart = (editorTrimStartSeekBar.progress.toFloat() / 100f * duration).toLong()

                        if (newEnd <= currentStart) {
                            val boundedEnd = currentStart + 500L
                            val restrictedEnd = if (boundedEnd > duration) duration else boundedEnd
                            val newProgress = (restrictedEnd.toFloat() / duration * 100f).toInt()
                            seekBar?.progress = newProgress
                            viewModel.updateEditingTrim(currentStart, restrictedEnd)
                            editorVideoView?.seekTo(restrictedEnd.toInt())
                        } else {
                            viewModel.updateEditingTrim(currentStart, newEnd)
                            editorVideoView?.seekTo(newEnd.toInt())
                        }
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        outerContainer.addView(editorTrimEndSeekBar)

        // Separator Line
        val sep1 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                bottomMargin = dp(16)
            }
            setBackgroundColor(CyberBorder)
        }
        outerContainer.addView(sep1)

        // 4. Looping switch row
        val loopRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val loopTexts = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
        }

        val loopTitle = TextView(this).apply {
            text = "Зациклить воспроизведение"
            textColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        loopTexts.addView(loopTitle)

        val loopDesc = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2)
            }
            text = "Повторять воспроизведение видео по кругу"
            textColor(CyberMuted)
            textSize = 11f
        }
        loopTexts.addView(loopDesc)
        loopRow.addView(loopTexts)

        editorLoopSwitch = Switch(this).apply {
            thumbTintList = ColorStateList.valueOf(NeonCyan)
            trackTintList = ColorStateList.valueOf(0x4400F0FF.toInt())
            setOnCheckedChangeListener { _, isChecked ->
                viewModel.updateEditingOptions(isChecked, editorNightSwitch.isChecked, (editorNightDimSeekBar.progress.toFloat() / 100f).coerceIn(0.1f, 0.8f))
            }
        }
        loopRow.addView(editorLoopSwitch)
        outerContainer.addView(loopRow)

        // Separator
        val sep2 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                bottomMargin = dp(16)
            }
            setBackgroundColor(CyberBorder)
        }
        outerContainer.addView(sep2)

        // 5. Auto Night Mode Switch Row
        val nightRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val nightTexts = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            orientation = LinearLayout.VERTICAL
        }

        val nightTitle = TextView(this).apply {
            text = "Автояркость в ночное время"
            textColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        nightTexts.addView(nightTitle)

        val nightDesc = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2)
            }
            text = "Снижает яркость экрана ночью (22:00 - 06:00)"
            textColor(CyberMuted)
            textSize = 11f
        }
        nightTexts.addView(nightDesc)
        nightRow.addView(nightTexts)

        editorNightSwitch = Switch(this).apply {
            thumbTintList = ColorStateList.valueOf(NeonCyan)
            trackTintList = ColorStateList.valueOf(0x4400F0FF.toInt())
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !Settings.System.canWrite(this@MainActivity)) {
                    this.isChecked = false
                    showWriteSettingsPermissionDialog()
                } else {
                    editorNightDimLayout.visibility = if (isChecked) View.VISIBLE else View.GONE
                    viewModel.updateEditingOptions(editorLoopSwitch.isChecked, isChecked, (editorNightDimSeekBar.progress.toFloat() / 100f).coerceIn(0.1f, 0.8f))
                    syncDynamicNightModeOverlay()
                }
            }
        }
        nightRow.addView(editorNightSwitch)
        outerContainer.addView(nightRow)

        // Dim percentage Slider child panel
        editorNightDimLayout = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16)
            }
            orientation = LinearLayout.VERTICAL
            padding(12, 12, 12, 12)
            background = createRoundedGradientDrawable(0x22222B3D.toInt(), CyberBorder, 12, 1)
            visibility = View.GONE
        }

        val factorInfoRow = LinearLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
            orientation = LinearLayout.HORIZONTAL
        }

        val factorLabel = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = "Степень затемнения:"
            textColor(Color.WHITE)
            textSize = 12f
        }
        factorInfoRow.addView(factorLabel)

        editorNightDimPctLabel = TextView(this).apply {
            text = "50%"
            textColor(NeonCyan)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }
        factorInfoRow.addView(editorNightDimPctLabel)
        editorNightDimLayout.addView(factorInfoRow)

        editorNightDimSeekBar = SeekBar(this).apply {
            max = 80
            progress = 40 // corresponds to 40% (factor 0.4f)
            progressTintList = ColorStateList.valueOf(NeonCyan)
            thumbTintList = ColorStateList.valueOf(NeonCyan)

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val actualVal = if (progress < 10) 10 else progress
                    editorNightDimPctLabel.text = "$actualVal%"
                    if (fromUser) {
                        val factor = actualVal.toFloat() / 100f
                        viewModel.updateEditingOptions(editorLoopSwitch.isChecked, editorNightSwitch.isChecked, factor)
                        syncDynamicNightModeOverlay()
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        editorNightDimLayout.addView(editorNightDimSeekBar)
        outerContainer.addView(editorNightDimLayout)

        // Separator
        val sep3 = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                bottomMargin = dp(16)
            }
            setBackgroundColor(CyberBorder)
        }
        outerContainer.addView(sep3)

        // 6. Cover Frame Selector panel
        val coverTitle = TextView(this).apply {
            text = "Выбор обложки (кадра)"
            textColor(Color.WHITE)
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
        }
        outerContainer.addView(coverTitle)

        val coverDesc = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(2)
                bottomMargin = dp(8)
            }
            text = "Выберите кадр видео для обложки в вашей библиотеке"
            textColor(CyberMuted)
            textSize = 11f
        }
        outerContainer.addView(coverDesc)

        editorCoverTimeSeekBar = SeekBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(32)
            }
            progressTintList = ColorStateList.valueOf(NeonMint)
            thumbTintList = ColorStateList.valueOf(NeonMint)

            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val startMs = (editorTrimStartSeekBar.progress.toFloat() / 100f * viewModel.videoDurationMs.value).toLong()
                        val endMs = (editorTrimEndSeekBar.progress.toFloat() / 100f * viewModel.videoDurationMs.value).toLong()
                        val selectTime = startMs + ((progress.toFloat() / 100f) * (endMs - startMs)).toLong()

                        editorVideoView?.seekTo(selectTime.toInt())
                        // Schedule extracting dynamic frame
                        viewModel.changeEditingCoverFrame(this@MainActivity, selectTime)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        outerContainer.addView(editorCoverTimeSeekBar)

        root.addView(outerContainer)
        return root
    }

    private fun showWriteSettingsPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Требуется разрешение настроек")
            .setMessage("Для настройки автояркости ночью приложению требуется разрешение на запись системных настроек.")
            .setPositiveButton("Перейти") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun syncDynamicNightModeOverlay() {
        val editing = viewModel.editingItem.value ?: return
        if (editing.nightModeEnabled) {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val isNight = hour >= 22 || hour < 6
            if (isNight) {
                editorNightFilter.visibility = View.VISIBLE
                editorNightFilter.alpha = editing.nightBrightnessReduction
                editorNightIndicator.visibility = View.VISIBLE
            } else {
                editorNightFilter.visibility = View.GONE
                editorNightIndicator.visibility = View.GONE
            }
        } else {
            editorNightFilter.visibility = View.GONE
            editorNightIndicator.visibility = View.GONE
        }
    }

    private fun refreshScreenState(editingItem: WallpaperItem?) {
        if (editingItem == null) {
            listView.visibility = View.VISIBLE
            editorView.visibility = View.GONE

            // Release preview video view
            handler.removeCallbacks(previewPlaybackCheckRunnable)
            try {
                editorVideoView?.stopPlayback()
            } catch (ex: Exception) {}
            editorVideoView = null
            editorPreviewContainer.removeAllViews()
        } else {
            listView.visibility = View.GONE
            editorView.visibility = View.VISIBLE

            // Set up editor preview player
            setupEditorPreviewPlayer(editingItem)

            // Sync seek bars and toggles
            bindEditorViewData(editingItem)
        }
    }

    private fun setupEditorPreviewPlayer(item: WallpaperItem) {
        editorPreviewContainer.removeAllViews()

        editorVideoView = AspectRatioVideoView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setOnErrorListener { _, _, _ -> true }
            setOnPreparedListener { mp ->
                mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
                mp.setVolume(0f, 0f) // muted wallpaper style
                setVideoSize(mp.videoWidth, mp.videoHeight)

                val duration = mp.duration.toLong()
                val isFullyUntrimmed = item.trimStartMs <= 100L && (item.trimEndMs == 0L || item.trimEndMs >= duration - 100L)
                mp.isLooping = isFullyUntrimmed

                try {
                    seekTo(item.trimStartMs.toInt())
                    start()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error starting player", e)
                }
            }
            setOnCompletionListener { mp ->
                try {
                    seekTo(item.trimStartMs.toInt())
                    start()
                } catch (ex: Exception) {}
            }

            if (item.videoUriStr.startsWith("content://")) {
                setVideoURI(Uri.parse(item.videoUriStr))
            } else {
                setVideoPath(item.videoUriStr)
            }
        }

        editorPreviewContainer.addView(editorVideoView)
        editorPreviewContainer.addView(editorNightFilter)
        editorPreviewContainer.addView(editorNightIndicator)

        handler.post(previewPlaybackCheckRunnable)
    }

    private fun bindEditorViewData(item: WallpaperItem) {
        val duration = if (viewModel.videoDurationMs.value > 0L) viewModel.videoDurationMs.value else 10000L

        // Set text labels
        editorTrimStartLabel.text = "Старт: ${formatTime(item.trimStartMs)}"
        editorTrimEndLabel.text = "Конец: ${formatTime(item.trimEndMs)}"

        // Set seek bars percentage progress
        val startPct = (item.trimStartMs.toFloat() / duration * 100f).toInt().coerceIn(0, 100)
        val endPct = (item.trimEndMs.toFloat() / duration * 100f).toInt().coerceIn(0, 100)

        editorTrimStartSeekBar.progress = startPct
        editorTrimEndSeekBar.progress = endPct

        // Set switches
        editorLoopSwitch.isChecked = item.isLooping
        editorNightSwitch.isChecked = item.nightModeEnabled

        // Set Night Dim controls
        editorNightDimLayout.visibility = if (item.nightModeEnabled) View.VISIBLE else View.GONE
        val factorPct = (item.nightBrightnessReduction * 100f).toInt().coerceIn(10, 80)
        editorNightDimSeekBar.progress = factorPct
        editorNightDimPctLabel.text = "$factorPct%"

        // Display dim filter
        syncDynamicNightModeOverlay()

        // Sync cover frame selector progress
        val span = (item.trimEndMs - item.trimStartMs).coerceAtLeast(1L)
        // Assume default cover time is start time if uninitialized
        val coverRelTime = (item.trimStartMs).coerceAtLeast(0L)
        val coverProgress = (coverRelTime.toFloat() / span * 100f).toInt().coerceIn(0, 100)
        editorCoverTimeSeekBar.progress = coverProgress
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        try {
            editorVideoView?.stopPlayback()
        } catch (ex: Exception) {}
    }


    // UTILITIES & DECORATION HELPERS
    private fun dp(dpValue: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dpValue.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    private fun View.padding(l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) {
        setPadding(dp(l), dp(t), dp(r), dp(b))
    }

    private fun TextView.textColor(c: Int) {
        setTextColor(c)
    }

    private fun createRoundedGradientDrawable(solidColor: Int, strokeColor: Int, cornerDp: Int, strokeWidthDp: Int = 0): GradientDrawable {
        return GradientDrawable().apply {
            setColor(solidColor)
            cornerRadius = dp(cornerDp).toFloat()
            if (strokeWidthDp > 0) {
                setStroke(dp(strokeWidthDp), strokeColor)
            }
        }
    }

    private fun View.setElevationValue(elevationDp: Int) {
        elevation = dp(elevationDp).toFloat()
    }

    private fun loadThumbnailAsync(imageView: ImageView, path: String?) {
        if (path == null) {
            imageView.setImageResource(android.R.drawable.presence_video_online)
            imageView.imageTintList = ColorStateList.valueOf(CyberMuted)
            return
        }
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val file = File(path)
                if (file.exists()) {
                    val bitmap = android.graphics.BitmapFactory.decodeFile(path)
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        imageView.setImageBitmap(bitmap)
                        imageView.imageTintList = null
                    }
                } else {
                    withContext(kotlinx.coroutines.Dispatchers.Main) {
                        imageView.setImageResource(android.R.drawable.presence_video_online)
                        imageView.imageTintList = ColorStateList.valueOf(CyberMuted)
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Error loading thumbnail file", e)
            }
        }
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result.substring(cut + 1)
            }
        }
        return result
    }

    private fun launchWallpaperSelection(context: Context) {
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(context, VideoWallpaperService::class.java)
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (ex: Exception) {
                Log.e("MainActivity", "Failed to boot live wallpaper choice dialog", ex)
            }
        }
    }
}
