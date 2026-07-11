package com.example

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import com.example.databinding.FragmentSettingsBinding

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

        loadCurrentSettings()
        setupListeners()
    }

    private fun loadCurrentSettings() {
        val context = requireContext()
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

        // 1. Language Loading
        val currentLocales = AppCompatDelegate.getApplicationLocales()
        if (currentLocales.isEmpty) {
            binding.rbLangSystem.isChecked = true
        } else {
            val primaryLang = currentLocales.get(0)?.language ?: ""
            when (primaryLang) {
                "en" -> binding.rbLangEn.isChecked = true
                "ru" -> binding.rbLangRu.isChecked = true
                "uk" -> binding.rbLangUk.isChecked = true
                else -> binding.rbLangSystem.isChecked = true
            }
        }

        // 2. Theme Loading
        val savedTheme = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        when (savedTheme) {
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM -> binding.rbThemeSystem.isChecked = true
            AppCompatDelegate.MODE_NIGHT_NO -> binding.rbThemeLight.isChecked = true
            AppCompatDelegate.MODE_NIGHT_YES -> binding.rbThemeDark.isChecked = true
        }
    }

    private fun setupListeners() {
        // Language Listener
        binding.rgLanguage.setOnCheckedChangeListener { _, checkedId ->
            val langTag = when (checkedId) {
                R.id.rb_lang_en -> "en"
                R.id.rb_lang_ru -> "ru"
                R.id.rb_lang_uk -> "uk"
                else -> "system"
            }
            applyLanguage(langTag)
        }

        // Theme Listener
        binding.rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val themeMode = when (checkedId) {
                R.id.rb_theme_light -> AppCompatDelegate.MODE_NIGHT_NO
                R.id.rb_theme_dark -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            applyTheme(themeMode)
        }
    }

    private fun applyLanguage(langTag: String) {
        val localeList = if (langTag == "system") {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(langTag)
        }
        AppCompatDelegate.setApplicationLocales(localeList)
    }

    private fun applyTheme(themeMode: Int) {
        val prefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs.edit().putInt("theme_mode", themeMode).apply()
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
