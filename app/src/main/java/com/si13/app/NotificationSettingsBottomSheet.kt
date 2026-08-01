package com.si13.app

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.switchmaterial.SwitchMaterial

class NotificationSettingsBottomSheet : BottomSheetDialogFragment() {
    private lateinit var preferences: ForgettyPreferences

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted && isAdded) {
                MaterialAlertDialogBuilder(requireContext())
                    .setMessage(R.string.notification_permission_denied)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = ForgettyPreferences.create(requireContext())
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext())
        val root = LayoutInflater.from(requireContext())
            .inflate(R.layout.bottom_sheet_notification_settings, null)
        dialog.setContentView(root)
        configureWindow(dialog.window)
        bind(root)
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return@setOnShowListener
            sheet.setBackgroundColor(Color.TRANSPARENT)
            BottomSheetBehavior.from(sheet).apply {
                isFitToContents = true
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        return dialog
    }

    private fun configureWindow(window: Window?) {
        window ?: return
        window.navigationBarColor = requireContext().getColor(R.color.forgetty_surface)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        window.setDimAmount(0.36f)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars =
            resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK !=
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun bind(root: View) {
        ViewCompat.setAccessibilityPaneTitle(root, getString(R.string.notifications))
        root.findViewById<View>(R.id.notification_settings_close).setOnClickListener { dismiss() }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navigation.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)

        val notification = preferences.notificationPreferences
        bindSwitch(
            root,
            R.id.notification_task_reminders_row,
            R.id.notification_task_reminders_switch,
            notification.taskReminders,
            preferences::setTaskReminders
        )
        bindSwitch(
            root,
            R.id.notification_overdue_reminders_row,
            R.id.notification_overdue_reminders_switch,
            notification.overdueReminders,
            preferences::setOverdueReminders
        )
        bindSwitch(
            root,
            R.id.notification_daily_summary_row,
            R.id.notification_daily_summary_switch,
            notification.dailySummary,
            preferences::setDailySummary
        )
    }

    private fun bindSwitch(
        root: View,
        rowId: Int,
        switchId: Int,
        initialValue: Boolean,
        persist: (Boolean) -> Unit
    ) {
        val toggle = root.findViewById<SwitchMaterial>(switchId)
        toggle.isChecked = initialValue
        toggle.setOnCheckedChangeListener { _, enabled ->
            persist(enabled)
            if (enabled) requestNotificationPermission()
        }
        root.findViewById<View>(rowId).setOnClickListener {
            toggle.isChecked = !toggle.isChecked
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val TAG = "NotificationSettingsBottomSheet"

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            NotificationSettingsBottomSheet().show(fragmentManager, TAG)
        }
    }
}
