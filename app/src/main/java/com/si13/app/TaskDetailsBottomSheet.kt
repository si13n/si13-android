package com.si13.app

import android.app.Dialog
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.TextView
import androidx.core.os.bundleOf
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

class TaskDetailsBottomSheet : BottomSheetDialogFragment() {
    private lateinit var task: Task
    private lateinit var title: TextInputEditText
    private var priority = TaskPriority.NONE
    private var dueDate: String? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        task = Task(requireArguments().getString("id")!!, requireArguments().getString("text")!!, requireArguments().getBoolean("completed"), requireArguments().getLong("created"), requireArguments().getLong("updated"), TaskPriority.fromStorageValue(requireArguments().getString("priority")), requireArguments().getString("due"))
        priority = task.priority; dueDate = task.dueDate
        return BottomSheetDialog(requireContext()).apply {
            setContentView(R.layout.bottom_sheet_task_details)
            window?.setDimAmount(if (resources.configuration.uiMode and 0x30 == 0x20) .45f else .32f)
            bind(findViewById(com.google.android.material.R.id.design_bottom_sheet) ?: return@apply)
        }
    }

    private fun bind(sheet: View) {
        title = sheet.findViewById(R.id.task_details_title)
        val counter: TextView = sheet.findViewById(R.id.task_details_counter)
        title.setText(task.text); title.filters = arrayOf(InputFilter.LengthFilter(TaskRepository.MAX_TASK_LENGTH))
        counter.text = "${task.text.length}/${TaskRepository.MAX_TASK_LENGTH}"
        title.addTextChangedListener(SimpleTextWatcher { counter.text = "${it.length}/${TaskRepository.MAX_TASK_LENGTH}" })
        sheet.findViewById<View>(R.id.task_details_close).setOnClickListener { dismiss() }
        val priorityValue: TextView = sheet.findViewById(R.id.task_details_priority_value)
        fun priorityLabel() { priorityValue.text = priority.name.lowercase().replaceFirstChar { it.uppercase() } }
        priorityLabel()
        sheet.findViewById<View>(R.id.task_details_priority).setOnClickListener {
            val values = TaskPriority.entries.toTypedArray()
            MaterialAlertDialogBuilder(requireContext()).setSingleChoiceItems(values.map { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }.toTypedArray(), values.indexOf(priority)) { d, which -> priority = values[which]; priorityLabel(); save(); d.dismiss() }.show()
        }
        val dueValue: TextView = sheet.findViewById(R.id.task_details_due_value)
        fun dueLabel() { dueValue.text = dueDate?.let(::formatDueDate) ?: getString(R.string.no_due_date) }
        dueLabel()
        sheet.findViewById<View>(R.id.task_details_due_date).setOnClickListener { openDatePicker(::dueLabel) }
        sheet.findViewById<TextView>(R.id.task_details_created).text = getString(R.string.created_format, DateFormat.getDateTimeInstance().format(Date(task.createdAt)))
        sheet.findViewById<MaterialButton>(R.id.task_details_complete).apply { text = getString(if (task.completed) R.string.mark_as_active else R.string.mark_as_complete); setOnClickListener { (parentFragment as? HomeFragment)?.updateTaskFromDetails(task, completed = !task.completed); dismiss() } }
        sheet.findViewById<MaterialButton>(R.id.task_details_delete).setOnClickListener { (parentFragment as? HomeFragment)?.deleteTaskFromDetails(task); dismiss() }
    }
    private fun openDatePicker(onChanged: () -> Unit) {
        val selection = dueDate?.let { LocalDate.parse(it).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() } ?: MaterialDatePicker.todayInUtcMilliseconds()
        MaterialDatePicker.Builder.datePicker().setSelection(selection).setCalendarConstraints(CalendarConstraints.Builder().setStart(MaterialDatePicker.todayInUtcMilliseconds()).build()).build().also { picker -> picker.addOnPositiveButtonClickListener { dueDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString(); onChanged(); save() }; picker.show(parentFragmentManager, "due_date") }
    }
    private fun formatDueDate(value: String): String { val date = LocalDate.parse(value); val today = LocalDate.now(); return when (date) { today -> getString(R.string.today); today.plusDays(1) -> getString(R.string.tomorrow); else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant())) } }
    private fun save() { val value = title.text?.toString()?.trim().orEmpty(); (parentFragment as? HomeFragment)?.updateTaskFromDetails(task, if (value.isEmpty()) task.text else value, priority, dueDate) }
    override fun onDismiss(dialog: android.content.DialogInterface) { if (::title.isInitialized) save(); super.onDismiss(dialog) }
    companion object { fun show(manager: androidx.fragment.app.FragmentManager, task: Task) { TaskDetailsBottomSheet().apply { arguments = bundleOf("id" to task.id, "text" to task.text, "completed" to task.completed, "created" to task.createdAt, "updated" to task.updatedAt, "priority" to task.priority.storageValue, "due" to task.dueDate) }.show(manager, "task_details") } }
}
