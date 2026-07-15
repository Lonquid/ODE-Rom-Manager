package com.oderommanager.app.ui.library

import android.os.Bundle
import android.view.*
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.oderommanager.app.databinding.DialogArtConfirmBinding

/**
 * Full-size art preview dialog with confirm / wrong art options.
 * Shown when user taps a thumbnail that has a "?" (unverified art).
 * Also shown when tapping a verified thumbnail — just shows full size with a confirm button.
 */
class ArtConfirmDialog : DialogFragment() {

    private var _binding: DialogArtConfirmBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LibraryViewModel by viewModels({ requireParentFragment() })

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogArtConfirmBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val romId = arguments?.getLong(ARG_ROM_ID) ?: run { dismiss(); return }
        val artUriString = arguments?.getString(ARG_ART_URI) ?: run { dismiss(); return }
        val gameName = arguments?.getString(ARG_GAME_NAME) ?: ""
        val gameCode = arguments?.getString(ARG_GAME_CODE) ?: ""
        val isVerified = arguments?.getBoolean(ARG_IS_VERIFIED, false) ?: false

        binding.tvGameName.text = gameName
        binding.tvGameCode.text = "Code: $gameCode"

        Glide.with(this)
            .load(android.net.Uri.parse(artUriString))
            .into(binding.ivFullArt)

        binding.btnConfirmArt.setOnClickListener {
            viewModel.verifyArt(romId)
            dismiss()
        }

        binding.btnWrongArt.setOnClickListener {
            // Route to hack workflow to replace art
            viewModel.requestReplaceArt(romId)
            dismiss()
        }

        // If already verified, just showing full size — simplify buttons
        if (isVerified) {
            binding.btnConfirmArt.text = "Looks Good"
            binding.btnWrongArt.text = "Replace Art"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ROM_ID = "rom_id"
        private const val ARG_ART_URI = "art_uri"
        private const val ARG_GAME_NAME = "game_name"
        private const val ARG_GAME_CODE = "game_code"
        private const val ARG_IS_VERIFIED = "is_verified"

        fun newInstance(
            romId: Long,
            artUri: String,
            gameName: String,
            gameCode: String,
            isVerified: Boolean
        ) = ArtConfirmDialog().apply {
            arguments = Bundle().apply {
                putLong(ARG_ROM_ID, romId)
                putString(ARG_ART_URI, artUri)
                putString(ARG_GAME_NAME, gameName)
                putString(ARG_GAME_CODE, gameCode)
                putBoolean(ARG_IS_VERIFIED, isVerified)
            }
        }
    }
}
