package com.si13.app

import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class DataSyncBottomSheet : BottomSheetDialogFragment() {
    private lateinit var repository: TaskRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = TaskRepository.create(requireContext().applicationContext)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext())
        val root = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_data_sync, null)
        dialog.setContentView(root)
        configureWindow(dialog.window)
        bind(root)
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return@setOnShowListener
            sheet.setBackgroundColor(Color.TRANSPARENT)
            BottomSheetBehavior.from(sheet).apply {
                isFitToContents = true
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        return dialog
    }

    private fun configureWindow(window: Window?) {
        window ?: return
        window.navigationBarColor = requireContext().getColor(R.color.forgetty_surface)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        window.setDimAmount(0.36f)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK !=
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun bind(root: View) {
        ViewCompat.setAccessibilityPaneTitle(root, getString(R.string.data_and_sync))
        root.findViewById<View>(R.id.data_sync_close).setOnClickListener { dismiss() }
        root.findViewById<View>(R.id.data_sync_export_row).setOnClickListener { exportTasks() }
        root.findViewById<View>(R.id.data_sync_delete_all_row).setOnClickListener {
            confirmDeleteAllTasks()
        }
        root.findViewById<View>(R.id.data_sync_delete_account_row).apply {
            isEnabled = false
            isClickable = false
            isFocusable = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navigation.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private fun exportTasks() {
        lifecycleScope.launch {
            runCatching {
                val uri = TaskExporter.createJson(requireContext(), repository.getTasks())
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, getString(R.string.export_tasks)))
            }.onFailure {
                if (isAdded) showMessage(R.string.export_failed)
            }
        }
    }

    private fun confirmDeleteAllTasks() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_all_tasks_title)
            .setMessage(R.string.delete_all_tasks_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    runCatching { repository.deleteAllTasks() }
                        .onSuccess { if (isAdded) dismiss() }
                        .onFailure { if (isAdded) showMessage(R.string.tasks_error) }
                }
            }
            .show()
    }

    private fun showMessage(messageRes: Int) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(messageRes)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    companion object {
        const val TAG = "DataSyncBottomSheet"

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            DataSyncBottomSheet().show(fragmentManager, TAG)
        }
    }
}
