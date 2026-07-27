package com.si13.app

import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.Rect
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
    private lateinit var swipeDismissLayout: SwipeDismissLayout

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
        swipeDismissLayout = view as SwipeDismissLayout

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

                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dx != 0 || dy != 0) {
                        taskAdapter.closeRevealedAction()
                    }
                }
            })
            addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
                private var pendingDeleteAction: View? = null

                override fun onInterceptTouchEvent(recyclerView: RecyclerView, event: MotionEvent): Boolean {
                    if (event.actionMasked != MotionEvent.ACTION_DOWN) {
                        return pendingDeleteAction != null
                    }
                    pendingDeleteAction = taskAdapter.findRevealedDeleteAction(
                        recyclerView,
                        event.rawX.toInt(),
                        event.rawY.toInt()
                    )
                    return pendingDeleteAction != null
                }

                override fun onTouchEvent(recyclerView: RecyclerView, event: MotionEvent) {
                    if (event.actionMasked == MotionEvent.ACTION_UP) {
                        pendingDeleteAction?.performClick()
                        pendingDeleteAction = null
                    } else if (event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        pendingDeleteAction = null
                    }
                }
            })
        }
        ItemTouchHelper(TaskSwipeCallback(taskAdapter)).attachToRecyclerView(taskList)

        swipeDismissLayout.onTouchDown = { event ->
            val inside = isTouchInsideRevealedTask(event.rawX.toInt(), event.rawY.toInt())
            if (!inside) {
                taskAdapter.closeRevealedAction()
            }
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

    override fun onDestroyView() {
        taskObservationJob?.cancel()
        if (::swipeDismissLayout.isInitialized) {
            swipeDismissLayout.onTouchDown = null
        }
        if (::taskList.isInitialized) {
            taskList.adapter = null
        }
        super.onDestroyView()
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
            taskAdapter.closeRevealedAction()
            addTask()
        }

        taskSettingsButton.setOnClickListener {
            taskAdapter.closeRevealedAction()
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
                taskAdapter.allowDeleteRetry(task.id)
                showError()
            }
        }
    }

    private fun isTouchInsideRevealedTask(rawX: Int, rawY: Int): Boolean {
        val taskId = taskAdapter.revealedTaskId ?: return false
        val position = taskAdapter.positionOfTask(taskId)
        if (position == RecyclerView.NO_POSITION) return false
        val itemView = taskList.findViewHolderForAdapterPosition(position)?.itemView ?: return false
        val bounds = Rect()
        return itemView.getGlobalVisibleRect(bounds) && bounds.contains(rawX, rawY)
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
    private val swipeController = TaskSwipeController { previousTaskId, openTaskId ->
        previousTaskId?.let(::notifyTaskChanged)
        openTaskId?.let(::notifyTaskChanged)
    }
    val revealedTaskId: String?
        get() = swipeController.openTaskId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(RevealStatePayload)) {
            holder.updateRevealState(getItem(position).id == revealedTaskId, animate = true)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onCurrentListChanged(previousList: MutableList<Task>, currentList: MutableList<Task>) {
        super.onCurrentListChanged(previousList, currentList)
        swipeController.retainTasks(currentList.mapTo(mutableSetOf()) { it.id })
    }

    fun getTask(position: Int): Task? = getItemOrNull(position)

    fun positionOfTask(taskId: String): Int = currentList.indexOfFirst { it.id == taskId }

    fun findRevealedDeleteAction(recyclerView: RecyclerView, rawX: Int, rawY: Int): View? {
        val taskId = revealedTaskId ?: return null
        val position = positionOfTask(taskId)
        if (position == RecyclerView.NO_POSITION) return null
        val holder = recyclerView.findViewHolderForAdapterPosition(position) as? TaskViewHolder
            ?: return null
        return holder.deleteActionAt(rawX, rawY)
    }

    fun closeRevealedAction(): Boolean = swipeController.close()

    fun setRevealedTask(taskId: String?) {
        swipeController.open(taskId)
    }

    fun requestDelete(task: Task): Boolean = swipeController.requestDelete(task.id) {
        onDeleteClicked(task)
    }

    fun allowDeleteRetry(taskId: String) {
        swipeController.allowDeleteRetry(taskId)
    }

    private fun notifyTaskChanged(taskId: String) {
        positionOfTask(taskId)
            .takeIf { it != RecyclerView.NO_POSITION }
            ?.let { notifyItemChanged(it, RevealStatePayload) }
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
                closeRevealedAction()
                onCompletedChanged(task, !task.completed)
            }
            checkbox.setOnClickListener {
                val task = boundTask ?: return@setOnClickListener
                closeRevealedAction()
                onCompletedChanged(task, !task.completed)
            }
            priorityButton.setOnClickListener {
                val task = boundTask ?: return@setOnClickListener
                closeRevealedAction()
                onPriorityClicked(task)
            }
            deleteAction.setOnClickListener {
                boundTask?.let(::requestDelete)
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
            updateRevealState(task.id == revealedTaskId, animate = false)
            ViewCompat.replaceAccessibilityAction(
                cell,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_DISMISS,
                cell.context.getString(R.string.delete_task_accessibility, task.text)
            ) { _, _ ->
                task.takeIf(::requestDelete)?.let(onDeleteClicked)
                true
            }
        }

        fun foregroundView(): View = foreground

        fun updateRevealState(revealed: Boolean, animate: Boolean) {
            foreground.animate().cancel()
            if (revealed) {
                showDeleteAction()
            }
            val targetTranslation = if (revealed) -deleteActionWidth(cell) else 0f
            priorityButton.isEnabled = !revealed
            if (animate) {
                foreground.animate()
                    .translationX(targetTranslation)
                    .setDuration(REVEAL_ANIMATION_DURATION_MS)
                    .withEndAction {
                        if (!revealed) {
                            deleteAction.isVisible = false
                            deleteAction.translationZ = 0f
                        }
                    }
                    .start()
            } else {
                foreground.translationX = targetTranslation
                deleteAction.isVisible = revealed
                deleteAction.translationZ = if (revealed) 2f else 0f
            }
        }

        fun showDeleteAction() {
            deleteAction.isVisible = true
            deleteAction.translationZ = 2f
        }

        fun deleteActionAt(rawX: Int, rawY: Int): View? {
            if (boundTask?.id != revealedTaskId || !deleteAction.isVisible) return null
            val bounds = Rect()
            return deleteAction.takeIf {
                it.getGlobalVisibleRect(bounds) && bounds.contains(rawX, rawY)
            }
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

    private object RevealStatePayload

    companion object {
        private const val REVEAL_ANIMATION_DURATION_MS = 200L
    }
}

private class TaskSwipeCallback(
    private val adapter: TaskAdapter
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
    private var swipeBaseOffset = 0f
    private var activeViewHolder: TaskAdapter.TaskViewHolder? = null

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ) = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val task = adapter.getTask(viewHolder.bindingAdapterPosition)
        adapter.setRevealedTask(task?.id)
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 2f

    override fun getSwipeEscapeVelocity(defaultValue: Float): Float = Float.MAX_VALUE

    override fun getSwipeVelocityThreshold(defaultValue: Float): Float = Float.MAX_VALUE

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && viewHolder is TaskAdapter.TaskViewHolder) {
            activeViewHolder = viewHolder
            viewHolder.showDeleteAction()
            val task = adapter.getTask(viewHolder.bindingAdapterPosition)
            swipeBaseOffset = if (task?.id == adapter.revealedTaskId) {
                -deleteActionWidth(viewHolder.itemView)
            } else {
                adapter.closeRevealedAction()
                0f
            }
        } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
            activeViewHolder?.let(::settleRevealState)
            activeViewHolder = null
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
            if (!isCurrentlyActive) {
                val task = adapter.getTask(viewHolder.bindingAdapterPosition)
                foreground.translationX = if (task?.id == adapter.revealedTaskId) {
                    -deleteActionWidth(viewHolder.itemView)
                } else {
                    0f
                }
                return
            }
            foreground.translationX = (swipeBaseOffset + dX)
                .coerceIn(-deleteActionWidth(viewHolder.itemView), 0f)
            return
        }
        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        if (viewHolder is TaskAdapter.TaskViewHolder) {
            val task = adapter.getTask(viewHolder.bindingAdapterPosition)
            viewHolder.updateRevealState(task?.id == adapter.revealedTaskId, animate = true)
        }
        swipeBaseOffset = 0f
    }

    private fun settleRevealState(viewHolder: TaskAdapter.TaskViewHolder) {
        val width = deleteActionWidth(viewHolder.itemView)
        val shouldReveal = viewHolder.foregroundView().translationX <= -width * REVEAL_THRESHOLD
        val task = adapter.getTask(viewHolder.bindingAdapterPosition)
        adapter.setRevealedTask(if (shouldReveal) task?.id else null)
    }

    companion object {
        private const val REVEAL_THRESHOLD = 0.38f
    }
}

private fun deleteActionWidth(view: View): Float =
    view.findViewById<View>(R.id.task_delete_action)
        ?.width
        ?.takeIf { it > 0 }
        ?.toFloat()
        ?: view.resources.getDimension(R.dimen.task_delete_action_width)
