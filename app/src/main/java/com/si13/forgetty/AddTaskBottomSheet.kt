package com.si13.forgetty

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

class AddTaskBottomSheet : BottomSheetDialogFragment() {
    private lateinit var repository: TaskRepository
    private lateinit var preferences: ForgettyPreferences
    private lateinit var listStore: TaskListStore
    private lateinit var title: TextInputEditText
    private lateinit var titleLayout: TextInputLayout
    private lateinit var save: MaterialButton
    private lateinit var priority: SwitchMaterial
    private lateinit var dueButton: MaterialButton
    private lateinit var repeatButton: MaterialButton
    private lateinit var notes: TextInputEditText
    private lateinit var lists: ChipGroup
    private lateinit var tags: ChipGroup
    private lateinit var subtasksContainer: LinearLayout
    private lateinit var attachmentStatus: TextView
    private var dueDate: LocalDate? = null
    private var repeatRule = TaskRepeatRule.NONE
    private var dueTimeMinutes: Int? = null
    private var reminderOffsetMinutes: Int? = null
    private var attachments = mutableListOf<TaskAttachment>()
    private val subtasks = mutableListOf<Subtask>()
    private var selectedList = DEFAULT_TASK_LIST
    private var submitting = false

    private val voiceLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let {
                title.setText(it.take(TaskRepository.MAX_TASK_LENGTH))
                title.setSelection(title.text?.length ?: 0)
            }
        }
    }
    private val fileLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::attachDocument)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = TaskRepository.create(requireContext())
        preferences = ForgettyPreferences.create(requireContext())
        listStore = TaskListStore.create(requireContext())
        selectedList = preferences.defaultList
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View =
        inflater.inflate(R.layout.bottom_sheet_add_task, container, false)

    override fun onViewCreated(view: View, state: Bundle?) {
        title = view.findViewById(R.id.task_input)
        titleLayout = view.findViewById(R.id.add_task_title_layout)
        save = view.findViewById(R.id.add_task_button)
        lists = view.findViewById(R.id.add_task_list_group)
        priority = view.findViewById(R.id.add_task_priority)
        dueButton = view.findViewById(R.id.add_task_due_button)
        repeatButton = view.findViewById(R.id.add_task_repeat_button)
        notes = view.findViewById(R.id.add_task_notes)
        tags = view.findViewById(R.id.add_task_tags)
        subtasksContainer = view.findViewById(R.id.add_task_subtasks)
        attachmentStatus = view.findViewById(R.id.add_task_attachment_status)

        populateLists()
        populateTags()
        titleLayout.setEndIconOnClickListener { startVoiceInput() }
        title.doAfterTextChanged {
            titleLayout.error = null
            save.isEnabled = !it.isNullOrBlank() && !submitting
            renderSuggestions(it?.toString().orEmpty())
        }
        title.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && save.isEnabled) {
                save.performClick(); true
            } else false
        }
        priority.setOnCheckedChangeListener { _, checked ->
            priority.text = getString(if (checked) R.string.priority_high_label else R.string.priority_none)
        }
        dueButton.setOnClickListener { showDueDateChoices() }
        repeatButton.setOnClickListener { showRepeatChoices() }
        view.findViewById<MaterialButton>(R.id.add_task_time_button).setOnClickListener { showTimePicker() }
        view.findViewById<MaterialButton>(R.id.add_task_reminder_button).setOnClickListener { showReminderChoices() }
        view.findViewById<View>(R.id.add_task_close).setOnClickListener { requestClose() }
        view.findViewById<MaterialButton>(R.id.add_task_more_options).setOnClickListener { button ->
            val advanced = view.findViewById<View>(R.id.add_task_advanced)
            advanced.isVisible = !advanced.isVisible
            (button as MaterialButton).setText(if (advanced.isVisible) R.string.less_options else R.string.more_options)
            button.icon?.level = if (advanced.isVisible) 1 else 0
        }
        view.findViewById<TextInputEditText>(R.id.add_task_subtask_input).setOnEditorActionListener { field, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && field.text?.isNotBlank() == true) {
                addSubtask(field.text.toString()); field.text = ""; true
            } else false
        }
        view.findViewById<View>(R.id.add_task_attachment).setOnClickListener {
            fileLauncher.launch(arrayOf("image/*", "application/pdf", "text/*"))
        }
        view.findViewById<View>(R.id.add_task_location).setOnClickListener { showLocationEducation() }
        save.setOnClickListener { submit() }
        title.requestFocus()
        if (arguments?.getBoolean(ARG_START_VOICE) == true) title.post { startVoiceInput() }
    }

    override fun onStart() {
        super.onStart()
        (activity as? MainActivity)?.setBottomNavigationVisible(false)
        (dialog as? BottomSheetDialog)?.let { sheet ->
            sheet.behavior.skipCollapsed = true
            sheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
            sheet.behavior.isDraggable = false
            sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.layoutParams?.height =
                ViewGroup.LayoutParams.MATCH_PARENT
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        (activity as? MainActivity)?.setBottomNavigationVisible(true)
        super.onDismiss(dialog)
    }

    private fun populateLists() {
        lists.removeAllViews()
        listStore.getLists().forEach { definition ->
            lists.addView(Chip(requireContext()).apply {
                id = View.generateViewId()
                tag = definition
                text = definition.name
                isCheckable = true
                isCheckedIconVisible = false
                isChecked = definition.name == selectedList
                setOnCheckedChangeListener { _, checked -> if (checked) selectedList = definition.name }
            })
        }
        if (lists.checkedChipId == View.NO_ID) lists.getChildAt(0)?.let { (it as Chip).isChecked = true }
    }

    private fun populateTags() {
        listOf("QA", "Personal", "Shopping", "Important").forEach { label ->
            tags.addView(Chip(requireContext()).apply {
                id = View.generateViewId(); text = label; tag = label; isCheckable = true
            })
        }
    }

    private fun renderSuggestions(value: String) {
        val group = view?.findViewById<ChipGroup>(R.id.add_task_suggestions) ?: return
        group.removeAllViews()
        val normalized = value.lowercase(Locale.getDefault())
        if (listOf("urgent", "asap", "important").any(normalized::contains)) {
            group.addSuggestion(R.string.suggest_high_priority) { priority.isChecked = true }
        }
        if ("tomorrow" in normalized) {
            group.addSuggestion(R.string.suggest_due_tomorrow) { setDueDate(LocalDate.now().plusDays(1)) }
        }
        if ("every week" in normalized || "weekly" in normalized) {
            group.addSuggestion(R.string.suggest_repeat_weekly) { setRepeat(TaskRepeatRule.WEEKLY) }
        }
    }

    private fun ChipGroup.addSuggestion(label: Int, action: () -> Unit) {
        addView(Chip(requireContext()).apply {
            setText(label); isCheckable = false; setOnClickListener { action() }
        })
    }

    private fun showDueDateChoices() {
        val today = LocalDate.now()
        val labels = arrayOf(
            getString(R.string.no_due_date), getString(R.string.today), getString(R.string.tomorrow),
            getString(R.string.this_weekend), getString(R.string.custom)
        )
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.due_date).setItems(labels) { _, index ->
            when (index) {
                0 -> setDueDate(null)
                1 -> setDueDate(today)
                2 -> setDueDate(today.plusDays(1))
                3 -> setDueDate(generateSequence(today) { it.plusDays(1) }.first { it.dayOfWeek == DayOfWeek.SATURDAY })
                else -> showCustomDate()
            }
        }.show()
    }

    private fun showCustomDate() {
        val picker = MaterialDatePicker.Builder.datePicker().setTitleText(R.string.select_date).build()
        picker.addOnPositiveButtonClickListener { millis ->
            setDueDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
        }
        picker.show(childFragmentManager, "add_task_date")
    }

    private fun setDueDate(value: LocalDate?) {
        dueDate = value
        dueButton.text = when (value) {
            null -> getString(R.string.no_due_date)
            LocalDate.now() -> getString(R.string.today)
            LocalDate.now().plusDays(1) -> getString(R.string.tomorrow)
            else -> value.toString()
        }
        view?.findViewById<ChipGroup>(R.id.add_task_due_group)?.check(
            when (value) {
                null -> R.id.add_task_due_none
                LocalDate.now() -> R.id.add_task_due_today
                LocalDate.now().plusDays(1) -> R.id.add_task_due_tomorrow
                else -> View.NO_ID
            }
        )
    }

    private fun showTimePicker() {
        if (dueDate == null) {
            Snackbar.make(requireView(), R.string.select_due_date_first, Snackbar.LENGTH_SHORT).show(); return
        }
        val current = dueTimeMinutes ?: preferences.defaultReminderMinutes
        MaterialTimePicker.Builder().setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(current / 60).setMinute(current % 60).setTitleText(R.string.add_time).build().also { picker ->
                picker.addOnPositiveButtonClickListener {
                    dueTimeMinutes = picker.hour * 60 + picker.minute
                    view?.findViewById<MaterialButton>(R.id.add_task_time_button)?.text = "%02d:%02d".format(picker.hour, picker.minute)
                }
                picker.show(childFragmentManager, "add_task_time")
            }
    }

    private fun showReminderChoices() {
        if (dueDate == null) {
            Snackbar.make(requireView(), R.string.select_due_date_first, Snackbar.LENGTH_SHORT).show(); return
        }
        val values = arrayOf(null, 0, 10, 24 * 60)
        val labels = arrayOf(getString(R.string.no_date), getString(R.string.at_due_time), getString(R.string.ten_minutes_before), getString(R.string.one_day_before))
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.reminders).setItems(labels) { _, which ->
            reminderOffsetMinutes = values[which]
            view?.findViewById<MaterialButton>(R.id.add_task_reminder_button)?.text =
                if (which == 0) getString(R.string.add_reminder) else labels[which]
        }.show()
    }

    private fun showRepeatChoices() {
        val values = listOf(TaskRepeatRule.NONE, TaskRepeatRule.DAILY, TaskRepeatRule.WEEKDAYS, TaskRepeatRule.WEEKLY, TaskRepeatRule.MONTHLY, TaskRepeatRule.YEARLY)
        val labels = values.map(::repeatLabel).toTypedArray()
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.repeat).setSingleChoiceItems(
            labels, values.indexOf(repeatRule)
        ) { dialog, index -> setRepeat(values[index]); dialog.dismiss() }.show()
    }

    private fun setRepeat(value: TaskRepeatRule) {
        repeatRule = value
        repeatButton.text = repeatLabel(value)
    }

    private fun repeatLabel(value: TaskRepeatRule): String = getString(when (value) {
        TaskRepeatRule.NONE -> R.string.does_not_repeat
        TaskRepeatRule.DAILY -> R.string.repeat_daily
        TaskRepeatRule.WEEKDAYS -> R.string.repeat_weekdays
        TaskRepeatRule.WEEKLY -> R.string.repeat_weekly
        TaskRepeatRule.MONTHLY -> R.string.repeat_monthly
        TaskRepeatRule.YEARLY -> R.string.repeat_yearly
        TaskRepeatRule.CUSTOM -> R.string.custom
    })

    private fun addSubtask(raw: String) {
        val subtask = Subtask(UUID.randomUUID().toString(), raw.trim())
        subtasks += subtask
        subtasksContainer.addView(TextView(requireContext()).apply {
            text = "•  ${subtask.title}"
            textSize = 14f
            minimumHeight = (48 * resources.displayMetrics.density).toInt()
            gravity = android.view.Gravity.CENTER_VERTICAL
            contentDescription = subtask.title
            setOnLongClickListener { subtasks.remove(subtask); subtasksContainer.removeView(this); true }
        })
    }

    private fun attachDocument(uri: Uri) {
        runCatching {
            requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val name = queryDisplayName(uri) ?: uri.lastPathSegment ?: getString(R.string.attachment)
        val type = requireContext().contentResolver.getType(uri)
        attachments += TaskAttachment(UUID.randomUUID().toString(), name, uri.toString(), type)
        attachmentStatus.text = getString(R.string.attachment_selected, name)
        attachmentStatus.isVisible = true
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor: Cursor = requireContext().contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null) ?: return null
        return cursor.use { if (it.moveToFirst()) it.getString(0) else null }
    }

    private fun showLocationEducation() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.location_reminder_title)
            .setMessage(R.string.location_reminder_explanation)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                Snackbar.make(requireView(), R.string.location_reminder_not_configured, Snackbar.LENGTH_LONG).show()
            }.show()
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.voice_task))
        }
        if (intent.resolveActivity(requireContext().packageManager) != null) voiceLauncher.launch(intent)
        else Snackbar.make(requireView(), R.string.voice_unavailable, Snackbar.LENGTH_LONG).show()
    }

    private fun submit() {
        if (submitting) return
        val cleanTitle = title.text?.toString().orEmpty().trim()
        if (cleanTitle.isBlank()) {
            titleLayout.error = getString(R.string.task_title_required)
            return
        }
        submitting = true
        save.isEnabled = false
        val selectedTags = (0 until tags.childCount).mapNotNull { index ->
            (tags.getChildAt(index) as? Chip)?.takeIf(Chip::isChecked)?.tag as? String
        }
        lifecycleScope.launch {
            try {
                val task = repository.addTask(TaskDraft(
                    text = cleanTitle,
                    priority = if (priority.isChecked) TaskPriority.HIGH else TaskPriority.NONE,
                    dueDate = dueDate?.toString(),
                    dueTimeMinutes = dueTimeMinutes,
                    listName = selectedList,
                    note = notes.text?.toString().orEmpty(),
                    reminderAt = reminderOffsetMinutes?.let { offset ->
                        dueDate?.atTime((dueTimeMinutes ?: preferences.defaultReminderMinutes) / 60, (dueTimeMinutes ?: preferences.defaultReminderMinutes) % 60)
                            ?.atZone(ZoneId.systemDefault())?.minusMinutes(offset.toLong())?.toInstant()?.toEpochMilli()
                    },
                    repeatRule = repeatRule,
                    tags = selectedTags,
                    subtasks = subtasks.toList(),
                    attachments = attachments.toList()
                ))
                parentFragmentManager.setFragmentResult(
                    RESULT_KEY,
                    Bundle().apply { putString(RESULT_TASK_ID, task.id) }
                )
                dismiss()
            } catch (exception: IllegalArgumentException) {
                titleLayout.error = exception.message
                submitting = false
                save.isEnabled = true
            } catch (exception: Exception) {
                Snackbar.make(requireView(), R.string.add_task_failed, Snackbar.LENGTH_LONG).show()
                submitting = false
                save.isEnabled = true
            }
        }
    }

    private fun requestClose() {
        val dirty = title.text?.isNotBlank() == true || notes.text?.isNotBlank() == true || subtasks.isNotEmpty()
        if (!dirty) dismiss() else MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.discard_task_title)
            .setMessage(R.string.discard_task_message)
            .setNegativeButton(R.string.keep_editing, null)
            .setPositiveButton(R.string.discard) { _, _ -> dismiss() }
            .show()
    }

    companion object {
        const val TAG = "add_task"
        const val ARG_START_VOICE = "start_voice"
        const val RESULT_KEY = "add_task_result"
        const val RESULT_TASK_ID = "task_id"
    }
}
