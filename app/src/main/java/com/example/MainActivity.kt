package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var galleryFragment: GalleryFragment
    private lateinit var aboutFragment: AboutFragment
    private lateinit var settingsFragment: SettingsFragment
    private var activeFragment: Fragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable hardware acceleration at the window level
        window.setFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        )

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
            galleryFragment = GalleryFragment()
            aboutFragment = AboutFragment()
            settingsFragment = SettingsFragment()

            val title = getText(R.string.toolbar_gallery)
            supportActionBar?.title = title
            binding.toolbar.title = title

            // Pre-add all fragments in one batch so tab switching has 0ms inflation latency
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.fragment_container, aboutFragment, "about").hide(aboutFragment)
                .add(R.id.fragment_container, settingsFragment, "settings").hide(settingsFragment)
                .add(R.id.fragment_container, galleryFragment, "gallery")
                .commitNow()

            activeFragment = galleryFragment
        } else {
            galleryFragment = (supportFragmentManager.findFragmentByTag("gallery") as? GalleryFragment) ?: GalleryFragment()
            aboutFragment = (supportFragmentManager.findFragmentByTag("about") as? AboutFragment) ?: AboutFragment()
            settingsFragment = (supportFragmentManager.findFragmentByTag("settings") as? SettingsFragment) ?: SettingsFragment()
            activeFragment = when (binding.bottomNavigation.selectedItemId) {
                R.id.nav_gallery -> galleryFragment
                R.id.nav_about -> aboutFragment
                R.id.nav_settings -> settingsFragment
                else -> galleryFragment
            }
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
            // Instant response - no reloading on reselection
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

        transaction.commitNowAllowingStateLoss()
        activeFragment = target
    }
}
