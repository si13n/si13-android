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
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
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
    private lateinit var showCompletedButton: Button
    private lateinit var statusText: TextView
    private lateinit var emptyTasksText: TextView
    private lateinit var characterCounterText: TextView

    private var allTasks: List<Task> = emptyList()
    private var showCompleted = false
    private var observedUserId: String? = null
    private var hasObservedSource = false
    private var taskObservationJob: Job? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        taskRepository = TaskRepository.create(requireContext())
        taskInput = view.findViewById(R.id.task_input)
        addTaskButton = view.findViewById(R.id.add_task_button)
        showCompletedButton = view.findViewById(R.id.show_completed_button)
        statusText = view.findViewById(R.id.task_status_text)
        emptyTasksText = view.findViewById(R.id.empty_tasks_text)
        characterCounterText = view.findViewById(R.id.task_character_counter)

        taskAdapter = TaskAdapter { task, completed ->
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    taskRepository.setTaskCompleted(task, completed)
                } catch (exception: Exception) {
                    showError()
                }
            }
        }

        view.findViewById<RecyclerView>(R.id.task_list).apply {
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

        showCompletedButton.setOnClickListener {
            showCompleted = !showCompleted
            updateShowCompletedButton()
            renderTasks()
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
        val taskText = taskInput.text?.toString().orEmpty()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                taskRepository.addTask(taskText)
                taskInput.text?.clear()
            } catch (exception: IllegalArgumentException) {
                statusText.text = exception.message ?: getString(R.string.task_too_long)
                statusText.isVisible = true
            } catch (exception: Exception) {
                showError()
            }
        }
    }

    private fun submitTaskFromKeyboard(): Boolean {
        // A task input is one task per line: Enter submits instead of inserting a newline.
        addTask()
        return true
    }

    private fun renderTasks() {
        val visibleTasks = if (showCompleted) {
            allTasks
        } else {
            allTasks.filterNot { it.completed }
        }

        taskAdapter.submitList(visibleTasks)
        emptyTasksText.isVisible = visibleTasks.isEmpty()
    }

    private fun updateShowCompletedButton() {
        showCompletedButton.text = getString(
            if (showCompleted) R.string.hide_completed else R.string.show_completed
        )
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
        addTaskButton.isEnabled = !taskInput.text?.toString().orEmpty().isBlank()
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

private class TaskAdapter(
    private val onCompletedChanged: (Task, Boolean) -> Unit
) : ListAdapter<Task, TaskAdapter.TaskViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view as CheckBox, onCompletedChanged)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TaskViewHolder(
        private val checkbox: CheckBox,
        private val onCompletedChanged: (Task, Boolean) -> Unit
    ) : RecyclerView.ViewHolder(checkbox) {
        private var boundTask: Task? = null

        init {
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                val task = boundTask ?: return@setOnCheckedChangeListener
                if (task.completed != isChecked) {
                    onCompletedChanged(task, isChecked)
                }
            }
        }

        fun bind(task: Task) {
            boundTask = null
            checkbox.text = task.text
            checkbox.isChecked = task.completed
            checkbox.contentDescription = checkbox.context.getString(
                if (task.completed) R.string.task_completed else R.string.task_not_completed
            )
            checkbox.paintFlags = if (task.completed) {
                checkbox.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                checkbox.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            boundTask = task
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
