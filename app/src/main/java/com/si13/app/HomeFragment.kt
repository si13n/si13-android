package com.si13.app

import android.graphics.Paint
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class HomeFragment : Fragment(R.layout.fragment_home) {
    private lateinit var taskRepository: TaskRepository
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var taskInput: TextInputEditText
    private lateinit var addTaskButton: Button
    private lateinit var taskSettingsButton: ImageButton
    private lateinit var statusText: TextView
    private lateinit var emptyTasksText: TextView
    private lateinit var characterCounterText: TextView
    private lateinit var taskList: RecyclerView

    private var allTasks: List<Task> = emptyList()
    private var showCompleted = false
    private var observedUserId: String? = null
    private var hasObservedSource = false
    private var isAddingTask = false
    private var shouldScrollToTopAfterRender = false
    private var taskIdToScrollAfterRender: String? = null
    private var taskObservationJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        taskRepository = TaskRepository.create(requireContext())
        taskInput = view.findViewById(R.id.task_input)
        addTaskButton = view.findViewById(R.id.add_task_button)
        taskSettingsButton = view.findViewById(R.id.task_settings_button)
        statusText = view.findViewById(R.id.task_status_text)
        emptyTasksText = view.findViewById(R.id.empty_tasks_text)
        characterCounterText = view.findViewById(R.id.task_character_counter)

        taskAdapter = TaskAdapter(
            onCompletedChanged = { task, completed ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        taskRepository.setTaskCompleted(task, completed)
                    } catch (exception: Exception) {
                        showError()
                    }
                }
            },
            onPriorityClicked = { task ->
                cycleTaskPriority(task)
            }
        )

        taskList = view.findViewById(R.id.task_list)
        taskList.apply {
            adapter = taskAdapter
            layoutManager = LinearLayoutManager(requireContext())
        }

        configureTaskInput()
        bindActions()
        bindLoginResult()
        observeTasks()
    }

    override fun onResume() {
        super.onResume()
        val currentUserId = currentUserId()
        if (hasObservedSource && observedUserId != currentUserId) {
            taskRepository = TaskRepository.create(requireContext())
            observeTasks()
        }
    }

    private fun configureTaskInput() {
        taskInput.filters = arrayOf(InputFilter.LengthFilter(TaskRepository.MAX_TASK_LENGTH))
        updateCharacterCounter()
        updateAddButtonState()

        taskInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateCharacterCounter()
                updateAddButtonState()
            }

            override fun afterTextChanged(s: Editable?) = Unit
        })

        taskInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && addTaskButton.isEnabled) {
                submitTaskFromKeyboard()
            } else {
                false
            }
        }

        taskInput.setOnKeyListener { _, keyCode, event ->
            if (
                keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_UP &&
                addTaskButton.isEnabled
            ) {
                submitTaskFromKeyboard()
            } else {
                false
            }
        }
    }

    private fun bindActions() {
        addTaskButton.setOnClickListener {
            addTask()
        }

        taskSettingsButton.setOnClickListener {
            showTaskSettings()
        }
    }

    private fun bindLoginResult() {
        parentFragmentManager.setFragmentResultListener(
            LoginBottomSheet.LOGIN_RESULT_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            // Login can switch Home from guest Room data to authenticated Firestore data.
            taskRepository = TaskRepository.create(requireContext())
            observeTasks()
        }

        parentFragmentManager.setFragmentResultListener(
            TaskImportDialogFragment.IMPORT_RESULT_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            taskRepository = TaskRepository.create(requireContext())
            observeTasks()
        }
    }

    private fun observeTasks() {
        taskObservationJob?.cancel()
        observedUserId = currentUserId()
        hasObservedSource = true
        taskObservationJob = viewLifecycleOwner.lifecycleScope.launch {
            // Recreate collection when the active auth source changes instead of mixing data sources.
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                statusText.text = getString(R.string.tasks_loading)
                statusText.isVisible = true

                try {
                    taskRepository.observeTasks().collect { tasks ->
                        allTasks = tasks
                        statusText.isVisible = false
                        renderTasks()
                    }
                } catch (exception: Exception) {
                    showError()
                }
            }
        }
    }

    private fun addTask() {
        if (isAddingTask) {
            return
        }

        val taskText = taskInput.text?.toString().orEmpty()
        isAddingTask = true
        updateAddButtonState()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                taskRepository.addTask(taskText)
                shouldScrollToTopAfterRender = true
                taskInput.text?.clear()
            } catch (exception: IllegalArgumentException) {
                statusText.text = exception.message ?: getString(R.string.task_too_long)
                statusText.isVisible = true
            } catch (exception: Exception) {
                showError()
            } finally {
                isAddingTask = false
                updateAddButtonState()
            }
        }
    }

    private fun submitTaskFromKeyboard(): Boolean {
        // A task input is one task per line: Enter submits instead of inserting a newline.
        addTask()
        return true
    }

    private fun showTaskSettings() {
        val toggleCompletedText = getString(
            if (showCompleted) R.string.hide_completed else R.string.show_completed
        )
        val actions = listOf(
            TaskSettingsAction(
                label = toggleCompletedText,
                iconRes = if (showCompleted) R.drawable.ic_visibility_off else R.drawable.ic_visibility
            ),
            TaskSettingsAction(
                label = getString(R.string.delete_all_tasks),
                iconRes = R.drawable.ic_delete
            )
        )

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.task_settings)
            .setAdapter(TaskSettingsActionAdapter(requireContext(), actions)) { _, which ->
                when (which) {
                    0 -> toggleCompletedTasks()
                    1 -> confirmDeleteAllTasks()
                }
            }
            .show()
    }

    private fun toggleCompletedTasks() {
        showCompleted = !showCompleted
        renderTasks()
    }

    private fun cycleTaskPriority(task: Task) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                taskIdToScrollAfterRender = task.id
                taskRepository.toggleTaskPriority(task)
            } catch (exception: Exception) {
                taskIdToScrollAfterRender = null
                showError()
            }
        }
    }

    private fun confirmDeleteAllTasks() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_all_tasks_title)
            .setMessage(R.string.delete_all_tasks_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                deleteAllTasks()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteAllTasks() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                taskRepository.deleteAllTasks()
            } catch (exception: Exception) {
                showError()
            }
        }
    }

    private fun renderTasks() {
        val visibleTasks = if (showCompleted) {
            allTasks
        } else {
            allTasks.filterNot { it.completed }
        }

        taskAdapter.submitList(visibleTasks) {
            if (shouldScrollToTopAfterRender && visibleTasks.isNotEmpty()) {
                scrollTaskListToPosition(0)
                shouldScrollToTopAfterRender = false
            }
            taskIdToScrollAfterRender?.let { taskId ->
                val changedTaskPosition = visibleTasks.indexOfFirst { task -> task.id == taskId }
                if (changedTaskPosition != -1) {
                    scrollTaskListToPosition(changedTaskPosition)
                }
                taskIdToScrollAfterRender = null
            }
        }
        emptyTasksText.isVisible = visibleTasks.isEmpty()
    }

    private fun scrollTaskListToPosition(position: Int) {
        (taskList.layoutManager as? LinearLayoutManager)
            ?.scrollToPositionWithOffset(position, 0)
            ?: taskList.scrollToPosition(position)
    }

    private fun updateCharacterCounter() {
        val length = taskInput.text?.length ?: 0
        characterCounterText.text = getString(
            R.string.task_character_counter,
            length,
            TaskRepository.MAX_TASK_LENGTH
        )
    }

    private fun updateAddButtonState() {
        addTaskButton.isEnabled = !isAddingTask && !taskInput.text?.toString().orEmpty().isBlank()
    }

    private fun showError() {
        statusText.text = getString(R.string.tasks_error)
        statusText.isVisible = true
    }

    private fun currentUserId(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
            ?: AuthRepository(requireContext()).getCurrentUser()?.uid
    }
}

private data class TaskSettingsAction(
    val label: String,
    val iconRes: Int
)

private class TaskSettingsActionAdapter(
    context: android.content.Context,
    actions: List<TaskSettingsAction>
) : ArrayAdapter<TaskSettingsAction>(context, R.layout.item_task_settings_action, actions) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_task_settings_action, parent, false)
        val action = getItem(position) ?: return view

        view.findViewById<ImageView>(R.id.task_settings_action_icon)
            .setImageResource(action.iconRes)
        view.findViewById<TextView>(R.id.task_settings_action_text).text = action.label
        return view
    }
}

private class TaskAdapter(
    private val onCompletedChanged: (Task, Boolean) -> Unit,
    private val onPriorityClicked: (Task) -> Unit
) : ListAdapter<Task, TaskAdapter.TaskViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view, onCompletedChanged, onPriorityClicked)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TaskViewHolder(
        private val cell: View,
        private val onCompletedChanged: (Task, Boolean) -> Unit,
        private val onPriorityClicked: (Task) -> Unit
    ) : RecyclerView.ViewHolder(cell) {
        private val checkbox: CheckBox = cell.findViewById(R.id.task_checkbox)
        private val priorityButton: ImageButton = cell.findViewById(R.id.task_priority_button)
        private var boundTask: Task? = null

        init {
            cell.setOnClickListener {
                val task = boundTask ?: return@setOnClickListener
                onCompletedChanged(task, !task.completed)
            }
            checkbox.setOnClickListener {
                val task = boundTask ?: return@setOnClickListener
                onCompletedChanged(task, !task.completed)
            }
            priorityButton.setOnClickListener {
                val task = boundTask ?: return@setOnClickListener
                onPriorityClicked(task)
            }
        }

        fun bind(task: Task) {
            checkbox.text = task.text
            checkbox.isChecked = task.completed
            checkbox.contentDescription = checkbox.context.getString(
                if (task.completed) R.string.task_completed else R.string.task_not_completed
            )
            priorityButton.setImageResource(priorityIconRes(task.priority))
            priorityButton.contentDescription = priorityButton.context.getString(
                priorityLabelRes(task.priority)
            )
            checkbox.paintFlags = if (task.completed) {
                checkbox.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                checkbox.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            boundTask = task
        }

        private fun priorityIconRes(priority: TaskPriority?): Int {
            return when (priority) {
                TaskPriority.HIGH -> R.drawable.ic_priority_high
                null -> R.drawable.ic_priority_unset
            }
        }

        private fun priorityLabelRes(priority: TaskPriority?): Int {
            return when (priority) {
                TaskPriority.HIGH -> R.string.priority_high
                null -> R.string.set_task_priority
            }
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<Task>() {
        override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean {
            return oldItem == newItem
        }
    }
}
