package com.example

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.fragment.app.Fragment
import com.example.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply saved theme preferences before view inflating or lifecycle runs
        val prefs = getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val savedTheme = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        if (AppCompatDelegate.getDefaultNightMode() != savedTheme) {
            AppCompatDelegate.setDefaultNightMode(savedTheme)
        }

        super.onCreate(savedInstanceState)
        
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setup custom toolbar title
        setSupportActionBar(binding.toolbar)

        setupBottomNavigation()

        // Load the initial Gallery fragment on clean app starts
        if (savedInstanceState == null) {
            replaceFragment(GalleryFragment(), getText(R.string.toolbar_gallery))
            binding.bottomNavigation.selectedItemId = R.id.nav_gallery
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        syncToolbarTitle()
    }

    private fun syncToolbarTitle() {
        val title = when (binding.bottomNavigation.selectedItemId) {
            R.id.nav_gallery -> getText(R.string.toolbar_gallery)
            R.id.nav_about -> getText(R.string.toolbar_about)
            R.id.nav_settings -> getText(R.string.toolbar_settings)
            else -> getText(R.string.toolbar_gallery)
        }
        supportActionBar?.title = title
        binding.toolbar.title = title
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            val (fragment, title) = when (menuItem.itemId) {
                R.id.nav_gallery -> GalleryFragment() to getText(R.string.toolbar_gallery)
                R.id.nav_about -> AboutFragment() to getText(R.string.toolbar_about)
                R.id.nav_settings -> SettingsFragment() to getText(R.string.toolbar_settings)
                else -> GalleryFragment() to getText(R.string.toolbar_gallery)
            }
            replaceFragment(fragment, title)
            true
        }
    }

    private fun replaceFragment(fragment: Fragment, title: CharSequence) {
        supportActionBar?.title = title
        binding.toolbar.title = title
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
