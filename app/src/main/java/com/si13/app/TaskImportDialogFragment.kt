package com.si13.app

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class TaskImportDialogFragment : DialogFragment() {
    private lateinit var taskRepository: TaskRepository

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        taskRepository = TaskRepository.create(requireContext())

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.import_local_tasks_title)
            .setMessage(R.string.import_local_tasks_message)
            .setPositiveButton(R.string.import_local_tasks_add, null)
            .setNegativeButton(R.string.import_local_tasks_discard, null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        val dialog = requireDialog() as AlertDialog

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            lifecycleScope.launch {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = false

                when (taskRepository.importLocalTasksToRemote()) {
                    is TaskImportResult.Imported,
                    TaskImportResult.NoLocalTasks -> {
                        publishImportResult()
                        dismiss()
                    }

                    is TaskImportResult.Failure -> {
                        Toast.makeText(
                            requireContext(),
                            R.string.import_local_tasks_failed,
                            Toast.LENGTH_LONG
                        ).show()
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = true
                    }
                }
            }
        }

        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            lifecycleScope.launch {
                taskRepository.discardLocalTasks()
                publishImportResult()
                dismiss()
            }
        }
    }

    companion object {
        private const val TAG = "TaskImportDialog"
        const val IMPORT_RESULT_KEY = "task_import_result"

        suspend fun showIfLocalTasks(context: Context, fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) != null) {
                return
            }

            val repository = TaskRepository.create(context)
            if (repository.hasLocalTasks()) {
                TaskImportDialogFragment().show(fragmentManager, TAG)
            }
        }
    }

    private fun publishImportResult() {
        parentFragmentManager.setFragmentResult(IMPORT_RESULT_KEY, Bundle.EMPTY)
    }
}
