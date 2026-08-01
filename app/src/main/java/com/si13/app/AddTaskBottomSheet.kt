package com.si13.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import java.time.LocalDate

class AddTaskBottomSheet : BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.bottom_sheet_add_task, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val title = view.findViewById<TextInputEditText>(R.id.task_input)
        val save = view.findViewById<MaterialButton>(R.id.add_task_button)
        val lists = view.findViewById<ChipGroup>(R.id.add_task_list_group)
        val dueDates = view.findViewById<ChipGroup>(R.id.add_task_due_group)
        val priority = view.findViewById<SwitchMaterial>(R.id.add_task_priority)

        title.doAfterTextChanged { save.isEnabled = !it.isNullOrBlank() }
        title.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && save.isEnabled) {
                save.performClick()
                true
            } else {
                false
            }
        }
        save.setOnClickListener {
            val listName = when (lists.checkedChipId) {
                R.id.add_task_list_work -> "Work"
                R.id.add_task_list_shared -> "Shared"
                R.id.add_task_list_shopping -> "Shopping"
                else -> DEFAULT_TASK_LIST
            }
            val today = LocalDate.now()
            val dueDate = when (dueDates.checkedChipId) {
                R.id.add_task_due_today -> today.toString()
                R.id.add_task_due_tomorrow -> today.plusDays(1).toString()
                else -> null
            }
            (parentFragment as? HomeFragment)?.createTask(
                text = title.text?.toString().orEmpty(),
                priority = if (priority.isChecked) TaskPriority.HIGH else TaskPriority.NONE,
                dueDate = dueDate,
                listName = listName
            )
            dismiss()
        }

        title.requestFocus()
    }

    companion object {
        const val TAG = "add_task"
    }
}
