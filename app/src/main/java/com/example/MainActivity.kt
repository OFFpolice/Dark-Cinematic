package com.example

import android.content.Context
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.example.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        private const val TAG_GALLERY = "TAG_GALLERY"
        private const val TAG_ABOUT = "TAG_ABOUT"
        private const val TAG_SETTINGS = "TAG_SETTINGS"
        private const val KEY_SELECTED_TAB = "KEY_SELECTED_TAB"
    }

    private var currentTabId = R.id.nav_gallery

    override fun onCreate(savedInstanceState: Bundle?) {
        // Fast-path theme application before inflating views
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val savedTheme = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        if (AppCompatDelegate.getDefaultNightMode() != savedTheme) {
            AppCompatDelegate.setDefaultNightMode(savedTheme)
        }

        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup custom toolbar
        setSupportActionBar(binding.toolbar)

        currentTabId = savedInstanceState?.getInt(KEY_SELECTED_TAB, R.id.nav_gallery) ?: R.id.nav_gallery

        setupBottomNavigation()
        showTab(currentTabId)

        // Handle custom back action: return to Gallery if on other screens
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_SELECTED_TAB, currentTabId)
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            showTab(menuItem.itemId)
            true
        }

        binding.bottomNavigation.setOnItemReselectedListener {
            // Do nothing on reselection to maintain instant performance
        }
    }

    private fun showTab(tabId: Int) {
        currentTabId = tabId
        if (binding.bottomNavigation.selectedItemId != tabId) {
            binding.bottomNavigation.selectedItemId = tabId
        }

        val targetTag = when (tabId) {
            R.id.nav_gallery -> TAG_GALLERY
            R.id.nav_about -> TAG_ABOUT
            R.id.nav_settings -> TAG_SETTINGS
            else -> TAG_GALLERY
        }

        val titleRes = when (tabId) {
            R.id.nav_gallery -> R.string.toolbar_gallery
            R.id.nav_about -> R.string.toolbar_about
            R.id.nav_settings -> R.string.toolbar_settings
            else -> R.string.toolbar_gallery
        }
        val title = getText(titleRes)
        supportActionBar?.title = title
        binding.toolbar.title = title

        val fm = supportFragmentManager
        val transaction = fm.beginTransaction().setReorderingAllowed(true)

        val allTags = listOf(TAG_GALLERY, TAG_ABOUT, TAG_SETTINGS)
        for (tag in allTags) {
            val fragment = fm.findFragmentByTag(tag)
            if (tag == targetTag) {
                if (fragment == null) {
                    val newFragment = createFragmentForTag(tag)
                    transaction.add(R.id.fragment_container, newFragment, tag)
                } else {
                    transaction.show(fragment)
                }
            } else {
                if (fragment != null) {
                    transaction.hide(fragment)
                }
            }
        }
        transaction.commit()
    }

    private fun createFragmentForTag(tag: String): Fragment {
        return when (tag) {
            TAG_GALLERY -> GalleryFragment()
            TAG_ABOUT -> AboutFragment()
            TAG_SETTINGS -> SettingsFragment()
            else -> GalleryFragment()
        }
    }
}
