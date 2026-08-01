package com.si13.app

import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.Rect
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ContextThemeWrapper
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.widget.doAfterTextChanged
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
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HomeFragment : Fragment(R.layout.fragment_home) {
    private lateinit var taskRepository: TaskRepository
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var taskSearchInput: TextInputEditText
    private lateinit var taskSearchPanel: View
    private lateinit var taskSearchButton: ImageButton
    private lateinit var taskSettingsButton: ImageButton
    private lateinit var taskSortButton: ImageButton
    private lateinit var homeDateText: TextView
    private lateinit var statusText: TextView
    private lateinit var emptyTasksText: TextView
    private lateinit var emptyTasksHint: TextView
    private lateinit var emptyTasksContainer: View
    private lateinit var taskProgressText: TextView
    private lateinit var taskProgressEncouragement: TextView
    private lateinit var taskProgressIndicator: LinearProgressIndicator
    private lateinit var taskList: RecyclerView
    private lateinit var swipeDismissLayout: SwipeDismissLayout
    private lateinit var taskListFilterGroup: ChipGroup
    private lateinit var taskStatusFilterGroup: ChipGroup

    private var allTasks: List<Task> = emptyList()
    private var selectedList: String? = null
    private var statusFilter = HomeTaskFilter.ALL
    private var searchQuery = ""
    private var sortMode = TaskSortMode.PRIORITY_FIRST
    private val taskSourceTracker = TaskSourceTracker()
    private var isAddingTask = false
    private var shouldScrollToTopAfterRender = false
    private var taskIdToScrollAfterRender: String? = null
    private var taskObservationJob: Job? = null
    private val taskDetailsUpdateMutex = Mutex()
    private val authStateListener = FirebaseAuth.AuthStateListener {
        refreshTaskSourceIfNeeded()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        taskRepository = TaskRepository.create(requireContext())
        taskSearchInput = view.findViewById(R.id.task_search_input)
        taskSearchPanel = view.findViewById(R.id.task_search_panel)
        taskSearchButton = view.findViewById(R.id.task_search_button)
        taskSettingsButton = view.findViewById(R.id.task_settings_button)
        taskSortButton = view.findViewById(R.id.task_sort_button)
        homeDateText = view.findViewById(R.id.home_date_text)
        statusText = view.findViewById(R.id.task_status_text)
        emptyTasksText = view.findViewById(R.id.empty_tasks_text)
        emptyTasksHint = view.findViewById(R.id.empty_tasks_hint)
        emptyTasksContainer = view.findViewById(R.id.empty_tasks_container)
        taskProgressText = view.findViewById(R.id.task_progress_text)
        taskProgressEncouragement = view.findViewById(R.id.task_progress_encouragement)
        taskProgressIndicator = view.findViewById(R.id.task_progress_indicator)
        taskListFilterGroup = view.findViewById(R.id.task_list_filter_group)
        taskStatusFilterGroup = view.findViewById(R.id.task_status_filter_group)
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
            },
            onTaskClicked = { task ->
                TaskDetailsBottomSheet.show(childFragmentManager, task)
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

        configureSearch(view)
        configureFilters(view)
        bindActions()
        bindLoginResult()
        updateCurrentDate()
        observeTasks()
    }

    override fun onResume() {
        super.onResume()
        updateCurrentDate()
        refreshTaskSourceIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
    }

    override fun onStop() {
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
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

    private fun configureSearch(view: View) {
        taskSearchInput.doAfterTextChanged {
            searchQuery = it?.toString().orEmpty().trim()
            renderTasks()
        }
        view.findViewById<View>(R.id.task_search_close).setOnClickListener {
            taskSearchInput.text?.clear()
            taskSearchPanel.isVisible = false
            hideKeyboard()
        }
    }

    private fun configureFilters(view: View) {
        view.findViewById<View>(R.id.list_view_button).isSelected = true
        listOf(taskListFilterGroup, taskStatusFilterGroup).forEach { group ->
            repeat(group.childCount) { index ->
                (group.getChildAt(index) as? Chip)?.isCheckedIconVisible = false
            }
        }
        taskListFilterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedList = when (checkedIds.firstOrNull()) {
                R.id.list_filter_personal -> "Personal"
                R.id.list_filter_work -> "Work"
                R.id.list_filter_shared -> "Shared"
                R.id.list_filter_shopping -> "Shopping"
                else -> null
            }
            renderTasks()
        }
        taskStatusFilterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            statusFilter = when (checkedIds.firstOrNull()) {
                R.id.status_filter_today -> HomeTaskFilter.TODAY
                R.id.status_filter_high -> HomeTaskFilter.HIGH_PRIORITY
                R.id.status_filter_completed -> HomeTaskFilter.COMPLETED
                else -> HomeTaskFilter.ALL
            }
            renderTasks()
        }
        view.findViewById<View>(R.id.calendar_view_button).setOnClickListener {
            Snackbar.make(swipeDismissLayout, R.string.calendar_coming_soon, Snackbar.LENGTH_SHORT)
                .setAnchorView(requireActivity().findViewById(R.id.bottom_navigation))
                .show()
        }
        view.findViewById<View>(R.id.manage_lists_chip).setOnClickListener {
            showListSummary()
        }
    }

    private fun bindActions() {
        taskSearchButton.setOnClickListener {
            taskAdapter.closeRevealedAction()
            taskSearchPanel.isVisible = true
            taskSearchInput.requestFocus()
            taskSearchInput.post {
                (requireContext().getSystemService(InputMethodManager::class.java))
                    ?.showSoftInput(taskSearchInput, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        taskSettingsButton.setOnClickListener {
            taskAdapter.closeRevealedAction()
            taskStatusFilterGroup.check(R.id.status_filter_completed)
        }

        taskSortButton.setOnClickListener {
            taskAdapter.closeRevealedAction()
            showTaskMenu()
        }
    }

    private fun showListSummary() {
        val labels = BUILT_IN_TASK_LISTS.map { listName ->
            val count = allTasks.count { it.listName == listName }
            "$listName  ·  $count"
        }.toTypedArray()
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.manage_lists)
            .setItems(labels, null)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun hideKeyboard() {
        (requireContext().getSystemService(InputMethodManager::class.java))
            ?.hideSoftInputFromWindow(taskSearchInput.windowToken, 0)
    }

    private fun showTaskMenu() {
        PopupMenu(
            ContextThemeWrapper(requireContext(), R.style.ThemeOverlay_Si13_HomePopup),
            taskSortButton
        ).apply {
            inflate(R.menu.home_task_menu)
            menu.findItem(sortMode.menuItemId).isChecked = true
            setOnMenuItemClickListener { item ->
                TaskSortMode.fromMenuItemId(item.itemId)?.let { selectedMode ->
                    sortMode = selectedMode
                    item.isChecked = true
                    renderTasks()
                    true
                } ?: false
            }
            show()
        }
    }

    private fun bindLoginResult() {
        requireActivity().supportFragmentManager.setFragmentResultListener(
            LoginBottomSheet.LOGIN_RESULT_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            refreshTaskSourceIfNeeded()
        }

        requireActivity().supportFragmentManager.setFragmentResultListener(
            TaskImportDialogFragment.IMPORT_RESULT_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            refreshTaskSourceIfNeeded()
        }
    }

    @SuppressLint("RepeatOnLifecycleWrongUsage")
    private fun observeTasks() {
        taskObservationJob?.cancel()
        taskSourceTracker.markObserved(currentUserId())
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

    private fun refreshTaskSourceIfNeeded() {
        if (!isAdded || !::taskRepository.isInitialized) return
        val currentUserId = currentUserId()
        if (!taskSourceTracker.hasSourceChanged(currentUserId)) return

        taskRepository = TaskRepository.create(requireContext())
        observeTasks()
    }

    fun showAddTaskSheet() {
        if (!isAdded || childFragmentManager.findFragmentByTag(AddTaskBottomSheet.TAG) != null) return
        taskAdapter.closeRevealedAction()
        AddTaskBottomSheet().show(childFragmentManager, AddTaskBottomSheet.TAG)
    }

    fun createTask(
        text: String,
        priority: TaskPriority,
        dueDate: String?,
        listName: String
    ) {
        if (isAddingTask) return
        isAddingTask = true

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                taskRepository.addTask(text, priority, dueDate, listName)
                shouldScrollToTopAfterRender = true
            } catch (exception: IllegalArgumentException) {
                statusText.text = exception.message ?: getString(R.string.task_too_long)
                statusText.isVisible = true
            } catch (exception: Exception) {
                statusText.setText(R.string.add_task_failed)
                statusText.isVisible = true
            } finally {
                isAddingTask = false
            }
        }
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
                Snackbar.make(swipeDismissLayout, R.string.task_deleted, Snackbar.LENGTH_LONG)
                    .setAnchorView(requireActivity().findViewById(R.id.bottom_navigation))
                    .setAction(R.string.undo) { restoreTask(task) }
                    .show()
            } catch (exception: Exception) {
                taskAdapter.allowDeleteRetry(task.id)
                showError()
            }
        }
    }

    fun updateTaskFromDetails(
        task: Task,
        text: String = task.text,
        priority: TaskPriority = task.priority,
        dueDate: String? = task.dueDate,
        completed: Boolean = task.completed
    ) {
        val updatedTask = task.copy(
            text = text.trim().ifEmpty { task.text },
            completed = completed,
            priority = priority,
            dueDate = dueDate
        )
        allTasks = allTasks.map { currentTask ->
            if (currentTask.id == updatedTask.id) updatedTask else currentTask
        }
        renderTasks()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                taskDetailsUpdateMutex.withLock {
                    taskRepository.updateTask(updatedTask)
                }
            } catch (exception: Exception) {
                showError()
            }
        }
    }

    fun refreshTasksAfterDetails() {
        renderTasks()
    }

    fun deleteTaskFromDetails(task: Task) = deleteTask(task)

    private fun restoreTask(task: Task) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                taskRepository.restoreTask(task)
            } catch (exception: Exception) {
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
        val listFiltered = allTasks.filter { task ->
            (selectedList == null || task.listName == selectedList) &&
                (searchQuery.isBlank() || task.text.contains(searchQuery, ignoreCase = true))
        }
        val sections = TaskSectioner.sections(
            TaskSorter.sort(listFiltered, sortMode),
            statusFilter,
            LocalDate.now()
        )
        val visibleTaskCount = sections.sumOf { it.tasks.size }

        taskAdapter.submitSections(sections) {
            if (shouldScrollToTopAfterRender && visibleTaskCount > 0) {
                scrollTaskListToPosition(0)
                shouldScrollToTopAfterRender = false
            }
            taskIdToScrollAfterRender?.let { taskId ->
                val changedTaskPosition = taskAdapter.positionOfTask(taskId)
                if (changedTaskPosition != -1) {
                    scrollTaskListToPosition(changedTaskPosition)
                }
                taskIdToScrollAfterRender = null
            }
        }
        val completedTaskCount = allTasks.count(Task::completed)
        taskProgressText.text = getString(
            R.string.home_progress_summary,
            completedTaskCount,
            allTasks.size
        )
        val completionRate = if (allTasks.isEmpty()) 0 else completedTaskCount * 100 / allTasks.size
        taskProgressIndicator.setProgressCompat(completionRate, true)
        taskProgressEncouragement.setText(
            when {
                allTasks.isNotEmpty() && completionRate == 100 -> R.string.progress_all_done
                completionRate >= 75 -> R.string.progress_almost_there
                completionRate >= 35 -> R.string.progress_keep_going
                else -> R.string.progress_good_start
            }
        )
        statusText.isVisible = false

        emptyTasksContainer.isVisible = visibleTaskCount == 0
        if (allTasks.isEmpty()) {
            emptyTasksText.setText(R.string.tasks_empty)
            emptyTasksHint.setText(R.string.tasks_empty_hint)
        } else {
            emptyTasksText.setText(R.string.no_active_tasks)
            emptyTasksHint.text = when {
                searchQuery.isNotBlank() -> getString(R.string.search_tasks)
                statusFilter != HomeTaskFilter.COMPLETED -> getString(R.string.show_completed_hint)
                else -> getString(R.string.tasks_empty_hint)
            }
        }
    }

    private fun updateCurrentDate() {
        val locale = resources.configuration.locales[0] ?: Locale.getDefault()
        val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd")
        homeDateText.text = SimpleDateFormat(pattern, locale).format(Date())
    }

    private fun scrollTaskListToPosition(position: Int) {
        (taskList.layoutManager as? LinearLayoutManager)
            ?.scrollToPositionWithOffset(position, 0)
            ?: taskList.scrollToPosition(position)
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

internal enum class HomeTaskFilter {
    ALL,
    TODAY,
    HIGH_PRIORITY,
    COMPLETED
}

internal enum class TaskSectionKind(val titleRes: Int, val accentColorRes: Int) {
    OVERDUE(R.string.overdue_section, R.color.home_overdue),
    TODAY(R.string.today_section, R.color.home_accent),
    UPCOMING(R.string.upcoming_section, R.color.home_upcoming),
    NO_DUE_DATE(R.string.no_due_date_section, R.color.home_upcoming),
    COMPLETED(R.string.completed_section, R.color.home_success)
}

internal data class TaskSection(val kind: TaskSectionKind, val tasks: List<Task>)

internal object TaskSectioner {
    fun sections(tasks: List<Task>, filter: HomeTaskFilter, today: LocalDate): List<TaskSection> {
        val filtered = when (filter) {
            HomeTaskFilter.ALL -> tasks.filterNot { it.completed }
            HomeTaskFilter.TODAY -> tasks.filter {
                !it.completed && it.dueDate?.let(LocalDate::parse) == today
            }
            HomeTaskFilter.HIGH_PRIORITY -> tasks.filter {
                !it.completed && it.priority == TaskPriority.HIGH
            }
            HomeTaskFilter.COMPLETED -> tasks.filter { it.completed }
        }
        if (filter == HomeTaskFilter.COMPLETED) {
            return filtered.takeIf(List<Task>::isNotEmpty)
                ?.let { listOf(TaskSection(TaskSectionKind.COMPLETED, it)) }
                .orEmpty()
        }

        val overdue = mutableListOf<Task>()
        val dueToday = mutableListOf<Task>()
        val upcoming = mutableListOf<Task>()
        val noDueDate = mutableListOf<Task>()
        filtered.forEach { task ->
            val dueDate = task.dueDate?.let(LocalDate::parse)
            when {
                dueDate == null -> noDueDate += task
                dueDate.isBefore(today) -> overdue += task
                dueDate == today -> dueToday += task
                else -> upcoming += task
            }
        }
        return buildList {
            overdue.takeIf(List<Task>::isNotEmpty)?.let {
                add(TaskSection(TaskSectionKind.OVERDUE, it))
            }
            dueToday.takeIf(List<Task>::isNotEmpty)?.let {
                add(TaskSection(TaskSectionKind.TODAY, it))
            }
            upcoming.takeIf(List<Task>::isNotEmpty)?.let {
                add(TaskSection(TaskSectionKind.UPCOMING, it))
            }
            noDueDate.takeIf(List<Task>::isNotEmpty)?.let {
                add(TaskSection(TaskSectionKind.NO_DUE_DATE, it))
            }
        }
    }
}

internal enum class TaskSortMode(val menuItemId: Int) {
    NEWEST_FIRST(R.id.action_sort_newest),
    OLDEST_FIRST(R.id.action_sort_oldest),
    PRIORITY_FIRST(R.id.action_sort_priority),
    ALPHABETICAL(R.id.action_sort_alphabetical);

    companion object {
        fun fromMenuItemId(menuItemId: Int): TaskSortMode? =
            values().firstOrNull { it.menuItemId == menuItemId }
    }
}

internal object TaskSorter {
    fun sort(tasks: List<Task>, mode: TaskSortMode): List<Task> {
        return when (mode) {
            TaskSortMode.NEWEST_FIRST -> tasks.sortedWith(
                compareByDescending<Task> { it.createdAt }.thenBy { it.id }
            )
            TaskSortMode.OLDEST_FIRST -> tasks.sortedWith(
                compareBy<Task> { it.createdAt }.thenBy { it.id }
            )
            TaskSortMode.PRIORITY_FIRST -> tasks.sortedWith(
                compareByDescending<Task> { it.priority?.rank ?: 0 }
                    .thenByDescending { it.createdAt }
                    .thenBy { it.id }
            )
            TaskSortMode.ALPHABETICAL -> {
                val collator = Collator.getInstance()
                tasks.sortedWith { first, second ->
                    collator.compare(first.text, second.text).takeIf { it != 0 }
                        ?: second.createdAt.compareTo(first.createdAt)
                }
            }
        }
    }
}

private enum class TaskGroupPosition {
    SINGLE,
    FIRST,
    MIDDLE,
    LAST
}

private sealed class TaskListItem {
    data class Row(val task: Task, val groupPosition: TaskGroupPosition) : TaskListItem()
    data class SectionHeader(val kind: TaskSectionKind, val count: Int) : TaskListItem()
}

private class TaskAdapter(
    private val onCompletedChanged: (Task, Boolean) -> Unit,
    private val onPriorityClicked: (Task) -> Unit,
    private val onDeleteClicked: (Task) -> Unit,
    private val onTaskClicked: (Task) -> Unit
) : ListAdapter<TaskListItem, RecyclerView.ViewHolder>(DiffCallback) {
    private val swipeController = TaskSwipeController { previousTaskId, openTaskId ->
        previousTaskId?.let(::notifyTaskChanged)
        openTaskId?.let(::notifyTaskChanged)
    }
    val revealedTaskId: String?
        get() = swipeController.openTaskId

    override fun getItemViewType(position: Int): Int {
        return when (getItem(position)) {
            is TaskListItem.Row -> VIEW_TYPE_TASK
            is TaskListItem.SectionHeader -> VIEW_TYPE_SECTION_HEADER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_TASK -> TaskViewHolder(inflater.inflate(R.layout.item_task, parent, false))
            else -> SectionHeaderViewHolder(
                inflater.inflate(R.layout.item_task_section_header, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is TaskViewHolder -> holder.bind(getItem(position) as TaskListItem.Row)
            is SectionHeaderViewHolder -> holder.bind(getItem(position) as TaskListItem.SectionHeader)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: MutableList<Any>
    ) {
        val task = (getItem(position) as? TaskListItem.Row)?.task
        if (holder is TaskViewHolder && task != null && payloads.contains(RevealStatePayload)) {
            holder.updateRevealState(task.id == revealedTaskId, animate = true)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onCurrentListChanged(
        previousList: MutableList<TaskListItem>,
        currentList: MutableList<TaskListItem>
    ) {
        super.onCurrentListChanged(previousList, currentList)
        swipeController.retainTasks(
            currentList.mapNotNullTo(mutableSetOf()) { item ->
                (item as? TaskListItem.Row)?.task?.id
            }
        )
    }

    fun submitSections(sections: List<TaskSection>, commitCallback: () -> Unit) {
        val items = buildList {
            sections.forEach { section ->
                add(TaskListItem.SectionHeader(section.kind, section.tasks.size))
                section.tasks.forEachIndexed { index, task ->
                    add(TaskListItem.Row(task, groupPosition(index, section.tasks.size)))
                }
            }
        }
        submitList(items, commitCallback)
    }

    fun getTask(position: Int): Task? = getItemOrNull(position)?.task

    fun positionOfTask(taskId: String): Int = currentList.indexOfFirst { item ->
        (item as? TaskListItem.Row)?.task?.id == taskId
    }

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

    private fun getItemOrNull(position: Int): TaskListItem.Row? =
        if (position in 0 until itemCount) getItem(position) as? TaskListItem.Row else null

    private class SectionHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val title: TextView = view.findViewById(R.id.task_section_title)
        private val count: TextView = view.findViewById(R.id.task_section_count)
        private val accent: View = view.findViewById(R.id.task_section_accent)

        fun bind(header: TaskListItem.SectionHeader) {
            title.setText(header.kind.titleRes)
            count.text = header.count.toString()
            accent.setBackgroundColor(accent.context.getColor(header.kind.accentColorRes))
        }
    }

    inner class TaskViewHolder(
        private val cell: View,
    ) : RecyclerView.ViewHolder(cell) {
        private val checkbox: CheckBox = cell.findViewById(R.id.task_checkbox)
        private val taskTitle: TextView = cell.findViewById(R.id.task_title)
        private val taskDueDate: TextView = cell.findViewById(R.id.task_due_date)
        private val taskListName: TextView = cell.findViewById(R.id.task_list_name)
        private val priorityButton: View = cell.findViewById(R.id.task_priority_button)
        private val priorityDot: View = cell.findViewById(R.id.task_priority_dot)
        private val foreground: View = cell.findViewById(R.id.task_foreground_container)
        private val divider: View = cell.findViewById(R.id.task_divider)
        private val deleteAction: View = cell.findViewById(R.id.task_delete_action)
        private var boundTask: Task? = null

        init {
            cell.setOnClickListener {
                val task = boundTask ?: return@setOnClickListener
                if (revealedTaskId != null) {
                    closeRevealedAction()
                } else {
                    onTaskClicked(task)
                }
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

        fun bind(item: TaskListItem.Row) {
            val task = item.task
            taskTitle.text = task.text
            taskTitle.isActivated = task.completed
            bindDueDate(task)
            bindList(task)
            checkbox.isChecked = task.completed
            checkbox.contentDescription = checkbox.context.getString(
                if (task.completed) R.string.task_completed else R.string.task_not_completed
            )
            priorityDot.isVisible = task.priority != TaskPriority.NONE
            priorityButton.contentDescription = priorityButton.context.getString(
                priorityLabelRes(task.priority)
            )
            priorityDot.backgroundTintList = android.content.res.ColorStateList.valueOf(
                priorityButton.context.getColor(priorityDotColor(task.priority))
            )
            priorityDot.alpha = if (task.completed) COMPLETED_PRIORITY_ALPHA else 1f
            taskTitle.paintFlags = if (task.completed) {
                taskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                taskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            cell.alpha = 1f
            boundTask = task
            updateGroupShape(item.groupPosition)
            updateRevealState(task.id == revealedTaskId, animate = false)
            ViewCompat.replaceAccessibilityAction(
                cell,
                AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_DISMISS,
                cell.context.getString(R.string.delete_task_accessibility, task.text)
            ) { _, _ ->
                requestDelete(task)
                true
            }
        }

        fun foregroundView(): View = foreground

        fun updateRevealState(revealed: Boolean, animate: Boolean) {
            foreground.translationX = 0f
            deleteAction.animate().cancel()
            showDeleteAction()
            val width = deleteActionWidth(cell)
            val targetTranslation = if (revealed) 0f else width
            priorityButton.isEnabled = !revealed
            if (animate) {
                deleteAction.animate()
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
                deleteAction.translationX = targetTranslation
                deleteAction.isVisible = revealed
                deleteAction.translationZ = if (revealed) overlayTranslationZ() else 0f
            }
        }

        fun updateSwipeReveal(swipeOffset: Float) {
            foreground.translationX = 0f
            deleteAction.animate().cancel()
            showDeleteAction()
            val width = deleteActionWidth(cell)
            deleteAction.translationX = TaskSwipeBounds.overlayTranslation(swipeOffset, width)
        }

        fun showDeleteAction() {
            deleteAction.isVisible = true
            deleteAction.translationZ = overlayTranslationZ()
        }

        private fun overlayTranslationZ(): Float = foreground.elevation + 1f

        fun deleteActionAt(rawX: Int, rawY: Int): View? {
            if (boundTask?.id != revealedTaskId || !deleteAction.isVisible) return null
            val bounds = Rect()
            return deleteAction.takeIf {
                it.getGlobalVisibleRect(bounds) && bounds.contains(rawX, rawY)
            }
        }

        private fun priorityLabelRes(priority: TaskPriority): Int {
            return when (priority) {
                TaskPriority.NONE -> R.string.no_priority
                TaskPriority.HIGH -> R.string.high_priority
            }
        }

        private fun priorityDotColor(priority: TaskPriority): Int = when (priority) {
            TaskPriority.NONE -> R.color.home_text_secondary
            TaskPriority.HIGH -> R.color.home_priority_high
        }

        private fun bindDueDate(task: Task) {
            val dueDate = task.dueDate?.let(LocalDate::parse)
            if (dueDate == null) {
                taskDueDate.visibility = View.GONE
                taskDueDate.text = null
                taskDueDate.compoundDrawablesRelative[0]?.setTintList(null)
                return
            }
            val today = LocalDate.now()
            val overdue = TaskDatePresentation.isOverdue(dueDate, today)
            val color = if (overdue && !task.completed) R.color.home_delete else R.color.home_text_secondary
            taskDueDate.visibility = View.VISIBLE
            taskDueDate.text = when (dueDate) {
                today -> taskDueDate.context.getString(R.string.due_today)
                today.plusDays(1) -> taskDueDate.context.getString(R.string.due_tomorrow)
                else -> taskDueDate.context.getString(
                    R.string.due_date_format,
                    TaskDatePresentation.formatDate(
                        dueDate,
                        today,
                        taskDueDate.resources.configuration.locales[0] ?: Locale.getDefault()
                    )
                )
            }
            taskDueDate.setTextColor(taskDueDate.context.getColor(color))
            taskDueDate.compoundDrawablesRelative[0]?.setTint(taskDueDate.context.getColor(color))
        }

        private fun bindList(task: Task) {
            taskListName.text = task.listName
            taskListName.setTextColor(
                taskListName.context.getColor(
                    when (task.listName) {
                        "Work" -> R.color.home_work
                        "Shared" -> R.color.home_shared
                        "Shopping" -> R.color.home_shopping
                        else -> R.color.home_personal
                    }
                )
            )
        }

        private fun updateGroupShape(position: TaskGroupPosition) {
            foreground.setBackgroundResource(R.drawable.bg_task_group_single)
            foreground.invalidateOutline()
            divider.isVisible = false
            deleteAction.setBackgroundResource(R.drawable.bg_task_delete_action)
        }
    }

    private object DiffCallback : DiffUtil.ItemCallback<TaskListItem>() {
        override fun areItemsTheSame(oldItem: TaskListItem, newItem: TaskListItem): Boolean {
            return when {
                oldItem is TaskListItem.Row && newItem is TaskListItem.Row -> {
                    oldItem.task.id == newItem.task.id
                }
                oldItem is TaskListItem.SectionHeader && newItem is TaskListItem.SectionHeader -> {
                    oldItem.kind == newItem.kind
                }
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: TaskListItem, newItem: TaskListItem): Boolean {
            return oldItem == newItem
        }
    }

    private object RevealStatePayload

    companion object {
        private const val COMPLETED_PRIORITY_ALPHA = 0.55f
        private const val REVEAL_ANIMATION_DURATION_MS = 200L
        private const val VIEW_TYPE_TASK = 1
        private const val VIEW_TYPE_SECTION_HEADER = 2
    }
}

private fun groupPosition(index: Int, size: Int): TaskGroupPosition {
    return when {
        size == 1 -> TaskGroupPosition.SINGLE
        index == 0 -> TaskGroupPosition.FIRST
        index == size - 1 -> TaskGroupPosition.LAST
        else -> TaskGroupPosition.MIDDLE
    }
}

private class TaskSwipeCallback(
    private val adapter: TaskAdapter
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
    private var swipeBaseOffset = 0f
    private var currentSwipeOffset = 0f
    private var activeViewHolder: TaskAdapter.TaskViewHolder? = null

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ) = false

    override fun getSwipeDirs(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        return if (viewHolder is TaskAdapter.TaskViewHolder) {
            super.getSwipeDirs(recyclerView, viewHolder)
        } else {
            0
        }
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val task = adapter.getTask(viewHolder.bindingAdapterPosition)
        adapter.setRevealedTask(if (direction == ItemTouchHelper.LEFT) task?.id else null)
        viewHolder.itemView.translationX = 0f
    }

    override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 2f

    override fun getSwipeEscapeVelocity(defaultValue: Float): Float = Float.MAX_VALUE

    override fun getSwipeVelocityThreshold(defaultValue: Float): Float = Float.MAX_VALUE

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && viewHolder is TaskAdapter.TaskViewHolder) {
            viewHolder.itemView.translationX = 0f
            activeViewHolder = viewHolder
            viewHolder.showDeleteAction()
            val task = adapter.getTask(viewHolder.bindingAdapterPosition)
            swipeBaseOffset = if (task?.id == adapter.revealedTaskId) {
                -deleteActionWidth(viewHolder.itemView)
            } else {
                adapter.closeRevealedAction()
                0f
            }
            currentSwipeOffset = swipeBaseOffset
            viewHolder.updateSwipeReveal(currentSwipeOffset)
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
            viewHolder.itemView.translationX = 0f
            viewHolder.foregroundView().translationX = 0f
            if (!isCurrentlyActive) {
                val task = adapter.getTask(viewHolder.bindingAdapterPosition)
                currentSwipeOffset = if (task?.id == adapter.revealedTaskId) {
                    -deleteActionWidth(viewHolder.itemView)
                } else {
                    0f
                }
                viewHolder.updateSwipeReveal(currentSwipeOffset)
                return
            }
            currentSwipeOffset = TaskSwipeBounds.swipeOffset(
                baseOffset = swipeBaseOffset,
                gestureOffset = dX,
                actionWidth = deleteActionWidth(viewHolder.itemView)
            )
            viewHolder.updateSwipeReveal(currentSwipeOffset)
            return
        }
        super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.translationX = 0f
        if (viewHolder is TaskAdapter.TaskViewHolder) {
            val task = adapter.getTask(viewHolder.bindingAdapterPosition)
            viewHolder.updateRevealState(task?.id == adapter.revealedTaskId, animate = true)
        }
        swipeBaseOffset = 0f
        currentSwipeOffset = 0f
    }

    private fun settleRevealState(viewHolder: TaskAdapter.TaskViewHolder) {
        val width = deleteActionWidth(viewHolder.itemView)
        val target = TaskSwipeBounds.settleTarget(
            swipeOffset = currentSwipeOffset,
            actionWidth = width
        )
        val task = adapter.getTask(viewHolder.bindingAdapterPosition)
        adapter.setRevealedTask(if (target < 0f) task?.id else null)
    }
}

private fun deleteActionWidth(view: View): Float =
    view.findViewById<View>(R.id.task_delete_action)
        ?.width
        ?.takeIf { it > 0 }
        ?.toFloat()
        ?: view.resources.getDimension(R.dimen.task_delete_action_width)
