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

    private val galleryFragment by lazy { GalleryFragment() }
    private val aboutFragment by lazy { AboutFragment() }
    private val settingsFragment by lazy { SettingsFragment() }
    private var activeFragment: Fragment? = null

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
        setupBottomNavigation()

        // Handle custom back action: return to Gallery if on other screens
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.bottomNavigation.selectedItemId != R.id.nav_gallery) {
                    binding.bottomNavigation.selectedItemId = R.id.nav_gallery
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
        } else {
            // Restore active fragment reference from FragmentManager
            activeFragment = supportFragmentManager.findFragmentById(R.id.fragment_container) 
                ?: galleryFragment
            syncToolbarTitle()
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
            val targetFragment = when (menuItem.itemId) {
                R.id.nav_gallery -> galleryFragment
                R.id.nav_about -> aboutFragment
                R.id.nav_settings -> settingsFragment
                else -> galleryFragment
            }
            val title = when (menuItem.itemId) {
                R.id.nav_gallery -> getText(R.string.toolbar_gallery)
                R.id.nav_about -> getText(R.string.toolbar_about)
                R.id.nav_settings -> getText(R.string.toolbar_settings)
                else -> getText(R.string.toolbar_gallery)
            }

            switchFragment(targetFragment, title)
            true
        }

        binding.bottomNavigation.setOnItemReselectedListener {
            // Do nothing on reselection to maintain instant performance
        }
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
