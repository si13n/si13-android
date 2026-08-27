package com.si13.forgetty

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.ForegroundColorSpan
import android.text.format.DateFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.ImageViewCompat
import androidx.fragment.app.Fragment
import androidx.activity.OnBackPressedCallback
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
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class HomeFragment : Fragment(R.layout.fragment_home) {
    private lateinit var taskRepository: TaskRepository
    private lateinit var taskAdapter: TaskAdapter
    private lateinit var searchAdapter: TaskAdapter
    private lateinit var calendarAdapter: TaskAdapter
    private lateinit var taskSearchInput: TextInputEditText
    private lateinit var taskSearchPanel: View
    private lateinit var taskSearchButton: ImageButton
    private lateinit var taskSettingsButton: ImageButton
    private lateinit var taskFilterButton: ImageButton
    private lateinit var taskSortButton: ImageButton
    private lateinit var homeDateText: TextView
    private lateinit var statusText: TextView
    private lateinit var emptyTasksText: TextView
    private lateinit var emptyTasksHint: TextView
    private lateinit var emptyTasksContainer: View
    private lateinit var taskProgressText: TextView
    private lateinit var taskProgressEncouragement: TextView
    private lateinit var taskProgressIndicator: ProgressBar
    private lateinit var taskList: RecyclerView
    private lateinit var swipeDismissLayout: SwipeDismissLayout
    private lateinit var taskListFilterGroup: ChipGroup
    private lateinit var taskStatusFilterGroup: ChipGroup
    private lateinit var preferences: ForgettyPreferences
    private lateinit var taskListStore: TaskListStore
    private lateinit var calendarContainer: View
    private lateinit var calendarGrid: GridLayout
    private lateinit var calendarAgendaList: RecyclerView
    private lateinit var calendarEmpty: View
    private lateinit var calendarMonthTitle: TextView
    private lateinit var calendarSelectedDate: TextView
    private lateinit var searchResults: RecyclerView
    private lateinit var searchEmpty: View
    private lateinit var searchHelper: View
    private lateinit var homeLoading: View

    private var allTasks: List<Task> = emptyList()
    private var selectedList: String? = null
    private var selectedTags: Set<String> = emptySet()
    private var statusFilter = HomeTaskFilter.ALL
    private var searchQuery = ""
    private var sortMode = TaskSortMode.DUE_DATE
    private var displayMode = HomeDisplayMode.LIST
    private var showCompleted = false
    private var calendarMonth = YearMonth.now()
    private var selectedCalendarDate = LocalDate.now()
    private val taskSourceTracker = TaskSourceTracker()
    private var isAddingTask = false
    private var shouldScrollToTopAfterRender = false
    private var taskIdToScrollAfterRender: String? = null
    private var pendingTaskIdToOpen: String? = null
    private var suppressTaskOpenForCurrentTouch = false
    private var taskObservationJob: Job? = null
    private val taskDetailsUpdateMutex = Mutex()
    private val authStateListener = FirebaseAuth.AuthStateListener {
        refreshTaskSourceIfNeeded()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        taskRepository = TaskRepository.create(requireContext())
        preferences = ForgettyPreferences.create(requireContext())
        taskListStore = TaskListStore.create(requireContext())
        selectedList = preferences.selectedList.takeUnless { it == ForgettyPreferences.ALL_TASKS }
        statusFilter = HomeTaskFilter.fromKey(preferences.homeFilter)
        sortMode = TaskSortMode.fromKey(preferences.sortMode)
        displayMode = preferences.displayMode
        showCompleted = preferences.showCompleted
        taskSearchInput = view.findViewById(R.id.task_search_input)
        taskSearchPanel = view.findViewById(R.id.home_search_overlay)
        taskSearchButton = view.findViewById(R.id.task_search_button)
        taskSettingsButton = view.findViewById(R.id.task_settings_button)
        taskFilterButton = view.findViewById(R.id.task_filter_button)
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
        calendarContainer = view.findViewById(R.id.home_calendar_container)
        calendarGrid = view.findViewById(R.id.calendar_grid)
        calendarAgendaList = view.findViewById(R.id.calendar_agenda_list)
        calendarEmpty = view.findViewById(R.id.calendar_empty)
        calendarMonthTitle = view.findViewById(R.id.calendar_month_title)
        calendarSelectedDate = view.findViewById(R.id.calendar_selected_date)
        searchResults = view.findViewById(R.id.search_results)
        searchEmpty = view.findViewById(R.id.search_empty)
        searchHelper = view.findViewById(R.id.search_helper)
        homeLoading = view.findViewById(R.id.home_loading)

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
                if (suppressTaskOpenForCurrentTouch) {
                    suppressTaskOpenForCurrentTouch = false
                } else {
                    TaskDetailsBottomSheet.show(childFragmentManager, task)
                }
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

        searchAdapter = simpleTaskAdapter()
        searchResults.layoutManager = LinearLayoutManager(requireContext())
        searchResults.adapter = searchAdapter
        calendarAdapter = simpleTaskAdapter()
        calendarAgendaList.layoutManager = LinearLayoutManager(requireContext())
        calendarAgendaList.adapter = calendarAdapter

        swipeDismissLayout.onTouchDown = { event ->
            val inside = isTouchInsideRevealedTask(event.rawX.toInt(), event.rawY.toInt())
            if (!inside) {
                suppressTaskOpenForCurrentTouch = taskAdapter.closeRevealedAction()
            }
        }

        configureSearch(view)
        configureFilters(view)
        configureCalendar(view)
        childFragmentManager.setFragmentResultListener(
            ListManagerBottomSheet.RESULT_KEY,
            viewLifecycleOwner
        ) { _, _ ->
            populateListChips()
            renderTasks()
        }
        childFragmentManager.setFragmentResultListener(
            SortMenuBottomSheet.RESULT_KEY,
            viewLifecycleOwner
        ) { _, result ->
            sortMode = TaskSortMode.fromKey(
                result.getString(SortMenuBottomSheet.RESULT_SORT_KEY).orEmpty()
            )
            preferences.sortMode = sortMode.key
            renderTasks()
        }
        childFragmentManager.setFragmentResultListener(
            FilterBottomSheet.RESULT_KEY,
            viewLifecycleOwner
        ) { _, result ->
            selectedList = result.getString(FilterBottomSheet.RESULT_LIST)
                ?.takeUnless { it == ForgettyPreferences.ALL_TASKS }
            selectedTags = result.getStringArrayList(FilterBottomSheet.RESULT_TAGS)?.toSet().orEmpty()
            preferences.selectedList = selectedList ?: ForgettyPreferences.ALL_TASKS
            renderTasks()
        }
        childFragmentManager.setFragmentResultListener(
            AddTaskBottomSheet.RESULT_KEY,
            viewLifecycleOwner
        ) { _, result ->
            taskIdToScrollAfterRender = result.getString(AddTaskBottomSheet.RESULT_TASK_ID)
            renderTasks()
        }
        bindActions()
        bindLoginResult()
        updateCurrentDate()
        observeTasks()

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() = closeSearch()
            }.also { callback ->
                taskSearchPanel.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    callback.isEnabled = taskSearchPanel.isVisible
                }
            }
        )
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
        if (::searchResults.isInitialized) searchResults.adapter = null
        if (::calendarAgendaList.isInitialized) calendarAgendaList.adapter = null
        super.onDestroyView()
    }

    private fun simpleTaskAdapter() = TaskAdapter(
        onCompletedChanged = { task, completed ->
            viewLifecycleOwner.lifecycleScope.launch {
                runCatching { taskRepository.setTaskCompleted(task, completed) }
                    .onFailure { showError() }
            }
        },
        onPriorityClicked = ::cycleTaskPriority,
        onDeleteClicked = ::deleteTask,
        onTaskClicked = { TaskDetailsBottomSheet.show(childFragmentManager, it) }
    )

    private fun configureSearch(view: View) {
        taskSearchInput.doAfterTextChanged {
            searchQuery = it?.toString().orEmpty().trim()
            view.findViewById<View>(R.id.task_search_clear).isVisible = searchQuery.isNotEmpty()
            renderTasks()
        }
        view.findViewById<View>(R.id.task_search_close).setOnClickListener { closeSearch() }
        view.findViewById<View>(R.id.task_search_clear).setOnClickListener { taskSearchInput.text?.clear() }
        view.findViewById<View>(R.id.search_empty_clear).setOnClickListener { taskSearchInput.text?.clear() }
    }

    private fun openSearch() {
        taskSearchPanel.isVisible = true
        (activity as? MainActivity)?.setBottomNavigationVisible(false)
        taskSearchInput.requestFocus()
        taskSearchInput.post {
            requireContext().getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(taskSearchInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun closeSearch() {
        taskSearchInput.text?.clear()
        taskSearchPanel.isVisible = false
        (activity as? MainActivity)?.setBottomNavigationVisible(true)
        hideKeyboard()
    }

    private fun configureFilters(view: View) {
        populateListChips()
        val filterId = when (statusFilter) {
            HomeTaskFilter.TODAY -> R.id.status_filter_today
            HomeTaskFilter.HIGH_PRIORITY -> R.id.status_filter_high
            HomeTaskFilter.COMPLETED -> R.id.status_filter_completed
            HomeTaskFilter.ALL -> R.id.status_filter_all
        }
        taskStatusFilterGroup.check(filterId)
        taskListFilterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedList = checkedIds.firstOrNull()?.let { id ->
                taskListFilterGroup.findViewById<Chip>(id)?.tag as? String
            }?.takeUnless { it == ForgettyPreferences.ALL_TASKS }
            preferences.selectedList = selectedList ?: ForgettyPreferences.ALL_TASKS
            renderTasks()
        }
        taskStatusFilterGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            statusFilter = when (checkedIds.firstOrNull()) {
                R.id.status_filter_today -> HomeTaskFilter.TODAY
                R.id.status_filter_high -> HomeTaskFilter.HIGH_PRIORITY
                R.id.status_filter_completed -> HomeTaskFilter.COMPLETED
                else -> HomeTaskFilter.ALL
            }
            preferences.homeFilter = statusFilter.key
            renderTasks()
        }
        view.findViewById<View>(R.id.list_view_button).setOnClickListener { setDisplayMode(HomeDisplayMode.LIST) }
        view.findViewById<View>(R.id.calendar_view_button).setOnClickListener { setDisplayMode(HomeDisplayMode.CALENDAR) }
        setDisplayMode(displayMode)
    }

    private fun populateListChips() {
        taskListFilterGroup.removeAllViews()
        val density = resources.displayMetrics.density
        val compactHeight = (30 * density).toInt()
        val lists = listOf(TaskListDefinition("all", ForgettyPreferences.ALL_TASKS, "#5268D8", protected = true)) +
            taskListStore.getLists()
        lists.forEach { definition ->
            val chip = Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
                id = View.generateViewId()
                tag = definition.name
                text = if (definition.shared) {
                    SpannableString("${definition.name}  •").apply {
                        setSpan(
                            ForegroundColorSpan(requireContext().getColor(R.color.forgetty_secondary)),
                            length - 1,
                            length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                } else {
                    definition.name
                }
                isCheckable = true
                isCheckedIconVisible = false
                setEnsureMinTouchTargetSize(false)
                minHeight = compactHeight
                chipMinHeight = 30 * density
                chipCornerRadius = 10 * density
                chipStartPadding = 12 * density
                chipEndPadding = 12 * density
                textStartPadding = 0f
                textEndPadding = 0f
                textSize = 13f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(requireContext().getColorStateList(R.color.home_list_chip_text))
                chipBackgroundColor = requireContext().getColorStateList(R.color.home_list_chip_background)
                chipStrokeWidth = 0f
                layoutParams = ChipGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, compactHeight)
                if (definition.id != "all") {
                    chipIcon = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.WHITE)
                        setSize((7 * density).toInt(), (7 * density).toInt())
                    }
                    isChipIconVisible = true
                    chipIconSize = 7 * density
                    iconStartPadding = 0f
                    iconEndPadding = 5 * density
                    val fallbackColor = runCatching { Color.parseColor(definition.color) }
                        .getOrDefault(requireContext().getColor(R.color.forgetty_text_secondary))
                    chipIconTint = ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                        intArrayOf(requireContext().getColor(R.color.forgetty_primary), fallbackColor)
                    )
                }
            }
            taskListFilterGroup.addView(chip)
            if ((selectedList ?: ForgettyPreferences.ALL_TASKS) == definition.name) chip.isChecked = true
        }
        val manage = ImageButton(requireContext()).apply {
            id = View.generateViewId()
            tag = MANAGE_LISTS_TAG
            contentDescription = getString(R.string.manage_lists)
            background = requireContext().getDrawable(R.drawable.bg_manage_lists_button)
            setImageResource(R.drawable.ic_manage_lists)
            ImageViewCompat.setImageTintList(
                this,
                ColorStateList.valueOf(requireContext().getColor(R.color.forgetty_text_secondary))
            )
            val iconPadding = (7 * density).toInt()
            setPadding(iconPadding, iconPadding, iconPadding, iconPadding)
            layoutParams = ChipGroup.LayoutParams(compactHeight, compactHeight)
            setOnClickListener { ListManagerBottomSheet.show(childFragmentManager) }
        }
        taskListFilterGroup.addView(manage)
    }

    private fun setDisplayMode(mode: HomeDisplayMode) {
        displayMode = mode
        preferences.displayMode = mode
        view?.findViewById<View>(R.id.list_view_button)?.isSelected = mode == HomeDisplayMode.LIST
        view?.findViewById<View>(R.id.calendar_view_button)?.isSelected = mode == HomeDisplayMode.CALENDAR
        taskList.isVisible = mode == HomeDisplayMode.LIST
        calendarContainer.isVisible = mode == HomeDisplayMode.CALENDAR
        if (mode == HomeDisplayMode.CALENDAR) renderCalendar()
        renderTasks()
    }

    private fun bindActions() {
        renderShowCompletedControl()
        taskSearchButton.setOnClickListener {
            taskAdapter.closeRevealedAction()
            openSearch()
        }

        taskSettingsButton.setOnClickListener {
            taskAdapter.closeRevealedAction()
            showCompleted = !showCompleted
            preferences.showCompleted = showCompleted
            renderShowCompletedControl()
            renderTasks()
        }

        taskFilterButton.setOnClickListener {
            taskAdapter.closeRevealedAction()
            FilterBottomSheet.show(
                childFragmentManager,
                selectedList,
                selectedTags,
                allTasks.map(Task::listName).distinct(),
                allTasks.flatMap(Task::tags).distinct().sorted()
            )
        }

        taskSortButton.setOnClickListener {
            taskAdapter.closeRevealedAction()
            showTaskMenu()
        }
    }

    private fun renderShowCompletedControl() {
        taskSettingsButton.setImageResource(R.drawable.ic_check_circle)
        ImageViewCompat.setImageTintList(
            taskSettingsButton,
            ColorStateList.valueOf(
                requireContext().getColor(
                    if (showCompleted) R.color.forgetty_primary else R.color.forgetty_text_secondary
                )
            )
        )
        taskSettingsButton.contentDescription = getString(
            if (showCompleted) R.string.hide_completed_tasks else R.string.show_completed_tasks
        )
    }

    private fun hideKeyboard() {
        (requireContext().getSystemService(InputMethodManager::class.java))
            ?.hideSoftInputFromWindow(taskSearchInput.windowToken, 0)
    }

    private fun showTaskMenu() {
        SortMenuBottomSheet.show(childFragmentManager, sortMode)
    }

    private fun configureCalendar(view: View) {
        view.findViewById<View>(R.id.calendar_previous_month).setOnClickListener {
            calendarMonth = calendarMonth.minusMonths(1)
            renderCalendar()
        }
        view.findViewById<View>(R.id.calendar_next_month).setOnClickListener {
            calendarMonth = calendarMonth.plusMonths(1)
            renderCalendar()
        }
        view.findViewById<View>(R.id.calendar_today).setOnClickListener {
            selectedCalendarDate = LocalDate.now()
            calendarMonth = YearMonth.from(selectedCalendarDate)
            renderCalendar()
        }
    }

    private fun renderCalendar() {
        if (!::calendarGrid.isInitialized) return
        val locale = resources.configuration.locales[0] ?: Locale.getDefault()
        calendarMonthTitle.text = calendarMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
        calendarSelectedDate.text = selectedCalendarDate.format(
            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
        )
        calendarGrid.removeAllViews()
        val firstDay = preferences.startOfWeek
        val labels = (0..6).map { firstDay.plus(it.toLong()) }
        labels.forEach { day ->
            calendarGrid.addView(calendarCell(day.getDisplayName(java.time.format.TextStyle.SHORT, locale), false))
        }
        val offset = (7 + calendarMonth.atDay(1).dayOfWeek.value - firstDay.value) % 7
        repeat(offset) { calendarGrid.addView(calendarCell("", false)) }
        val scopedTasks = tasksInSelectedList()
        repeat(calendarMonth.lengthOfMonth()) { index ->
            val date = calendarMonth.atDay(index + 1)
            val hasTask = scopedTasks.any { it.dueDate.toLocalDateOrNull() == date }
            calendarGrid.addView(calendarCell((index + 1).toString(), hasTask, date))
        }
        val agenda = TaskSorter.sort(scopedTasks.filter { it.dueDate.toLocalDateOrNull() == selectedCalendarDate }, sortMode)
        calendarAdapter.submitSections(
            agenda.takeIf { it.isNotEmpty() }?.let {
                listOf(TaskSection(TaskSectionKind.TODAY, it))
            }.orEmpty()
        ) {}
        calendarEmpty.isVisible = agenda.isEmpty()
    }

    private fun calendarCell(label: String, hasTask: Boolean, date: LocalDate? = null): TextView {
        val density = resources.displayMetrics.density
        val locale = resources.configuration.locales[0] ?: Locale.getDefault()
        return TextView(requireContext()).apply {
            layoutParams = GridLayout.LayoutParams().apply {
                width = 0
                height = (42 * density).toInt()
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
            gravity = Gravity.CENTER
            textSize = if (date == null) 11f else 13f
            text = if (hasTask) "$label\n•" else label
            setTextColor(requireContext().getColor(
                when {
                    date == selectedCalendarDate -> R.color.white
                    date == LocalDate.now() -> R.color.forgetty_primary
                    else -> R.color.forgetty_text_primary
                }
            ))
            if (date == selectedCalendarDate) {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(requireContext().getColor(R.color.forgetty_primary))
                }
            }
            if (date != null) {
                isClickable = true
                isFocusable = true
                contentDescription = date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale))
                setOnClickListener {
                    selectedCalendarDate = date
                    renderCalendar()
                }
            }
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

    fun showAddTaskSheet(startVoice: Boolean = false) {
        if (!isAdded || childFragmentManager.findFragmentByTag(AddTaskBottomSheet.TAG) != null) return
        taskAdapter.closeRevealedAction()
        AddTaskBottomSheet().apply {
            arguments = Bundle().apply { putBoolean(AddTaskBottomSheet.ARG_START_VOICE, startVoice) }
        }.show(childFragmentManager, AddTaskBottomSheet.TAG)
    }

    fun openSearchFromExtension() = openSearch()

    fun showTodayFromExtension() {
        taskStatusFilterGroup.check(R.id.status_filter_today)
        setDisplayMode(HomeDisplayMode.LIST)
    }

    fun openTaskFromExtension(taskId: String) {
        val task = allTasks.firstOrNull { it.id == taskId }
        if (task == null) pendingTaskIdToOpen = taskId
        else TaskDetailsBottomSheet.show(childFragmentManager, task)
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

    fun persistTaskFromDetails(updatedTask: Task) {
        allTasks = allTasks.map { if (it.id == updatedTask.id) updatedTask else it }
        renderTasks()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                taskDetailsUpdateMutex.withLock { taskRepository.updateTask(updatedTask) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                showError()
            }
        }
    }

    fun duplicateTaskFromDetails(task: Task) {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching { taskRepository.duplicateTask(task) }.onFailure { showError() }
        }
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
        val listFiltered = tasksInSelectedList()
        val sections = TaskSectioner.sections(
            TaskSorter.sort(listFiltered, sortMode),
            statusFilter,
            LocalDate.now(),
            showCompleted
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
                    taskIdToScrollAfterRender = null
                }
            }
        }
        // The Figma Home card summarizes the full task set, independent of list/filter chips.
        val progressScope = allTasks
        val completedTaskCount = progressScope.count(Task::completed)
        val progressSummary = getString(
            R.string.home_progress_summary,
            completedTaskCount,
            progressScope.size
        )
        val emphasizedPrefix = "$completedTaskCount of ${progressScope.size}"
        taskProgressText.text = SpannableString(progressSummary).apply {
            setSpan(
                StyleSpan(Typeface.BOLD),
                0,
                emphasizedPrefix.length.coerceAtMost(length),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        val completionRate = if (progressScope.isEmpty()) 0 else completedTaskCount * 100 / progressScope.size
        taskProgressIndicator.progress = completionRate
        taskProgressEncouragement.setText(
            HomeProgressPresentation.messageRes(completedTaskCount, progressScope.size)
        )
        statusText.isVisible = false
        homeLoading.isVisible = false

        emptyTasksContainer.isVisible = visibleTaskCount == 0 && displayMode == HomeDisplayMode.LIST
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

        if (taskSearchPanel.isVisible) {
            val searchTasks = if (searchQuery.isBlank()) emptyList() else TaskSorter.sort(listFiltered, sortMode)
            searchAdapter.submitSections(
                searchTasks.takeIf { it.isNotEmpty() }
                    ?.let { listOf(TaskSection(TaskSectionKind.UPCOMING, it)) }
                    .orEmpty()
            ) {}
            searchHelper.isVisible = searchQuery.isBlank()
            searchEmpty.isVisible = searchQuery.isNotBlank() && searchTasks.isEmpty()
            searchResults.isVisible = searchTasks.isNotEmpty()
        }
        if (displayMode == HomeDisplayMode.CALENDAR) renderCalendar()
        pendingTaskIdToOpen?.let { id ->
            allTasks.firstOrNull { it.id == id }?.let { task ->
                pendingTaskIdToOpen = null
                TaskDetailsBottomSheet.show(childFragmentManager, task)
            }
        }
    }

    private fun tasksInSelectedList(includeSearch: Boolean = true): List<Task> = allTasks.filter { task ->
        val matchesList = selectedList == null || task.listName == selectedList || task.listId == selectedList
        val matchesSearch = !includeSearch || searchQuery.isBlank() || listOf(
            task.text,
            task.note,
            task.listName,
            task.tags.joinToString(" ")
        ).any { it.contains(searchQuery, ignoreCase = true) }
        val matchesTags = selectedTags.isEmpty() || selectedTags.any { it in task.tags }
        matchesList && matchesTags && matchesSearch
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
        if (!isAdded || view == null) return
        homeLoading.isVisible = false
        statusText.text = getString(R.string.tasks_error)
        statusText.isVisible = true
    }

    private fun currentUserId(): String? {
        return FirebaseAuth.getInstance().currentUser?.uid
            ?: AuthRepository(requireContext()).getCurrentUser()?.uid
    }

    companion object {
        private const val MANAGE_LISTS_TAG = "manage_lists"
    }

}

internal enum class HomeTaskFilter(val key: String) {
    ALL("all"),
    TODAY("today"),
    HIGH_PRIORITY("high"),
    COMPLETED("completed");

    companion object {
        fun fromKey(key: String) = values().firstOrNull { it.key == key } ?: ALL
    }
}

internal object HomeProgressPresentation {
    fun messageRes(completed: Int, total: Int): Int {
        val percentage = if (total <= 0) 0 else completed.coerceIn(0, total) * 100 / total
        return when {
            percentage == 0 -> R.string.progress_get_started
            percentage < 40 -> R.string.progress_good_start
            percentage < 80 -> R.string.progress_keep_going
            else -> R.string.progress_almost_there
        }
    }
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
    fun sections(
        tasks: List<Task>,
        filter: HomeTaskFilter,
        today: LocalDate,
        showCompleted: Boolean = false
    ): List<TaskSection> {
        val filtered = when (filter) {
            HomeTaskFilter.ALL -> if (showCompleted) tasks else tasks.filterNot { it.completed }
            HomeTaskFilter.TODAY -> tasks.filter {
                !it.completed && it.dueDate.toLocalDateOrNull() == today
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
            if (task.completed) return@forEach
            val dueDate = task.dueDate.toLocalDateOrNull()
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
            if (showCompleted) {
                tasks.filter(Task::completed).takeIf(List<Task>::isNotEmpty)?.let {
                    add(TaskSection(TaskSectionKind.COMPLETED, it))
                }
            }
        }
    }
}

internal enum class TaskSortMode(val key: String) {
    NEWEST_FIRST("newest"),
    OLDEST_FIRST("oldest"),
    PRIORITY_FIRST("priority"),
    ALPHABETICAL("alphabetical"),
    DUE_DATE("due_date");

    companion object {
        fun fromKey(key: String): TaskSortMode = values().firstOrNull { it.key == key } ?: DUE_DATE
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
            TaskSortMode.DUE_DATE -> tasks.sortedWith(
                compareBy<Task> { it.dueDate.toLocalDateOrNull() ?: LocalDate.MAX }
                    .thenByDescending { it.priority?.rank ?: 0 }
                    .thenByDescending { it.createdAt }
                    .thenBy { it.id }
            )
        }
    }
}

private fun String?.toLocalDateOrNull(): LocalDate? = this?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

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
        private val metadataRow: ChipGroup = cell.findViewById(R.id.task_metadata_row)
        private val reminderIcon: View = cell.findViewById(R.id.task_reminder_icon)
        private val repeatIcon: View = cell.findViewById(R.id.task_repeat_icon)
        private val assignees: LinearLayout = cell.findViewById(R.id.task_assignees)
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
            bindMetadata(task)
            checkbox.isChecked = task.completed
            checkbox.setButtonDrawable(
                when {
                    task.completed -> R.drawable.ic_task_checkbox_checked_filled
                    task.priority == TaskPriority.HIGH -> R.drawable.ic_task_checkbox_priority
                    else -> R.drawable.ic_task_checkbox_outline
                }
            )
            checkbox.buttonTintList = null
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
            priorityDot.alpha = 1f
            taskTitle.paintFlags = if (task.completed) {
                taskTitle.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                taskTitle.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            cell.alpha = if (task.completed) COMPLETED_TASK_ALPHA else 1f
            boundTask = task
            updateGroupShape(item.groupPosition)
            foreground.setBackgroundResource(
                if (task.completed) R.drawable.bg_task_card_completed else R.drawable.bg_task_group_single
            )
            foreground.invalidateOutline()
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
            val dueDate = task.dueDate.toLocalDateOrNull()
            if (dueDate == null) {
                taskDueDate.visibility = View.GONE
                taskDueDate.text = null
                taskDueDate.compoundDrawablesRelative[0]?.setTintList(null)
                return
            }
            val today = LocalDate.now()
            val overdue = TaskDatePresentation.isOverdue(dueDate, today)
            val dueToday = dueDate == today
            val daysFromToday = ChronoUnit.DAYS.between(today, dueDate)
            val color = when {
                overdue && !task.completed -> R.color.home_overdue
                dueToday && !task.completed -> R.color.forgetty_primary
                else -> R.color.home_text_secondary
            }
            taskDueDate.visibility = View.VISIBLE
            val locale = taskDueDate.resources.configuration.locales[0] ?: Locale.getDefault()
            val dateLabel = when {
                daysFromToday < 0 -> taskDueDate.context.getString(
                    R.string.home_days_overdue,
                    -daysFromToday
                )
                daysFromToday == 0L -> taskDueDate.context.getString(R.string.home_due_today)
                daysFromToday == 1L -> taskDueDate.context.getString(R.string.home_due_tomorrow)
                daysFromToday in 2L..6L -> dueDate.format(DateTimeFormatter.ofPattern("EEE", locale))
                else -> TaskDatePresentation.formatDate(dueDate, today, locale)
            }
            val timeLabel = task.dueTimeMinutes?.let { minutes ->
                java.time.LocalTime.of(minutes / 60, minutes % 60).format(
                    java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)
                )
            }
            taskDueDate.text = listOfNotNull(dateLabel, timeLabel).joinToString(" ")
            taskDueDate.setTextColor(taskDueDate.context.getColor(color))
            taskDueDate.setTypeface(
                Typeface.create("sans-serif", Typeface.NORMAL),
                if ((overdue || dueToday) && !task.completed) Typeface.BOLD else Typeface.NORMAL
            )
            taskDueDate.compoundDrawablesRelative[0]?.setTint(taskDueDate.context.getColor(color))
        }

        private fun bindMetadata(task: Task) {
            reminderIcon.isVisible = task.reminderAt != null
            repeatIcon.isVisible = task.repeatRule != TaskRepeatRule.NONE
            while (metadataRow.childCount > FIXED_METADATA_CHILD_COUNT) {
                metadataRow.removeViewAt(metadataRow.childCount - 1)
            }
            task.tags.forEach { tag ->
                metadataRow.addView(TextView(metadataRow.context).apply {
                    text = tag
                    textSize = 11f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(context.getColor(R.color.forgetty_on_secondary_container))
                    setBackgroundResource(R.drawable.bg_task_tag)
                    setPadding(dp(6), dp(1), dp(6), dp(1))
                    maxLines = 1
                })
            }
            assignees.removeAllViews()
            assignees.isVisible = task.assigneeIds.isNotEmpty()
            task.assigneeIds.take(2).forEachIndexed { index, id ->
                assignees.addView(TextView(assignees.context).apply {
                    text = id.firstOrNull()?.uppercase() ?: ""
                    gravity = Gravity.CENTER
                    textSize = 9f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(
                        context.getColor(
                            if (index == 0) R.color.forgetty_on_primary_container
                            else R.color.forgetty_on_secondary_container
                        )
                    )
                    setBackgroundResource(
                        if (index == 0) R.drawable.bg_task_assignee_primary
                        else R.drawable.bg_task_assignee_secondary
                    )
                }, LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                    if (index > 0) marginStart = -dp(6)
                })
            }
            metadataRow.isVisible = taskDueDate.isVisible || reminderIcon.isVisible ||
                repeatIcon.isVisible || task.listName.isNotBlank() || task.tags.isNotEmpty()
        }

        private fun bindList(task: Task) {
            taskListName.text = task.listName
            taskListName.setTextColor(taskListName.context.getColor(R.color.forgetty_text_secondary))
        }

        private fun dp(value: Int): Int = (value * cell.resources.displayMetrics.density).toInt()

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
        private const val COMPLETED_TASK_ALPHA = 0.65f
        private const val FIXED_METADATA_CHILD_COUNT = 4
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
