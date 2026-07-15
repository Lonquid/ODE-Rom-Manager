package com.oderommanager.app.ui.hackworkflow

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.oderommanager.app.data.model.RomEntry
import com.oderommanager.app.databinding.FragmentHackListBinding

/**
 * Shows all GBA ROMs that may be hacks or need header assignment.
 * User selects a ROM to start the single-at-a-time hack workflow.
 */
class HackListFragment : Fragment() {

    private var _binding: FragmentHackListBinding? = null
    private val binding get() = _binding!!
    private val viewModel: HackListViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHackListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.initialize(requireContext())

        val adapter = HackRomAdapter { rom -> openHackWorkflow(rom) }
        binding.recyclerHackRoms.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerHackRoms.adapter = adapter

        viewModel.gbaRoms.observe(viewLifecycleOwner) { roms ->
            adapter.submitList(roms)
            binding.tvEmpty.visibility = if (roms.isEmpty()) View.VISIBLE else View.GONE
            binding.tvSubtitle.text = "${roms.size} GBA ROMs · ${roms.count { it.isRomHack }} flagged as hacks"
        }

        // Bulk queue: user can add items to a queue and process them sequentially
        viewModel.hackQueue.observe(viewLifecycleOwner) { queue ->
            binding.btnStartBulkQueue.visibility =
                if (queue.isNotEmpty()) View.VISIBLE else View.GONE
            binding.btnStartBulkQueue.text = "Process Queue (${queue.size})"
        }

        binding.btnStartBulkQueue.setOnClickListener {
            viewModel.startBulkQueue()
        }

        viewModel.openWorkflowFor.observe(viewLifecycleOwner) { rom ->
            if (rom != null) {
                openHackWorkflow(rom)
                viewModel.clearWorkflowRequest()
            }
        }
    }

    private fun openHackWorkflow(rom: RomEntry) {
        val dialog = HackWorkflowDialog.newInstance(rom.id)
        dialog.show(parentFragmentManager, "hack_workflow")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance() = HackListFragment()
    }
}
