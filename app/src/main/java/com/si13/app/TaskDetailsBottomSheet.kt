package com.si13.app

import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date

class TaskDetailsBottomSheet : BottomSheetDialogFragment() {
    private lateinit var task: Task
    private lateinit var title: TextInputEditText
    private lateinit var priorityValue: TextView
    private lateinit var priorityIcon: ImageView
    private lateinit var dueValue: TextView
    private lateinit var duePill: View
    private lateinit var dueIcon: ImageView
    private lateinit var clearDueDate: ImageButton
    private var priority = TaskPriority.NONE
    private var dueDate: String? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        task = taskFromArguments()
        priority = task.priority
        dueDate = task.dueDate

        return BottomSheetDialog(requireContext()).apply {
            setContentView(R.layout.bottom_sheet_task_details)
            window?.let(::configureWindow)
            setOnShowListener {
                val sheet = findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                    ?: return@setOnShowListener
                sheet.setBackgroundColor(Color.TRANSPARENT)
                BottomSheetBehavior.from(sheet).apply {
                    isFitToContents = true
                    state = BottomSheetBehavior.STATE_EXPANDED
                }
                bind(sheet)
            }
        }
    }

    private fun configureWindow(window: Window) {
        val surfaceColor = requireContext().resolveThemeColor(com.google.android.material.R.attr.colorSurface)
        window.navigationBarColor = surfaceColor
        window.setDimAmount(if (isDarkTheme()) 0.45f else 0.32f)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars = !isDarkTheme()
    }

    private fun bind(sheet: View) {
        val content = sheet.findViewById<View>(R.id.task_details_content)
        ViewCompat.setOnApplyWindowInsetsListener(content) { view, insets ->
            val navigationBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = dp(16) + navigationBars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(content)

        title = sheet.findViewById(R.id.task_details_title)
        title.setText(task.text)
        title.filters = arrayOf(InputFilter.LengthFilter(TaskRepository.MAX_TASK_LENGTH))
        sheet.findViewById<View>(R.id.task_details_close).setOnClickListener { dismiss() }

        priorityValue = sheet.findViewById(R.id.task_details_priority_value)
        priorityIcon = sheet.findViewById(R.id.task_details_priority_icon)
        sheet.findViewById<View>(R.id.task_details_priority).setOnClickListener {
            priority = if (priority == TaskPriority.NONE) TaskPriority.HIGH else TaskPriority.NONE
            renderPriority()
            save()
        }

        dueValue = sheet.findViewById(R.id.task_details_due_value)
        duePill = sheet.findViewById(R.id.task_details_due_pill)
        dueIcon = sheet.findViewById(R.id.task_details_due_icon)
        clearDueDate = sheet.findViewById(R.id.task_details_clear_due_date)
        sheet.findViewById<View>(R.id.task_details_due_date).setOnClickListener { openDatePicker() }
        clearDueDate.setOnClickListener {
            dueDate = null
            renderDueDate()
            save()
        }

        sheet.findViewById<TextView>(R.id.task_details_created).text = getString(
            R.string.created_format,
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(task.createdAt))
        )
        sheet.findViewById<MaterialButton>(R.id.task_details_complete).apply {
            setText(if (task.completed) R.string.mark_as_active else R.string.mark_as_complete)
            setOnClickListener {
                task = task.copy(completed = !task.completed)
                (parentFragment as? HomeFragment)?.updateTaskFromDetails(task)
                dismiss()
            }
        }
        sheet.findViewById<MaterialButton>(R.id.task_details_delete).setOnClickListener {
            (parentFragment as? HomeFragment)?.deleteTaskFromDetails(task)
            dismiss()
        }
        renderPriority()
        renderDueDate()
    }

    private fun renderPriority() {
        priorityValue.text = priorityLabel(priority)
        val presentation = priorityPresentation(priority)
        priorityValue.setTextColor(requireContext().getColor(presentation.colorRes))
        priorityIcon.setColorFilter(requireContext().getColor(presentation.colorRes))
        priorityValue.setBackgroundResource(presentation.backgroundRes)
    }

    private fun renderDueDate() {
        val selected = dueDate
        dueValue.text = selected?.let(::formatSheetDueDate) ?: getString(R.string.no_due_date)
        dueValue.setTextColor(requireContext().getColor(if (selected == null) R.color.text_secondary else R.color.home_accent))
        dueIcon.setColorFilter(requireContext().getColor(if (selected == null) R.color.text_secondary else R.color.home_accent))
        duePill.setBackgroundResource(if (selected == null) R.drawable.bg_task_property_chip else R.drawable.bg_task_property_chip_accent)
        clearDueDate.visibility = if (selected == null) View.GONE else View.VISIBLE
    }

    private fun openDatePicker() {
        val selection = dueDate
            ?.let(LocalDate::parse)
            ?.atStartOfDay(ZoneOffset.UTC)
            ?.toInstant()
            ?.toEpochMilli()
            ?: MaterialDatePicker.todayInUtcMilliseconds()
        MaterialDatePicker.Builder.datePicker()
            .setSelection(selection)
            .setCalendarConstraints(
                CalendarConstraints.Builder()
                    .setStart(MaterialDatePicker.todayInUtcMilliseconds())
                    .build()
            )
            .build()
            .also { picker ->
                picker.addOnPositiveButtonClickListener { utcMillis ->
                    dueDate = Instant.ofEpochMilli(utcMillis).atOffset(ZoneOffset.UTC).toLocalDate().toString()
                    renderDueDate()
                    save()
                }
                picker.show(parentFragmentManager, "due_date")
            }
    }

    private fun formatSheetDueDate(value: String): String {
        val date = LocalDate.parse(value)
        val today = LocalDate.now()
        return when (date) {
            today -> getString(R.string.today)
            today.plusDays(1) -> getString(R.string.tomorrow)
            else -> TaskDatePresentation.formatDate(date, today, resources.configuration.locales[0])
        }
    }

    private fun priorityLabel(value: TaskPriority): String = getString(
        when (value) {
            TaskPriority.NONE -> R.string.priority_none
            TaskPriority.HIGH -> R.string.priority_high_label
        }
    )

    private fun save() {
        val value = title.text?.toString()?.trim().orEmpty()
        task = task.copy(
            text = if (value.isEmpty()) task.text else value,
            priority = priority,
            dueDate = dueDate
        )
        (parentFragment as? HomeFragment)?.updateTaskFromDetails(task)
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        if (::title.isInitialized) save()
        (parentFragment as? HomeFragment)?.refreshTasksAfterDetails()
        super.onDismiss(dialog)
    }

    private fun taskFromArguments(): Task = Task(
        id = requireArguments().getString("id")!!,
        text = requireArguments().getString("text")!!,
        completed = requireArguments().getBoolean("completed"),
        createdAt = requireArguments().getLong("created"),
        updatedAt = requireArguments().getLong("updated"),
        priority = TaskPriority.fromStorageValue(requireArguments().getString("priority")),
        dueDate = requireArguments().getString("due"),
        listName = requireArguments().getString("list").orEmpty().ifBlank { DEFAULT_TASK_LIST }
    )

    private fun isDarkTheme(): Boolean = resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        fun show(manager: androidx.fragment.app.FragmentManager, task: Task) {
            TaskDetailsBottomSheet().apply {
                arguments = bundleOf(
                    "id" to task.id,
                    "text" to task.text,
                    "completed" to task.completed,
                    "created" to task.createdAt,
                    "updated" to task.updatedAt,
                    "priority" to task.priority.storageValue,
                    "due" to task.dueDate,
                    "list" to task.listName
                )
            }.show(manager, "task_details")
        }
    }
}

private fun android.content.Context.resolveThemeColor(attr: Int): Int {
    val value = android.util.TypedValue()
    theme.resolveAttribute(attr, value, true)
    return value.data
}
