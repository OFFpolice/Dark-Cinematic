package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.databinding.FragmentAboutBinding

class AboutFragment : Fragment() {

    private var _binding: FragmentAboutBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAboutBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateTexts()

        binding.cardTelegram.setOnClickListener {
            openUrl("https://t.me/OFFpolice")
        }

        binding.cardTwitter.setOnClickListener {
            openUrl("https://x.com/OFFpolice2077")
        }

        binding.cardInstagram.setOnClickListener {
            openUrl("https://www.instagram.com/offpolice2077")
        }
    }

    fun updateTexts() {
        if (_binding == null) return

        binding.tvAboutAppName.text = getString(R.string.about_app_name)
        binding.tvAboutVersion.text = getString(R.string.app_version)
        binding.tvAboutTitle.text = getString(R.string.about_title)
        binding.tvAboutDescription.text = getString(R.string.about_description)
        binding.tvWarningTitle.text = getString(R.string.warning_title)
        binding.tvWarningDescription.text = getString(R.string.warning_description)
        binding.tvAuthorTitle.text = getString(R.string.author_title)
        binding.tvDeveloperInfo.text = getString(R.string.developer_info)
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
