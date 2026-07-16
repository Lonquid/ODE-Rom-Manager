package com.oderommanager.app.ui.hackworkflow

import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.oderommanager.app.databinding.DialogHackWorkflowBinding

class HackWorkflowDialog : BottomSheetDialogFragment() {

    private var _binding: DialogHackWorkflowBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HackWorkflowViewModel by viewModels()
    private var dismissCallback: (() -> Unit)? = null

    fun setOnDismissCallback(callback: () -> Unit) { dismissCallback = callback }

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> if (uri != null) viewModel.onImageSelected(requireContext(), uri) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = DialogHackWorkflowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val romId = arguments?.getLong(ARG_ROM_ID) ?: run { dismiss(); return }
        viewModel.initialize(requireContext(), romId)

        viewModel.romEntry.observe(viewLifecycleOwner) { rom ->
            if (rom == null) return@observe
            binding.tvWorkflowTitle.text = rom.displayName
            binding.tvFileName.text = rom.fileName
            binding.tvHeaderTitle.text = if (!rom.headerGameTitle.isNullOrBlank()) rom.headerGameTitle else "(none)"
            binding.tvCurrentCode.text = rom.originalGameCode ?: "(none)"
            binding.etDisplayName.setText(rom.displayName)
            if (!rom.officialName.isNullOrBlank() && rom.mismatchType == "HACK") {
                binding.labelOfficialName.visibility = View.VISIBLE
                binding.tvOfficialName.visibility = View.VISIBLE
                binding.tvOfficialName.text = "${rom.officialName} ← serial says this"
            }
        }

        viewModel.currentStep.observe(viewLifecycleOwner) { showStep(it) }

        // Image candidates — build thumbnails dynamically
        viewModel.imageCandidates.observe(viewLifecycleOwner) { candidates ->
            binding.containerCandidates.removeAllViews()
            candidates.forEach { candidate ->
                val card = layoutInflater.inflate(
                    android.R.layout.simple_list_item_1,
                    binding.containerCandidates, false
                )
                // Build candidate card manually
                val cardView = CardView(requireContext()).apply {
                    radius = 8f
                    cardElevation = 2f
                    layoutParams = ViewGroup.MarginLayoutParams(140, 120).apply {
                        marginEnd = 8
                    }
                    isClickable = true
                    isFocusable = true
                    setOnClickListener { viewModel.selectCandidate(requireContext(), candidate) }
                }
                val container = android.widget.FrameLayout(requireContext())
                val iv = ImageView(requireContext()).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                val label = TextView(requireContext()).apply {
                    text = candidate.source
                    textSize = 9f
                    setTextColor(android.graphics.Color.WHITE)
                    setBackgroundColor(0x99000000.toInt())
                    setPadding(4, 2, 4, 2)
                    layoutParams = android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        android.view.Gravity.BOTTOM
                    )
                }
                Glide.with(this).load(candidate.url).into(iv)
                container.addView(iv)
                container.addView(label)
                cardView.addView(container)
                binding.containerCandidates.addView(cardView)
            }
        }

        viewModel.searchStatus.observe(viewLifecycleOwner) { status ->
            binding.tvSearchStatus.text = status
        }

        viewModel.selectedImageUri.observe(viewLifecycleOwner) { uri ->
            if (uri != null) {
                Glide.with(this).load(uri).into(binding.ivSelectedPreview)
            }
        }

        viewModel.selectedSource.observe(viewLifecycleOwner) { source ->
            binding.tvSelectedSource.text = source ?: ""
        }

        viewModel.generatedCode.observe(viewLifecycleOwner) { code ->
            binding.tvNewCode.text = "New code: $code"
        }

        viewModel.btnApplyEnabled.observe(viewLifecycleOwner) { enabled ->
            binding.btnConfirmAndApply.isEnabled = enabled
        }

        viewModel.existingArtworkPath.observe(viewLifecycleOwner) { path ->
            if (path != null) Glide.with(this).load(Uri.parse(path)).into(binding.ivExistingArt)
        }

        viewModel.operationState.observe(viewLifecycleOwner) { state ->
            when (state) {
                is HackWorkflowViewModel.OpState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvProgress.text = state.message
                    binding.tvProgress.visibility = View.VISIBLE
                    setButtonsEnabled(false)
                }
                is HackWorkflowViewModel.OpState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvProgress.visibility = View.GONE
                    showStep(HackWorkflowViewModel.Step.COMPLETE)
                    binding.tvSuccessMessage.text = state.message
                    setButtonsEnabled(true)
                }
                is HackWorkflowViewModel.OpState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvProgress.visibility = View.GONE
                    setButtonsEnabled(true)
                    Toast.makeText(requireContext(), "Error: ${state.message}", Toast.LENGTH_LONG).show()
                }
                else -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvProgress.visibility = View.GONE
                    setButtonsEnabled(true)
                }
            }
        }

        binding.btnConfirmName.setOnClickListener {
            val name = binding.etDisplayName.text.toString().trim()
            if (name.isBlank()) { binding.etDisplayName.error = "Name required"; return@setOnClickListener }
            viewModel.confirmHeaderAndName(name)
            // Start image search with context
            val rom = viewModel.romEntry.value ?: return@setOnClickListener
            viewModel.searchForImagesWithContext(requireContext(), name, rom)
        }

        binding.btnArtCorrect.setOnClickListener { viewModel.confirmExistingArt() }
        binding.btnArtWrong.setOnClickListener { viewModel.rejectExistingArt(requireContext()) }
        binding.btnUploadOwn.setOnClickListener { imagePickerLauncher.launch("image/*") }
        binding.btnConfirmAndApply.setOnClickListener {
            val name = binding.etDisplayName.text.toString().trim()
            viewModel.applyHackModification(requireContext(), name)
        }
        binding.btnDone.setOnClickListener { dismiss() }
    }

    private fun showStep(step: HackWorkflowViewModel.Step) {
        binding.panelReviewHeader.visibility = View.GONE
        binding.panelImageCandidates.visibility = View.GONE
        binding.panelStep2aExistingArt.visibility = View.GONE
        binding.panelComplete.visibility = View.GONE
        when (step) {
            HackWorkflowViewModel.Step.REVIEW_HEADER -> binding.panelReviewHeader.visibility = View.VISIBLE
            HackWorkflowViewModel.Step.PICK_IMAGE -> binding.panelImageCandidates.visibility = View.VISIBLE
            HackWorkflowViewModel.Step.CONFIRM_EXISTING_ART -> binding.panelStep2aExistingArt.visibility = View.VISIBLE
            HackWorkflowViewModel.Step.COMPLETE -> binding.panelComplete.visibility = View.VISIBLE
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.btnConfirmName.isEnabled = enabled
        binding.btnArtCorrect.isEnabled = enabled
        binding.btnArtWrong.isEnabled = enabled
        binding.btnUploadOwn.isEnabled = enabled
        binding.btnConfirmAndApply.isEnabled = enabled && (viewModel.btnApplyEnabled.value == true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        dismissCallback?.invoke()
    }

    companion object {
        private const val ARG_ROM_ID = "rom_id"
        fun newInstance(romId: Long) = HackWorkflowDialog().apply {
            arguments = Bundle().apply { putLong(ARG_ROM_ID, romId) }
        }
    }
}
