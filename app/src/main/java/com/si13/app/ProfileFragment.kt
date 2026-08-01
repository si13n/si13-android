package com.si13.app

import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.os.Build
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import coil.load
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ProfileFragment : Fragment(R.layout.fragment_profile) {
    private val users = MutableStateFlow<AuthUser?>(null)
    private lateinit var authRepository: AuthRepository
    private lateinit var taskRepository: TaskRepository
    private lateinit var viewModel: ProfileViewModel
    private lateinit var profilePicture: ImageView
    private lateinit var avatarInitial: TextView
    private lateinit var displayName: TextView
    private lateinit var email: TextView
    private lateinit var completedValue: TextView
    private lateinit var weekValue: TextView
    private lateinit var activeValue: TextView
    private lateinit var rateValue: TextView
    private lateinit var completedValueVertical: TextView
    private lateinit var activeValueVertical: TextView
    private lateinit var rateValueVertical: TextView
    private lateinit var progress: LinearProgressIndicator
    private lateinit var signInButton: View
    private lateinit var signOutButton: MaterialButton
    private lateinit var guestContainer: View
    private lateinit var accountCard: View
    private lateinit var metricsHorizontal: View
    private lateinit var metricsVertical: View
    private lateinit var errorText: TextView
    private lateinit var syncStatus: View
    private lateinit var syncIcon: ImageView
    private lateinit var syncText: TextView
    private lateinit var appearancePreferences: AppearancePreferences
    private lateinit var appearanceValue: TextView
    private lateinit var preferences: ForgettyPreferences
    private lateinit var weeklyActivity: WeeklyActivityView

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted && isAdded) MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.notification_permission_denied)
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private val authStateListener = FirebaseAuth.AuthStateListener { refreshUser() }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authRepository = AuthRepository(requireContext())
        val appContext = requireContext().applicationContext
        taskRepository = TaskRepository.create(appContext)
        appearancePreferences = AppearancePreferences.create(appContext)
        preferences = ForgettyPreferences.create(appContext)
        users.value = authRepository.getCurrentUser()
        viewModel = ViewModelProvider(this, ProfileViewModel.Factory(
            users = users,
            connectivity = AndroidConnectivityObserver(appContext).observeOnline(),
            taskRepositoryFactory = { TaskRepository.create(appContext) },
            signOutAction = {
                FirebaseAuth.getInstance().signOut()
                authRepository.clear()
                users.value = null
            }
        ))[ProfileViewModel::class.java]

        bindViews(view)
        weeklyActivity = view.findViewById(R.id.profile_weekly_activity)
        // Avoid showing the default guest state while the combined flow initializes.
        render(ProfileUiState(user = users.value))
        signInButton.setOnClickListener { signIn() }
        signOutButton.setOnClickListener { confirmSignOut() }
        view.findViewById<View>(R.id.profile_appearance_row).setOnClickListener {
            showAppearanceDialog()
        }
        view.findViewById<View>(R.id.profile_manage_lists_row).setOnClickListener {
            ListManagerBottomSheet.show(parentFragmentManager)
        }
        view.findViewById<View>(R.id.profile_delete_all_tasks_row).setOnClickListener {
            confirmDeleteAllTasks()
        }
        view.findViewById<View>(R.id.profile_content).addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            val vertical = resources.configuration.screenWidthDp < 360
            metricsVertical.isVisible = vertical
            metricsHorizontal.isVisible = !vertical
        }
        buildExtendedSettings(view.findViewById(R.id.profile_extended_settings))

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect(::render)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        FirebaseAuth.getInstance().addAuthStateListener(authStateListener)
        refreshUser()
    }

    override fun onStop() {
        FirebaseAuth.getInstance().removeAuthStateListener(authStateListener)
        super.onStop()
    }

    private fun bindViews(view: View) {
        profilePicture = view.findViewById(R.id.profile_picture_image)
        avatarInitial = view.findViewById(R.id.profile_avatar_initial)
        displayName = view.findViewById(R.id.profile_status_text)
        email = view.findViewById(R.id.profile_email_text)
        completedValue = view.findViewById(R.id.profile_completed_value)
        weekValue = view.findViewById(R.id.profile_week_value)
        activeValue = view.findViewById(R.id.profile_active_value)
        rateValue = view.findViewById(R.id.profile_rate_value)
        completedValueVertical = view.findViewById(R.id.profile_completed_value_vertical)
        activeValueVertical = view.findViewById(R.id.profile_active_value_vertical)
        rateValueVertical = view.findViewById(R.id.profile_rate_value_vertical)
        progress = view.findViewById(R.id.profile_progress_indicator)
        signInButton = view.findViewById(R.id.profile_sign_in_button)
        signOutButton = view.findViewById(R.id.profile_sign_out_button)
        guestContainer = view.findViewById(R.id.profile_guest_container)
        accountCard = view.findViewById(R.id.profile_account_card)
        metricsHorizontal = view.findViewById(R.id.profile_metrics_horizontal)
        metricsVertical = view.findViewById(R.id.profile_metrics_vertical)
        errorText = view.findViewById(R.id.profile_error_text)
        syncStatus = view.findViewById(R.id.profile_sync_status)
        syncIcon = view.findViewById(R.id.profile_sync_icon)
        syncText = view.findViewById(R.id.profile_sync_text)
        appearanceValue = view.findViewById(R.id.profile_appearance_value)
    }

    private fun render(state: ProfileUiState) {
        accountCard.isVisible = state.showProfileCard
        signOutButton.isVisible = state.showSignOut
        guestContainer.isVisible = state.user == null
        setProgress(state)
        renderSyncStatus(state.isOnline)
        appearanceValue.setText(appearancePreferences.mode.labelRes)
        weeklyActivity.values = state.weeklyActivity
        state.user?.let(::renderUser)
    }

    private fun showAppearanceDialog() {
        val modes = AppearanceMode.entries.toTypedArray()
        val labels = modes.map { getString(it.labelRes) }.toTypedArray()
        val selectedIndex = modes.indexOf(appearancePreferences.mode)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.appearance)
            .setSingleChoiceItems(labels, selectedIndex) { dialog, which ->
                appearancePreferences.setMode(modes[which])
                dialog.dismiss()
            }
            .show()
    }

    private fun renderUser(user: AuthUser) {
        val name = user.displayName?.takeIf { it.isNotBlank() } ?: getString(R.string.your_account)
        displayName.text = name
        email.text = user.email?.takeIf { it.isNotBlank() } ?: getString(R.string.unknown_email)
        val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()
        avatarInitial.text = initial
        val hasPhoto = !user.photoUrl.isNullOrBlank()
        profilePicture.isVisible = hasPhoto
        avatarInitial.isVisible = !hasPhoto
        if (hasPhoto) profilePicture.load(user.photoUrl)
    }

    private fun setProgress(state: ProfileUiState) {
        val rate = getString(R.string.percentage_format, state.completionRate)
        completedValue.text = state.completedToday.toString()
        weekValue.text = state.completedThisWeek.toString()
        activeValue.text = state.activeTaskCount.toString()
        rateValue.text = rate
        completedValueVertical.text = state.completedTaskCount.toString()
        activeValueVertical.text = state.activeTaskCount.toString()
        rateValueVertical.text = rate
        progress.progress = state.completionRate
    }

    private fun buildExtendedSettings(container: LinearLayout) {
        container.removeAllViews()
        container.addSettingsGroup(R.string.task_preferences, R.id.profile_task_preferences_card) {
            addRow(R.string.default_filter, preferences.defaultFilter) { showDefaultFilterDialog() }
            addRow(R.string.default_list, preferences.defaultList) { showDefaultListDialog() }
            addRow(R.string.default_reminder_time, formatMinutes(preferences.defaultReminderMinutes)) { showReminderTimeDialog() }
            addRow(R.string.start_of_week, preferences.startOfWeek.name.lowercase().replaceFirstChar(Char::titlecase)) { showStartWeekDialog() }
            addSwitch(R.string.show_completed_tasks, preferences.showCompleted) { preferences.showCompleted = it }
            addSwitch(R.string.confirm_before_deleting, preferences.confirmBeforeDeleting) { preferences.confirmBeforeDeleting = it }
        }

        val notification = preferences.notificationPreferences
        container.addSettingsGroup(R.string.notifications, R.id.profile_notifications_card) {
            addSwitch(R.string.task_reminders, notification.taskReminders) { enabled ->
                preferences.setTaskReminders(enabled); if (enabled) requestNotificationPermission()
            }
            addSwitch(R.string.overdue_reminders, notification.overdueReminders) { preferences.setOverdueReminders(it) }
            addSwitch(R.string.daily_summary, notification.dailySummary) { enabled ->
                preferences.setDailySummary(enabled); if (enabled) requestNotificationPermission()
            }
            addSwitch(R.string.shared_list_updates, notification.sharedListUpdates) { preferences.setSharedListUpdates(it) }
        }

        container.addSettingsGroup(R.string.data_and_sync, R.id.profile_data_sync_card) {
            addRow(R.string.export_tasks, getString(R.string.export_tasks_summary)) { exportTasks() }
            addRow(R.string.delete_completed_tasks, getString(R.string.delete_completed_tasks_summary)) { confirmDeleteCompleted() }
        }

        container.addSettingsGroup(R.string.lists_and_collaboration, R.id.profile_lists_collaboration_card) {
            addRow(R.string.manage_lists, getString(R.string.manage_lists_summary)) { ListManagerBottomSheet.show(parentFragmentManager) }
            addRow(R.string.shared_lists, getString(R.string.shared_lists_summary)) { showBackendRequirement() }
            addRow(R.string.pending_invitations, getString(R.string.no_pending_invitations)) { showBackendRequirement() }
        }

        val version = runCatching {
            requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
        }.getOrNull().orEmpty()
        container.addSettingsGroup(R.string.app_information, R.id.profile_app_information_card) {
            addRow(R.string.app_version, version, showChevron = false)
            addRow(R.string.privacy, getString(R.string.opens_policy)) { showPolicy(R.string.privacy) }
            addRow(R.string.terms, getString(R.string.opens_policy)) { showPolicy(R.string.terms) }
            addRow(R.string.send_feedback, getString(R.string.send_feedback_summary)) { sendFeedback() }
        }
    }

    private fun LinearLayout.addSettingsGroup(
        titleRes: Int,
        cardId: Int,
        buildRows: LinearLayout.() -> Unit
    ) {
        addView(TextView(context).apply {
            setText(titleRes)
            textSize = 18f
            setTextColor(context.getColor(R.color.forgetty_text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(22), 0, dp(8))
        })
        val rows = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            buildRows()
        }
        addView(MaterialCardView(context).apply {
            id = cardId
            radius = dp(20).toFloat()
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(context.getColor(R.color.profile_surface))
            addView(rows, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun LinearLayout.addRow(
        titleRes: Int,
        summary: String,
        showChevron: Boolean = true,
        action: (() -> Unit)? = null
    ) {
        addSettingsDividerIfNeeded()
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = dp(68)
            isClickable = action != null
            isFocusable = action != null
            foreground = selectableItemForeground()
            setPadding(dp(16), dp(10), dp(16), dp(10))
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    setText(titleRes)
                    textSize = 16f
                    setTextColor(context.getColor(R.color.forgetty_text_primary))
                })
                addView(TextView(context).apply {
                    text = summary
                    textSize = 13f
                    setTextColor(context.getColor(R.color.forgetty_text_secondary))
                    isVisible = summary.isNotBlank()
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (showChevron) {
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_chevron_right)
                    contentDescription = null
                }, LinearLayout.LayoutParams(dp(24), dp(24)))
            }
            setOnClickListener { action?.invoke() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun LinearLayout.addSwitch(titleRes: Int, checked: Boolean, action: (Boolean) -> Unit) {
        addSettingsDividerIfNeeded()
        val toggle = SwitchMaterial(context).apply {
            isChecked = checked
            minimumWidth = dp(48)
            minimumHeight = dp(48)
            contentDescription = getString(titleRes)
            setOnCheckedChangeListener { _, value -> action(value) }
        }
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            isClickable = true
            isFocusable = true
            foreground = selectableItemForeground()
            setPadding(dp(16), dp(8), dp(12), dp(8))
            addView(TextView(context).apply {
                setText(titleRes)
                textSize = 16f
                setTextColor(context.getColor(R.color.forgetty_text_primary))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(toggle)
            setOnClickListener { toggle.isChecked = !toggle.isChecked }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun LinearLayout.addSettingsDividerIfNeeded() {
        if (childCount == 0) return
        addView(View(context).apply {
            setBackgroundColor(context.getColor(R.color.profile_divider))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
            marginStart = dp(16)
        })
    }

    private fun selectableItemForeground() = TypedValue().let { value ->
        requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, value, true)
        ContextCompat.getDrawable(requireContext(), value.resourceId)
    }

    private fun showDefaultFilterDialog() {
        val values = arrayOf("all", "today", "high", "completed")
        val labels = arrayOf(getString(R.string.all_filter), getString(R.string.today_filter), getString(R.string.high_priority_filter), getString(R.string.completed_filter_label))
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.default_filter)
            .setSingleChoiceItems(labels, values.indexOf(preferences.defaultFilter)) { dialog, which ->
                preferences.defaultFilter = values[which]; dialog.dismiss(); rebuildSettings()
            }.show()
    }

    private fun showDefaultListDialog() {
        val lists = TaskListStore.create(requireContext()).getLists()
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.default_list)
            .setSingleChoiceItems(lists.map { it.name }.toTypedArray(), lists.indexOfFirst { it.name == preferences.defaultList }) { dialog, which ->
                preferences.defaultList = lists[which].name; dialog.dismiss(); rebuildSettings()
            }.show()
    }

    private fun showReminderTimeDialog() {
        val labels = arrayOf("08:00", "09:00", "12:00", "18:00")
        val values = intArrayOf(480, 540, 720, 1080)
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.default_reminder_time)
            .setItems(labels) { _, which -> preferences.defaultReminderMinutes = values[which]; rebuildSettings() }.show()
    }

    private fun showStartWeekDialog() {
        val days = arrayOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.SUNDAY)
        val labels = arrayOf(getString(R.string.monday), getString(R.string.sunday))
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.start_of_week)
            .setSingleChoiceItems(labels, days.indexOf(preferences.startOfWeek)) { dialog, which ->
                preferences.startOfWeek = days[which]; dialog.dismiss(); rebuildSettings()
            }.show()
    }

    private fun rebuildSettings() { view?.findViewById<LinearLayout>(R.id.profile_extended_settings)?.let(::buildExtendedSettings) }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun exportTasks() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val uri = TaskExporter.createJson(requireContext(), taskRepository.getTasks())
                startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, getString(R.string.export_tasks)))
            }.onFailure { showMessage(R.string.export_failed) }
        }
    }

    private fun confirmDeleteCompleted() {
        MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.delete_completed_tasks)
            .setMessage(R.string.delete_completed_confirmation).setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ -> viewLifecycleOwner.lifecycleScope.launch { taskRepository.deleteCompletedTasks() } }.show()
    }

    private fun showBackendRequirement() = showMessage(R.string.collaboration_backend_required)
    private fun showPolicy(title: Int) = MaterialAlertDialogBuilder(requireContext()).setTitle(title).setMessage(R.string.policy_not_configured).setPositiveButton(android.R.string.ok, null).show()
    private fun sendFeedback() = startActivity(Intent.createChooser(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:?subject=Forgetty feedback")), getString(R.string.send_feedback)))
    private fun showMessage(message: Int) = MaterialAlertDialogBuilder(requireContext()).setMessage(message).setPositiveButton(android.R.string.ok, null).show()
    private fun formatMinutes(value: Int) = "%02d:%02d".format(value / 60, value % 60)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun renderSyncStatus(isOnline: Boolean) {
        val textRes = if (isOnline) R.string.cloud_sync_on else R.string.cloud_sync_off
        val backgroundRes = if (isOnline) {
            R.drawable.bg_profile_sync_status
        } else {
            R.drawable.bg_profile_sync_off
        }
        val colorRes = if (isOnline) R.color.profile_success else R.color.profile_offline
        val color = ContextCompat.getColor(syncStatus.context, colorRes)
        syncStatus.setBackgroundResource(backgroundRes)
        syncText.setText(textRes)
        syncText.setTextColor(color)
        syncIcon.imageTintList = ColorStateList.valueOf(color)
    }

    private fun signIn() {
        viewLifecycleOwner.lifecycleScope.launch {
            GoogleSignInHandler(requireContext(), requireActivity()).signIn(
                signInButton = signInButton,
                errorText = errorText,
                onSuccess = {
                    TaskImportDialogFragment.showIfLocalTasks(requireContext(), parentFragmentManager)
                    refreshUser()
                }
            )
        }
    }

    private fun confirmSignOut() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.sign_out_confirmation_title)
            .setMessage(R.string.sign_out_confirmation_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.sign_out) { _, _ -> viewModel.signOut() }
            .show()
    }

    private fun confirmDeleteAllTasks() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_all_tasks_title)
            .setMessage(R.string.delete_all_tasks_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        taskRepository.deleteAllTasks()
                    } catch (exception: Exception) {
                        if (!isAdded) return@launch
                        MaterialAlertDialogBuilder(requireContext())
                            .setMessage(R.string.tasks_error)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun refreshUser() {
        if (::authRepository.isInitialized) users.value = authRepository.getCurrentUser()
    }
}
