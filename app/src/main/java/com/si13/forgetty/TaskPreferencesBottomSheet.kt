package com.si13.forgetty

import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.time.DayOfWeek

class TaskPreferencesBottomSheet : BottomSheetDialogFragment() {
    private lateinit var preferences: ForgettyPreferences
    private lateinit var defaultFilterValue: TextView
    private lateinit var defaultListValue: TextView
    private lateinit var startWeekValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preferences = ForgettyPreferences.create(requireContext())
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext())
        val root = LayoutInflater.from(requireContext())
            .inflate(R.layout.bottom_sheet_task_preferences, null)
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
        ViewCompat.setAccessibilityPaneTitle(root, getString(R.string.task_preferences))
        root.findViewById<View>(R.id.task_preferences_close).setOnClickListener { dismiss() }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navigation.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)

        defaultFilterValue = root.findViewById(R.id.task_preferences_default_filter_value)
        defaultListValue = root.findViewById(R.id.task_preferences_default_list_value)
        startWeekValue = root.findViewById(R.id.task_preferences_start_week_value)

        root.findViewById<View>(R.id.task_preferences_default_filter_row)
            .setOnClickListener { showDefaultFilterDialog() }
        root.findViewById<View>(R.id.task_preferences_default_list_row)
            .setOnClickListener { showDefaultListDialog() }
        root.findViewById<View>(R.id.task_preferences_start_week_row)
            .setOnClickListener { showStartWeekDialog() }
        renderValues()
    }

    private fun renderValues() {
        defaultFilterValue.setText(
            when (preferences.defaultFilter) {
                "today" -> R.string.today_filter
                "high" -> R.string.high_priority_filter
                "completed" -> R.string.completed_filter_label
                else -> R.string.all_filter
            }
        )
        defaultListValue.text = preferences.defaultList
        startWeekValue.setText(
            if (preferences.startOfWeek == DayOfWeek.SUNDAY) R.string.sunday else R.string.monday
        )
    }

    private fun showDefaultFilterDialog() {
        val values = arrayOf("all", "today", "high", "completed")
        val labels = arrayOf(
            getString(R.string.all_filter),
            getString(R.string.today_filter),
            getString(R.string.high_priority_filter),
            getString(R.string.completed_filter_label)
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.default_filter)
            .setSingleChoiceItems(labels, values.indexOf(preferences.defaultFilter)) { dialog, which ->
                preferences.defaultFilter = values[which]
                renderValues()
                dialog.dismiss()
            }
            .show()
    }

    private fun showDefaultListDialog() {
        val lists = TaskListStore.create(requireContext()).getLists()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.default_list)
            .setSingleChoiceItems(
                lists.map { it.name }.toTypedArray(),
                lists.indexOfFirst { it.name == preferences.defaultList }
            ) { dialog, which ->
                preferences.defaultList = lists[which].name
                renderValues()
                dialog.dismiss()
            }
            .show()
    }

    private fun showStartWeekDialog() {
        val days = arrayOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)
        val labels = arrayOf(getString(R.string.monday), getString(R.string.sunday))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.start_of_week)
            .setSingleChoiceItems(labels, days.indexOf(preferences.startOfWeek)) { dialog, which ->
                preferences.startOfWeek = days[which]
                renderValues()
                dialog.dismiss()
            }
            .show()
    }

    companion object {
        const val TAG = "TaskPreferencesBottomSheet"

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            TaskPreferencesBottomSheet().show(fragmentManager, TAG)
        }
    }
}
