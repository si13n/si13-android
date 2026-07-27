package com.si13.app

import android.annotation.SuppressLint
import android.graphics.Paint
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.CancellationException
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
            },
            onDeleteClicked = { task ->
                deleteTask(task)
            }
        )

        taskList = view.findViewById(R.id.task_list)
        taskList.apply {
            adapter = taskAdapter
            layoutManager = LinearLayoutManager(requireContext())
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                    if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                        taskAdapter.closeRevealedAction()
                    }
                }
            })
            addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(recyclerView: RecyclerView, event: MotionEvent): Boolean {
                    if (
                        event.actionMasked == MotionEvent.ACTION_DOWN &&
                        recyclerView.findChildViewUnder(event.x, event.y) == null
                    ) {
                        taskAdapter.closeRevealedAction()
                    }
                    return false
                }
            })
        }
        ItemTouchHelper(TaskSwipeCallback(taskAdapter)).attachToRecyclerView(taskList)

        view.findViewById<View>(R.id.home_header).setOnClickListener {
            taskAdapter.closeRevealedAction()
        }
        view.findViewById<View>(R.id.task_input_panel).setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                taskAdapter.closeRevealedAction()
            }
            false
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

    override fun onStop() {
        if (::taskAdapter.isInitialized) {
            taskAdapter.closeRevealedAction()
        }
        super.onStop()
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
            toggleCompletedTasks()
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

    @SuppressLint("RepeatOnLifecycleWrongUsage")
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
                        renderTasks()
                    }
                } catch (exception: CancellationException) {
                    throw exception
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

    private fun toggleCompletedTasks() {
        showCompleted = !showCompleted
        taskSettingsButton.setImageResource(
            if (showCompleted) R.drawable.ic_visibility_off else R.drawable.ic_visibility
        )
        taskSettingsButton.contentDescription = getString(
            if (showCompleted) R.string.hide_completed_tasks else R.string.show_completed_tasks
        )
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

    private fun deleteTask(task: Task) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                taskRepository.deleteTask(task.id)
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
        val activeTaskCount = allTasks.count { task -> !task.completed }
        statusText.text = resources.getQuantityString(
            R.plurals.tasks_left,
            activeTaskCount,
            activeTaskCount
        )
        statusText.isVisible = true
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

private class TaskAdapter(
    private val onCompletedChanged: (Task, Boolean) -> Unit,
    private val onPriorityClicked: (Task) -> Unit,
    private val onDeleteClicked: (Task) -> Unit
) : ListAdapter<Task, TaskAdapter.TaskViewHolder>(DiffCallback) {
    var revealedTaskId: String? = null
        private set

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    fun getTask(position: Int): Task? = getItemOrNull(position)

    fun closeRevealedAction(): Boolean {
        val previousId = revealedTaskId ?: return false
        revealedTaskId = null
        currentList.indexOfFirst { it.id == previousId }
            .takeIf { it >= 0 }
            ?.let(::notifyItemChanged)
        return true
    }

    fun setRevealedTask(taskId: String?) {
        if (revealedTaskId == taskId) return
        val previousId = revealedTaskId
        revealedTaskId = taskId
        listOfNotNull(previousId, taskId).forEach { id ->
            currentList.indexOfFirst { it.id == id }
                .takeIf { it >= 0 }
                ?.let(::notifyItemChanged)
        }
    }

    private fun getItemOrNull(position: Int): Task? =
        if (position in 0 until itemCount) getItem(position) else null

    inner class TaskViewHolder(
        private val cell: View,
    ) : RecyclerView.ViewHolder(cell) {
        private val checkbox: CheckBox = cell.findViewById(R.id.task_checkbox)
        private val priorityButton: ImageButton = cell.findViewById(R.id.task_priority_button)
        private val foreground: View = cell.findViewById(R.id.task_foreground_container)
        private val deleteAction: View = cell.findViewById(R.id.task_delete_action)
        private var boundTask: Task? = null

        init {
            cell.setOnClickListener {
                val task = boundTask ?: return@setOnClickListener
                if (closeRevealedAction()) return@setOnClickListener
                onCompletedChanged(task, !task.completed)
            }
            checkbox.setOnClickListener {
                val task = boundTask ?: return@setOnClickListener
                if (closeRevealedAction()) return@setOnClickListener
                onCompletedChanged(task, !task.completed)
            }
            priorityButton.setOnClickListener {
                val task = boundTask ?: return@setOnClickListener
                if (closeRevealedAction()) return@setOnClickListener
                onPriorityClicked(task)
            }
            deleteAction.setOnClickListener {
                boundTask?.let(onDeleteClicked)
                setRevealedTask(null)
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
            cell.alpha = if (task.completed) 0.55f else 1f
            boundTask = task
            foreground.translationX = if (task.id == revealedTaskId) {
                -deleteActionWidth(cell)
            } else {
                0f
            }
            ViewCompat.replaceAccessibilityAction(
                cell,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_DISMISS,
                cell.context.getString(R.string.delete_task_accessibility, task.text)
            ) { _, _ ->
                onDeleteClicked(task)
                true
            }
        }

        fun foregroundView(): View = foreground

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

private class TaskSwipeCallback(
    private val adapter: TaskAdapter
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
    private var swipeBaseOffset = 0f

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ) = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        adapter.closeRevealedAction()
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 2f

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && viewHolder is TaskAdapter.TaskViewHolder) {
            val task = adapter.getTask(viewHolder.bindingAdapterPosition)
            swipeBaseOffset = if (task?.id == adapter.revealedTaskId) {
                -deleteActionWidth(viewHolder.itemView)
            } else {
                adapter.closeRevealedAction()
                0f
            }
        }
        super.onSelectedChanged(viewHolder, actionState)
    }

    override fun onChildDraw(
        canvas: android.graphics.Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (viewHolder is TaskAdapter.TaskViewHolder && actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
            val foreground = viewHolder.foregroundView()
            foreground.translationX = (swipeBaseOffset + dX)
                .coerceIn(-deleteActionWidth(viewHolder.itemView), 0f)
            return
        }
        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        if (viewHolder is TaskAdapter.TaskViewHolder) {
            val foreground = viewHolder.foregroundView()
            val width = deleteActionWidth(viewHolder.itemView)
            val shouldReveal = foreground.translationX <= -width * REVEAL_THRESHOLD
            val task = adapter.getTask(viewHolder.bindingAdapterPosition)
            adapter.setRevealedTask(if (shouldReveal) task?.id else null)
            foreground.animate()
                .translationX(if (shouldReveal) -width else 0f)
                .setDuration(200L)
                .start()
        }
        swipeBaseOffset = 0f
    }

    companion object {
        private const val REVEAL_THRESHOLD = 0.38f
    }
}

private fun deleteActionWidth(view: View): Float =
    view.resources.getDimension(R.dimen.task_delete_action_width)
