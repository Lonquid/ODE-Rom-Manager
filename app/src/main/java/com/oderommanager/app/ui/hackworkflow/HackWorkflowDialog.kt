package com.oderommanager.app.ui.hackworkflow

import android.net.Uri
import android.os.Bundle
import android.view.*
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.oderommanager.app.databinding.DialogHackWorkflowBinding

class HackWorkflowDialog : BottomSheetDialogFragment() {

    private var _binding: DialogHackWorkflowBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HackWorkflowViewModel by viewModels()

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) viewModel.onImageSelected(requireContext(), uri)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = DialogHackWorkflowBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val romId = arguments?.getLong(ARG_ROM_ID) ?: run { dismiss(); return }
        viewModel.initialize(requireContext(), romId)

        // Observe ROM data — populate header review panel (Fix #4)
        viewModel.romEntry.observe(viewLifecycleOwner) { rom ->
            if (rom == null) return@observe
            binding.tvWorkflowTitle.text = rom.displayName
            binding.tvFileName.text = rom.fileName
            binding.tvHeaderTitle.text = if (!rom.headerGameTitle.isNullOrBlank())
                rom.headerGameTitle else "(no title in header)"
            binding.tvCurrentCode.text = rom.originalGameCode ?: "(none)"
            binding.etDisplayName.setText(rom.displayName)
        }

        viewModel.currentStep.observe(viewLifecycleOwner) { step -> showStep(step) }

        viewModel.existingArtworkPath.observe(viewLifecycleOwner) { path ->
            if (path != null) Glide.with(this).load(path).into(binding.ivExistingArt)
        }

        viewModel.generatedCode.observe(viewLifecycleOwner) { code ->
            binding.tvNewCode.text = "New code: $code"
        }

        viewModel.selectedImageUri.observe(viewLifecycleOwner) { uri ->
            if (uri != null) {
                Glide.with(this).load(uri).into(binding.ivSelectedArt)
                binding.btnConfirmAndApply.isEnabled = true
            }
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
        }

        binding.btnArtCorrect.setOnClickListener { viewModel.confirmExistingArt() }
        binding.btnArtWrong.setOnClickListener { viewModel.rejectExistingArt() }
        binding.btnPickImage.setOnClickListener { imagePickerLauncher.launch("image/*") }
        binding.btnConfirmAndApply.setOnClickListener {
            val name = binding.etDisplayName.text.toString().trim()
            viewModel.applyHackModification(requireContext(), name)
        }
        binding.btnDone.setOnClickListener { dismiss() }
    }

    private fun showStep(step: HackWorkflowViewModel.Step) {
        binding.panelReviewHeader.visibility = View.GONE
        binding.panelStep2aExistingArt.visibility = View.GONE
        binding.panelStep2bNewArt.visibility = View.GONE
        binding.panelComplete.visibility = View.GONE
        when (step) {
            HackWorkflowViewModel.Step.REVIEW_HEADER ->
                binding.panelReviewHeader.visibility = View.VISIBLE
            HackWorkflowViewModel.Step.CONFIRM_EXISTING_ART ->
                binding.panelStep2aExistingArt.visibility = View.VISIBLE
            HackWorkflowViewModel.Step.PICK_NEW_ART ->
                binding.panelStep2bNewArt.visibility = View.VISIBLE
            HackWorkflowViewModel.Step.COMPLETE ->
                binding.panelComplete.visibility = View.VISIBLE
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        binding.btnConfirmName.isEnabled = enabled
        binding.btnArtCorrect.isEnabled = enabled
        binding.btnArtWrong.isEnabled = enabled
        binding.btnPickImage.isEnabled = enabled
        binding.btnConfirmAndApply.isEnabled = enabled && viewModel.selectedImageUri.value != null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_ROM_ID = "rom_id"
        fun newInstance(romId: Long) = HackWorkflowDialog().apply {
            arguments = Bundle().apply { putLong(ARG_ROM_ID, romId) }
        }
    }
}
