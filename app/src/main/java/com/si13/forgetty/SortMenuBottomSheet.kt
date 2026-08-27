package com.si13.forgetty

import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/** Native rendering of the Figma SortMenu sheet. */
class SortMenuBottomSheet : BottomSheetDialogFragment() {
    private data class SortOption(
        val mode: TaskSortMode,
        val labelRes: Int,
        val viewId: Int
    )

    private val currentMode: TaskSortMode
        get() = TaskSortMode.fromKey(arguments?.getString(ARG_CURRENT_SORT).orEmpty())

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext())
        val root = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_sort_menu, null)
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
        val optionContainer = root.findViewById<LinearLayout>(R.id.sort_option_container)
        ViewCompat.setAccessibilityPaneTitle(root, getString(R.string.sort_tasks))

        options.forEach { option ->
            val row = layoutInflater.inflate(R.layout.item_sort_option, optionContainer, false)
            val label = row.findViewById<TextView>(R.id.sort_option_label)
            val check = row.findViewById<ImageView>(R.id.sort_option_check)
            val selected = option.mode == currentMode
            row.id = option.viewId
            row.isSelected = selected
            label.setText(option.labelRes)
            label.setTextColor(
                requireContext().getColor(
                    if (selected) R.color.forgetty_primary else R.color.forgetty_text_primary
                )
            )
            label.typeface = Typeface.create(
                if (selected) "sans-serif-medium" else "sans-serif",
                Typeface.NORMAL
            )
            check.visibility = if (selected) View.VISIBLE else View.INVISIBLE
            row.contentDescription = if (selected) {
                getString(R.string.sort_option_selected, getString(option.labelRes))
            } else {
                getString(option.labelRes)
            }
            row.setOnClickListener { select(option.mode) }
            optionContainer.addView(row)
        }
    }

    private fun select(mode: TaskSortMode) {
        parentFragmentManager.setFragmentResult(
            RESULT_KEY,
            Bundle().apply { putString(RESULT_SORT_KEY, mode.key) }
        )
        dismiss()
    }

    companion object {
        const val TAG = "SortMenuBottomSheet"
        const val RESULT_KEY = "sort_menu_result"
        const val RESULT_SORT_KEY = "sort_key"
        private const val ARG_CURRENT_SORT = "current_sort"

        private val options = listOf(
            SortOption(TaskSortMode.PRIORITY_FIRST, R.string.sort_priority_first, R.id.sort_option_priority),
            SortOption(TaskSortMode.NEWEST_FIRST, R.string.sort_newest_first, R.id.sort_option_newest),
            SortOption(TaskSortMode.OLDEST_FIRST, R.string.sort_oldest_first, R.id.sort_option_oldest),
            SortOption(TaskSortMode.ALPHABETICAL, R.string.sort_alphabetical, R.id.sort_option_alphabetical),
            SortOption(TaskSortMode.DUE_DATE, R.string.sort_due_date, R.id.sort_option_due_date)
        )

        internal fun show(fragmentManager: FragmentManager, currentMode: TaskSortMode) {
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            SortMenuBottomSheet().apply {
                arguments = Bundle().apply { putString(ARG_CURRENT_SORT, currentMode.key) }
            }.show(fragmentManager, TAG)
        }
    }
}
