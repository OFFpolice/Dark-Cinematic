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
            replaceFragment(GalleryFragment(), getString(R.string.tab_gallery))
            binding.bottomNavigation.selectedItemId = R.id.nav_gallery
        }
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            val (fragment, title) = when (menuItem.itemId) {
                R.id.nav_gallery -> GalleryFragment() to getString(R.string.tab_gallery)
                R.id.nav_about -> AboutFragment() to getString(R.string.tab_about)
                R.id.nav_settings -> SettingsFragment() to getString(R.string.tab_settings)
                else -> GalleryFragment() to getString(R.string.tab_gallery)
            }
            replaceFragment(fragment, title)
            true
        }
    }

    private fun replaceFragment(fragment: Fragment, title: String) {
        binding.toolbar.title = title
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}
