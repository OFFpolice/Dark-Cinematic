package com.example

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import com.example.databinding.FragmentSettingsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateLanguageSummary()
        setupClickListeners()
    }

    private fun updateLanguageSummary() {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val summaryText = if (currentLocales.isEmpty) {
            getString(R.string.lang_system)
        } else {
            when (currentLocales.get(0)?.language) {
                "en" -> getString(R.string.lang_en)
                "ru" -> getString(R.string.lang_ru)
                "uk" -> getString(R.string.lang_uk)
                else -> getString(R.string.lang_system)
            }
        }
        binding.tvLanguageSummary.text = summaryText
    }

    private fun setupClickListeners() {
        // Language selector dialog
        binding.itemSettingLanguage.setOnClickListener {
            showLanguageSelectionDialog()
        }

        // Open Wallpaper Manager
        binding.itemSettingWallpaper.setOnClickListener {
            openSystemWallpaperSettings()
        }

        // Open App System Settings / Permissions
        binding.itemSettingPermissions.setOnClickListener {
            openAppSettings()
        }
    }

    private fun showLanguageSelectionDialog() {
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        val currentLang = if (currentLocales.isEmpty) "" else currentLocales.get(0)?.language ?: ""

        val options = arrayOf(
            getString(R.string.lang_system),
            getString(R.string.lang_en),
            getString(R.string.lang_ru),
            getString(R.string.lang_uk)
        )

        val selectedIndex = when (currentLang) {
            "en" -> 1
            "ru" -> 2
            "uk" -> 3
            else -> 0
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_select_language)
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                val langTag = when (which) {
                    1 -> "en"
                    2 -> "ru"
                    3 -> "uk"
                    else -> "system"
                }
                applyLanguage(langTag)
                updateLanguageSummary()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun applyLanguage(langTag: String) {
        val localeList = if (langTag == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(langTag)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    private fun openSystemWallpaperSettings() {
        try {
            val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    ComponentName(requireContext(), VideoWallpaperService::class.java)
                )
            }
            startActivity(intent)
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                startActivity(fallbackIntent)
            } catch (ignored: Exception) {
            }
        }
    }

    private fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", requireContext().packageName, null)
            }
            startActivity(intent)
        } catch (ignored: Exception) {
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
