package com.example

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var galleryFragment: GalleryFragment? = null
    private var aboutFragment: AboutFragment? = null
    private var settingsFragment: SettingsFragment? = null

    private var currentTabId: Int = R.id.nav_gallery

    companion object {
        private const val TAG_GALLERY = "gallery"
        private const val TAG_ABOUT = "about"
        private const val TAG_SETTINGS = "settings"
        private const val KEY_SELECTED_TAB = "key_selected_tab"
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable hardware acceleration at the window level
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

        super.onCreate(savedInstanceState)
        LocaleManager.applyLocale(this)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup custom toolbar
        setSupportActionBar(binding.toolbar)

        val initialTab = savedInstanceState?.getInt(KEY_SELECTED_TAB) ?: R.id.nav_gallery
        currentTabId = initialTab

        initInitialFragment(savedInstanceState, initialTab)

        binding.bottomNavigation.selectedItemId = initialTab
        syncToolbarTitle()

        setupBottomNavigation()
        setupBackPress()

        // Defer pre-warming of non-visible tabs to after the first frame has rendered
        binding.root.post {
            preloadOtherTabs()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_TAB, currentTabId)
    }

    private fun initInitialFragment(savedInstanceState: Bundle?, initialTab: Int) {
        val fm = supportFragmentManager

        // Reconnect existing fragments if restored from state
        galleryFragment = fm.findFragmentByTag(TAG_GALLERY) as? GalleryFragment
        aboutFragment = fm.findFragmentByTag(TAG_ABOUT) as? AboutFragment
        settingsFragment = fm.findFragmentByTag(TAG_SETTINGS) as? SettingsFragment

        val target = when (initialTab) {
            R.id.nav_about -> (aboutFragment ?: AboutFragment()).also { aboutFragment = it }
            R.id.nav_settings -> (settingsFragment ?: SettingsFragment()).also { settingsFragment = it }
            else -> (galleryFragment ?: GalleryFragment()).also { galleryFragment = it }
        }

        val tag = when (initialTab) {
            R.id.nav_about -> TAG_ABOUT
            R.id.nav_settings -> TAG_SETTINGS
            else -> TAG_GALLERY
        }

        val transaction = fm.beginTransaction().setReorderingAllowed(true)
        if (!target.isAdded) {
            transaction.add(R.id.fragment_container, target, tag)
        }
        transaction.show(target)

        // If other fragments exist from state restoration, hide them
        listOfNotNull(galleryFragment, aboutFragment, settingsFragment).forEach { f ->
            if (f !== target && f.isAdded) {
                transaction.hide(f)
            }
        }
        transaction.commitNow()
    }

    private fun preloadOtherTabs() {
        if (isFinishing || isDestroyed) return
        val fm = supportFragmentManager
        val transaction = fm.beginTransaction().setReorderingAllowed(true)
        var needsCommit = false

        if (aboutFragment == null && fm.findFragmentByTag(TAG_ABOUT) == null) {
            val about = AboutFragment().also { aboutFragment = it }
            transaction.add(R.id.fragment_container, about, TAG_ABOUT).hide(about)
            needsCommit = true
        }
        if (settingsFragment == null && fm.findFragmentByTag(TAG_SETTINGS) == null) {
            val settings = SettingsFragment().also { settingsFragment = it }
            transaction.add(R.id.fragment_container, settings, TAG_SETTINGS).hide(settings)
            needsCommit = true
        }

        if (needsCommit) {
            transaction.commitAllowingStateLoss()
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            if (menuItem.itemId != currentTabId) {
                showTab(menuItem.itemId)
            }
            true
        }

        binding.bottomNavigation.setOnItemReselectedListener {
            // Instant response - no reloading on reselection
        }
    }

    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (currentTabId != R.id.nav_gallery) {
                    binding.bottomNavigation.selectedItemId = R.id.nav_gallery
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    fun showTab(tabId: Int) {
        currentTabId = tabId
        val fm = supportFragmentManager

        val target: Fragment = when (tabId) {
            R.id.nav_about -> {
                (aboutFragment ?: (fm.findFragmentByTag(TAG_ABOUT) as? AboutFragment)
                    ?: AboutFragment()).also { aboutFragment = it }
            }
            R.id.nav_settings -> {
                (settingsFragment ?: (fm.findFragmentByTag(TAG_SETTINGS) as? SettingsFragment)
                    ?: SettingsFragment()).also { settingsFragment = it }
            }
            else -> {
                (galleryFragment ?: (fm.findFragmentByTag(TAG_GALLERY) as? GalleryFragment)
                    ?: GalleryFragment()).also { galleryFragment = it }
            }
        }

        val tag = when (tabId) {
            R.id.nav_about -> TAG_ABOUT
            R.id.nav_settings -> TAG_SETTINGS
            else -> TAG_GALLERY
        }

        val transaction = fm.beginTransaction().setReorderingAllowed(true)

        if (!target.isAdded) {
            transaction.add(R.id.fragment_container, target, tag)
        }
        transaction.show(target)

        listOfNotNull(galleryFragment, aboutFragment, settingsFragment).forEach { f ->
            if (f !== target && f.isAdded && !f.isHidden) {
                transaction.hide(f)
            }
        }

        transaction.commitNowAllowingStateLoss()
        syncToolbarTitle()
    }

    private fun syncToolbarTitle() {
        val title = when (currentTabId) {
            R.id.nav_gallery -> getText(R.string.toolbar_gallery)
            R.id.nav_about -> getText(R.string.toolbar_about)
            R.id.nav_settings -> getText(R.string.toolbar_settings)
            else -> getText(R.string.toolbar_gallery)
        }
        supportActionBar?.title = title
        binding.toolbar.title = title
    }

    fun onLanguageChanged() {
        syncToolbarTitle()
        updateBottomNavTitles()

        galleryFragment?.updateTexts()
        aboutFragment?.updateTexts()
        settingsFragment?.updateTexts()
    }

    private fun updateBottomNavTitles() {
        binding.bottomNavigation.menu.findItem(R.id.nav_gallery)?.title = getString(R.string.tab_gallery)
        binding.bottomNavigation.menu.findItem(R.id.nav_about)?.title = getString(R.string.tab_about)
        binding.bottomNavigation.menu.findItem(R.id.nav_settings)?.title = getString(R.string.tab_settings)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        LocaleManager.applyLocale(this)
        onLanguageChanged()
    }
}
