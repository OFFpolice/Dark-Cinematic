package com.example

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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

        updateTexts()
        setupClickListeners()
    }

    fun updateTexts() {
        if (_binding == null) return

        binding.tvCategoryAppearance.text = getString(R.string.settings_category_appearance)
        binding.tvSettingLanguageTitle.text = getString(R.string.settings_language)
        updateLanguageSummary()

        binding.tvCategoryWallpaper.text = getString(R.string.settings_category_wallpaper)
        binding.tvSettingWallpaperTitle.text = getString(R.string.settings_system_wallpaper_title)
        binding.tvSettingWallpaperSummary.text = getString(R.string.settings_system_wallpaper_summary)

        binding.tvCategoryGeneral.text = getString(R.string.settings_category_general)
        binding.tvSettingPermissionsTitle.text = getString(R.string.settings_app_info_title)
        binding.tvSettingPermissionsSummary.text = getString(R.string.settings_app_info_summary)
        binding.tvSettingVersionTitle.text = getString(R.string.settings_version_title)
        binding.tvSettingVersionSummary.text = getString(R.string.settings_version_summary)
    }

    private fun updateLanguageSummary() {
        val currentLang = LocaleManager.getLanguage(requireContext())
        val summaryText = when (currentLang) {
            LocaleManager.LANG_EN -> getString(R.string.lang_en)
            LocaleManager.LANG_RU -> getString(R.string.lang_ru)
            LocaleManager.LANG_UK -> getString(R.string.lang_uk)
            else -> getString(R.string.lang_system)
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
        val currentLang = LocaleManager.getLanguage(requireContext())

        val options = arrayOf(
            getString(R.string.lang_system),
            getString(R.string.lang_en),
            getString(R.string.lang_ru),
            getString(R.string.lang_uk)
        )

        val selectedIndex = when (currentLang) {
            LocaleManager.LANG_EN -> 1
            LocaleManager.LANG_RU -> 2
            LocaleManager.LANG_UK -> 3
            else -> 0
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.dialog_select_language)
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                val langTag = when (which) {
                    1 -> LocaleManager.LANG_EN
                    2 -> LocaleManager.LANG_RU
                    3 -> LocaleManager.LANG_UK
                    else -> LocaleManager.LANG_SYSTEM
                }
                LocaleManager.setLanguage(requireContext(), langTag)
                (activity as? MainActivity)?.onLanguageChanged()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
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
