package com.example

import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    companion object {
        const val TAB_GALLERY = 1
        const val TAB_ABOUT = 2
        const val TAB_SETTINGS = 3
    }

    private lateinit var binding: ActivityMainBinding

    private val galleryFragment by lazy { GalleryFragment() }
    private val aboutFragment by lazy { AboutFragment() }
    private val settingsFragment by lazy { SettingsFragment() }
    
    private var activeFragment: Fragment? = null
    private var currentTab: Int = TAB_GALLERY

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enforce dark theme across the entire application
        if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_YES) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        }

        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup custom toolbar
        setSupportActionBar(binding.toolbar)

        setupFragments(savedInstanceState)
        setupFloatingNavigation()

        // Handle custom back action: return to Gallery if on other screens
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentTab != TAB_GALLERY) {
                    selectTab(TAB_GALLERY)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun setupFragments(savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            val title = getText(R.string.toolbar_gallery)
            supportActionBar?.title = title
            binding.toolbar.title = title

            supportFragmentManager.beginTransaction()
                .add(R.id.fragment_container, galleryFragment, "gallery")
                .commit()
            activeFragment = galleryFragment
            currentTab = TAB_GALLERY
            updateNavUi(TAB_GALLERY)
        } else {
            currentTab = savedInstanceState.getInt("current_tab", TAB_GALLERY)
            activeFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) 
                ?: galleryFragment
            updateNavUi(currentTab)
            syncToolbarTitle()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("current_tab", currentTab)
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        syncToolbarTitle()
    }

    private fun syncToolbarTitle() {
        val title = when (currentTab) {
            TAB_GALLERY -> getText(R.string.toolbar_gallery)
            TAB_ABOUT -> getText(R.string.toolbar_about)
            TAB_SETTINGS -> getText(R.string.toolbar_settings)
            else -> getText(R.string.toolbar_gallery)
        }
        supportActionBar?.title = title
        binding.toolbar.title = title
    }

    private fun setupFloatingNavigation() {
        binding.navItemGallery.setOnClickListener {
            selectTab(TAB_GALLERY)
        }

        binding.navItemAbout.setOnClickListener {
            selectTab(TAB_ABOUT)
        }

        binding.navItemSettings.setOnClickListener {
            selectTab(TAB_SETTINGS)
        }
    }

    private fun selectTab(tab: Int) {
        if (currentTab == tab && activeFragment != null) return

        currentTab = tab
        updateNavUi(tab)

        val targetFragment = when (tab) {
            TAB_GALLERY -> galleryFragment
            TAB_ABOUT -> aboutFragment
            TAB_SETTINGS -> settingsFragment
            else -> galleryFragment
        }

        val title = when (tab) {
            TAB_GALLERY -> getText(R.string.toolbar_gallery)
            TAB_ABOUT -> getText(R.string.toolbar_about)
            TAB_SETTINGS -> getText(R.string.toolbar_settings)
            else -> getText(R.string.toolbar_gallery)
        }

        switchFragment(targetFragment, title)
    }

    private fun updateNavUi(selectedTab: Int) {
        val colorActive = ContextCompat.getColor(this, R.color.nav_active_tint)
        val colorInactiveIcon = ContextCompat.getColor(this, R.color.nav_inactive_tint)
        val colorInactiveText = ContextCompat.getColor(this, R.color.nav_inactive_text)

        // 1. Gallery item
        val isGallery = (selectedTab == TAB_GALLERY)
        binding.navItemGallery.setBackgroundResource(
            if (isGallery) R.drawable.bg_nav_active_pill else 0
        )
        binding.ivNavGallery.setColorFilter(if (isGallery) colorActive else colorInactiveIcon)
        binding.tvNavGallery.setTextColor(if (isGallery) colorActive else colorInactiveText)
        binding.tvNavGallery.setTypeface(null, if (isGallery) Typeface.BOLD else Typeface.NORMAL)

        // 2. About item
        val isAbout = (selectedTab == TAB_ABOUT)
        binding.navItemAbout.setBackgroundResource(
            if (isAbout) R.drawable.bg_nav_active_pill else 0
        )
        binding.ivNavAbout.setColorFilter(if (isAbout) colorActive else colorInactiveIcon)
        binding.tvNavAbout.setTextColor(if (isAbout) colorActive else colorInactiveText)
        binding.tvNavAbout.setTypeface(null, if (isAbout) Typeface.BOLD else Typeface.NORMAL)

        // 3. Settings item
        val isSettings = (selectedTab == TAB_SETTINGS)
        binding.navItemSettings.setBackgroundResource(
            if (isSettings) R.drawable.bg_nav_active_pill else 0
        )
        binding.ivNavSettings.setColorFilter(if (isSettings) colorActive else colorInactiveIcon)
        binding.tvNavSettings.setTextColor(if (isSettings) colorActive else colorInactiveText)
        binding.tvNavSettings.setTypeface(null, if (isSettings) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun switchFragment(target: Fragment, title: CharSequence) {
        if (activeFragment === target) return

        supportActionBar?.title = title
        binding.toolbar.title = title

        val transaction = supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)

        activeFragment?.let { transaction.hide(it) }

        if (!target.isAdded) {
            transaction.add(R.id.fragment_container, target)
        } else {
            transaction.show(target)
        }

        transaction.commit()
        activeFragment = target
    }
}
