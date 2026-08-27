package com.si13.forgetty

import android.app.Dialog
import android.content.res.Configuration
import android.content.res.ColorStateList
import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.graphics.Paint
import android.view.View
import android.view.Window
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Date
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TaskDetailsBottomSheet : BottomSheetDialogFragment() {
    private lateinit var task: Task
    private lateinit var title: TextInputEditText
    private lateinit var priorityValue: TextView
    private lateinit var priorityIcon: ImageView
    private lateinit var dueValue: TextView
    private lateinit var duePill: View
    private lateinit var dueIcon: ImageView
    private lateinit var clearDueDate: ImageButton
    private lateinit var notes: TextInputEditText
    private lateinit var repeatValue: TextView
    private lateinit var saveStatus: TextView
    private lateinit var tagGroup: ChipGroup
    private lateinit var tagEditor: View
    private lateinit var tagEditButton: TextView
    private lateinit var newTag: TextInputEditText
    private val tags = linkedSetOf<String>()
    private var editingTags = false
    private var priority = TaskPriority.NONE
    private var dueDate: String? = null
    private var repeatRule = TaskRepeatRule.NONE
    private var textSaveJob: Job? = null
    private var binding = true
    private var skipSaveOnDismiss = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        task = taskFromArguments()
        priority = task.priority
        dueDate = task.dueDate
        repeatRule = task.repeatRule
        tags += task.tags

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
        renderTitleState()
        sheet.findViewById<View>(R.id.task_details_close).setOnClickListener { dismiss() }
        sheet.findViewById<View>(R.id.task_details_toggle_complete).setOnClickListener {
            task = task.copy(completed = !task.completed)
            renderTitleState()
            save()
        }
        notes = sheet.findViewById(R.id.task_details_notes)
        notes.setText(task.note)
        saveStatus = sheet.findViewById(R.id.task_details_save_status)
        title.doAfterTextChanged { if (!binding) scheduleTextSave() }
        notes.doAfterTextChanged { if (!binding) scheduleTextSave() }

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

        repeatValue = sheet.findViewById(R.id.task_details_repeat_value)
        sheet.findViewById<View>(R.id.task_details_repeat).setOnClickListener { showRepeatChoices() }

        sheet.findViewById<TextView>(R.id.task_details_created).text = getString(
            R.string.created_format,
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(task.createdAt))
        )
        sheet.findViewById<TextView>(R.id.task_details_updated).text = getString(
            R.string.updated_format,
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(task.updatedAt))
        )
        bindMetadata(sheet)
        sheet.findViewById<MaterialButton>(R.id.task_details_complete).apply {
            setText(if (task.completed) R.string.mark_as_active else R.string.mark_as_complete)
            setOnClickListener {
                task = task.copy(completed = !task.completed)
                (parentFragment as? HomeFragment)?.updateTaskFromDetails(task)
                dismiss()
            }
        }
        sheet.findViewById<MaterialButton>(R.id.task_details_delete).setOnClickListener {
            if (ForgettyPreferences.create(requireContext()).confirmBeforeDeleting) {
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.delete_task)
                    .setMessage(R.string.delete_task_confirmation)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.delete) { _, _ -> deleteTask() }
                    .show()
            } else deleteTask()
        }
        sheet.findViewById<MaterialButton>(R.id.task_details_duplicate).setOnClickListener {
            skipSaveOnDismiss = true
            (parentFragment as? HomeFragment)?.duplicateTaskFromDetails(currentTask())
            dismiss()
        }
        sheet.findViewById<MaterialButton>(R.id.task_details_share).setOnClickListener { shareTask() }
        tagGroup = sheet.findViewById(R.id.task_details_tag_group)
        tagEditor = sheet.findViewById(R.id.task_details_tag_editor)
        tagEditButton = sheet.findViewById(R.id.task_details_tags_edit)
        newTag = sheet.findViewById(R.id.task_details_new_tag)
        tagEditButton.setOnClickListener {
            editingTags = !editingTags
            renderTags()
        }
        sheet.findViewById<MaterialButton>(R.id.task_details_add_tag).setOnClickListener {
            newTag.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let { value ->
                tags += value
                newTag.text?.clear()
                renderTags()
                save()
            }
        }
        renderTags()
        renderPriority()
        renderDueDate()
        renderRepeat()
        binding = false
    }

    private fun bindMetadata(sheet: View) {
        val subtasks = sheet.findViewById<LinearLayout>(R.id.task_details_subtasks)
        task.subtasks.forEach { item ->
            subtasks.addView(CheckBox(requireContext()).apply {
                text = item.title
                isChecked = item.completed
                minimumHeight = dp(48)
                setOnCheckedChangeListener { _, checked ->
                    task = task.copy(subtasks = task.subtasks.map { if (it.id == item.id) it.copy(completed = checked) else it })
                    save()
                }
            })
        }
        sheet.findViewById<TextView>(R.id.task_details_list).apply {
            text = task.listName
            setOnClickListener { showListChoices() }
        }
        renderTags()
        sheet.findViewById<TextView>(R.id.task_details_attachments).apply {
            isVisible = task.attachments.isNotEmpty()
            text = getString(R.string.attachment_count, task.attachments.size)
        }
        sheet.findViewById<TextView>(R.id.task_details_location).apply {
            isVisible = task.locationReminder != null
            text = task.locationReminder?.let { getString(R.string.location_reminder_summary, it.label) }
        }
    }

    private fun scheduleTextSave() {
        saveStatus.setText(R.string.saving)
        textSaveJob?.cancel()
        textSaveJob = lifecycleScope.launch {
            delay(600)
            save()
            saveStatus.setText(R.string.saved)
        }
    }

    private fun showRepeatChoices() {
        val rules = listOf(TaskRepeatRule.NONE, TaskRepeatRule.DAILY, TaskRepeatRule.WEEKDAYS, TaskRepeatRule.WEEKLY, TaskRepeatRule.MONTHLY, TaskRepeatRule.YEARLY)
        val labels = rules.map(::repeatLabel).toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.repeat)
            .setSingleChoiceItems(labels, rules.indexOf(repeatRule)) { dialog, which ->
                repeatRule = rules[which]
                renderRepeat()
                save()
                dialog.dismiss()
            }.show()
    }

    private fun repeatLabel(rule: TaskRepeatRule) = getString(when (rule) {
        TaskRepeatRule.NONE -> R.string.does_not_repeat
        TaskRepeatRule.DAILY -> R.string.repeat_daily
        TaskRepeatRule.WEEKDAYS -> R.string.repeat_weekdays
        TaskRepeatRule.WEEKLY -> R.string.repeat_weekly
        TaskRepeatRule.MONTHLY -> R.string.repeat_monthly
        TaskRepeatRule.YEARLY -> R.string.repeat_yearly
        TaskRepeatRule.CUSTOM -> R.string.custom
    })

    private fun renderRepeat() { if (::repeatValue.isInitialized) repeatValue.text = repeatLabel(repeatRule) }

    private fun renderTags() {
        if (!::tagGroup.isInitialized) return
        tagGroup.removeAllViews()
        tags.forEach { value ->
            tagGroup.addView(Chip(requireContext()).apply {
                text = if (editingTags) "$value  ×" else value
                isClickable = editingTags
                isCheckable = false
                setTextColor(requireContext().getColor(R.color.forgetty_text_primary))
                chipBackgroundColor = ColorStateList.valueOf(requireContext().getColor(R.color.home_accent_container))
                setOnClickListener {
                    if (editingTags) {
                        tags.remove(value)
                        renderTags()
                        save()
                    }
                }
            })
        }
        tagEditor.isVisible = editingTags
        tagEditButton.text = getString(if (editingTags) R.string.done else R.string.edit)
    }

    private fun showListChoices() {
        val definitions = TaskListStore.create(requireContext()).getLists()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.choose_list)
            .setSingleChoiceItems(definitions.map { it.name }.toTypedArray(), definitions.indexOfFirst { it.name == task.listName }) { dialog, which ->
                task = currentTask().copy(listName = definitions[which].name, listId = definitions[which].id)
                this@TaskDetailsBottomSheet.dialog
                    ?.findViewById<TextView>(R.id.task_details_list)?.text = task.listName
                save()
                dialog.dismiss()
            }.show()
    }

    private fun deleteTask() {
        skipSaveOnDismiss = true
        (parentFragment as? HomeFragment)?.deleteTaskFromDetails(task)
        dismiss()
    }

    private fun shareTask() {
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_task_subject))
            putExtra(Intent.EXTRA_TEXT, buildString {
                append(task.text)
                task.dueDate?.let { append("\n").append(getString(R.string.due_date_format, it)) }
                task.note.takeIf { it.isNotBlank() }?.let { append("\n\n").append(it) }
            })
        }, getString(R.string.share)))
    }

    private fun renderPriority() {
        priorityValue.text = priorityLabel(priority)
        val presentation = priorityPresentation(priority)
        priorityValue.setTextColor(requireContext().getColor(presentation.colorRes))
        priorityIcon.setColorFilter(requireContext().getColor(presentation.colorRes))
        priorityValue.setBackgroundResource(presentation.backgroundRes)
    }

    private fun renderTitleState() {
        if (!::title.isInitialized) return
        title.paintFlags = if (task.completed) {
            title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
        title.setTextColor(requireContext().getColor(
            if (task.completed) R.color.forgetty_text_secondary else R.color.forgetty_text_primary
        ))
        dialog?.findViewById<ImageButton>(R.id.task_details_toggle_complete)?.apply {
            setImageResource(if (task.completed) R.drawable.ic_check_circle else R.drawable.ic_task_checkbox_unchecked)
            contentDescription = getString(if (task.completed) R.string.mark_as_active else R.string.mark_as_complete)
        }
        dialog?.findViewById<MaterialButton>(R.id.task_details_complete)?.setText(
            if (task.completed) R.string.mark_as_active else R.string.mark_as_complete
        )
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
        task = currentTask()
        (parentFragment as? HomeFragment)?.persistTaskFromDetails(task)
    }

    private fun currentTask(): Task {
        val value = if (::title.isInitialized) title.text?.toString()?.trim().orEmpty() else task.text
        return task.copy(
            text = if (value.isEmpty()) task.text else value,
            priority = priority,
            dueDate = dueDate,
            note = if (::notes.isInitialized) notes.text?.toString().orEmpty() else task.note,
            repeatRule = repeatRule,
            tags = tags.toList()
        )
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        textSaveJob?.cancel()
        if (::title.isInitialized && !skipSaveOnDismiss) save()
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
        dueTimeMinutes = requireArguments().getInt("dueTime", -1).takeIf { it >= 0 },
        listName = requireArguments().getString("list").orEmpty().ifBlank { DEFAULT_TASK_LIST },
        note = requireArguments().getString("note").orEmpty(),
        reminderAt = requireArguments().getLong("reminder", -1L).takeIf { it >= 0L },
        repeatRule = TaskRepeatRule.fromStorageValue(requireArguments().getString("repeat")),
        listId = requireArguments().getString("listId"),
        tags = TaskFieldCodec.decodeStrings(requireArguments().getString("tags")),
        subtasks = TaskFieldCodec.decodeSubtasks(requireArguments().getString("subtasks")),
        attachments = TaskFieldCodec.decodeAttachments(requireArguments().getString("attachments")),
        locationReminder = TaskFieldCodec.decodeLocation(requireArguments().getString("location")),
        completedAt = requireArguments().getLong("completedAt", -1L).takeIf { it >= 0L }
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
                    "dueTime" to (task.dueTimeMinutes ?: -1),
                    "list" to task.listName,
                    "listId" to task.listId,
                    "note" to task.note,
                    "reminder" to (task.reminderAt ?: -1L),
                    "repeat" to task.repeatRule.storageValue,
                    "tags" to TaskFieldCodec.encodeStrings(task.tags),
                    "subtasks" to TaskFieldCodec.encodeSubtasks(task.subtasks),
                    "attachments" to TaskFieldCodec.encodeAttachments(task.attachments),
                    "location" to TaskFieldCodec.encodeLocation(task.locationReminder),
                    "completedAt" to (task.completedAt ?: -1L)
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
