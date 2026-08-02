package com.si13.app

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class FilterBottomSheet : BottomSheetDialogFragment() {
    private lateinit var listContainer: LinearLayout
    private lateinit var tagGroup: ChipGroup
    private lateinit var applyButton: MaterialButton
    private var selectedList: String? = null
    private val selectedTags = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedList = arguments?.getString(ARG_LIST)?.takeUnless { it == ForgettyPreferences.ALL_TASKS }
        selectedTags += arguments?.getStringArrayList(ARG_TAGS).orEmpty()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext())
        val root = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_filter, null)
        dialog.setContentView(root)
        configureWindow(dialog.window)
        bind(root)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { sheet ->
                sheet.setBackgroundColor(Color.TRANSPARENT)
                BottomSheetBehavior.from(sheet).apply { isFitToContents = true; skipCollapsed = true; state = BottomSheetBehavior.STATE_EXPANDED }
            }
        }
        return dialog
    }

    private fun configureWindow(window: Window?) {
        window ?: return
        window.navigationBarColor = requireContext().getColor(R.color.forgetty_surface)
        window.setDimAmount(0.36f)
    }

    private fun bind(root: View) {
        ViewCompat.setAccessibilityPaneTitle(root, getString(R.string.filter_tasks))
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = dp(16) + navigation.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        listContainer = root.findViewById(R.id.filter_list_container)
        tagGroup = root.findViewById(R.id.filter_tag_group)
        applyButton = root.findViewById(R.id.filter_apply)
        root.findViewById<View>(R.id.filter_close).setOnClickListener { dismiss() }
        root.findViewById<View>(R.id.filter_manage_lists).setOnClickListener {
            ListManagerBottomSheet.show(parentFragmentManager)
        }
        root.findViewById<View>(R.id.filter_clear).setOnClickListener {
            selectedList = null
            selectedTags.clear()
            renderLists()
            renderTags()
            updateApplyLabel()
        }
        renderLists()
        renderTags()
        applyButton.setOnClickListener {
            parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle().apply {
                putString(RESULT_LIST, selectedList ?: ForgettyPreferences.ALL_TASKS)
                putStringArrayList(RESULT_TAGS, ArrayList(selectedTags))
            })
            dismiss()
        }
        updateApplyLabel()
    }

    private fun renderLists() {
        listContainer.removeAllViews()
        val names = listOf(ForgettyPreferences.ALL_TASKS) + TaskListStore.create(requireContext()).getLists().map { it.name }.distinct()
        names.forEachIndexed { index, name ->
            val selected = (selectedList ?: ForgettyPreferences.ALL_TASKS) == name
            listContainer.addView(LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(58)
                setPadding(dp(16), 0, dp(16), 0)
                isClickable = true
                isFocusable = true
                if (selected) background = GradientDrawable().apply { setColor(requireContext().getColor(R.color.forgetty_primary_container)) }
                addView(View(context).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(requireContext().getColor(if (name == ForgettyPreferences.ALL_TASKS) R.color.forgetty_outline else R.color.forgetty_primary)) }; layoutParams = LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(14) } })
                addView(TextView(context).apply { text = name; textSize = 16f; typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT; setTextColor(requireContext().getColor(R.color.forgetty_text_primary)); layoutParams = LinearLayout.LayoutParams(0, -2, 1f) })
                if (selected) addView(ImageView(context).apply { setImageResource(R.drawable.ic_sort_selected_check); imageTintList = ColorStateList.valueOf(requireContext().getColor(R.color.forgetty_primary)); layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)) })
                setOnClickListener { selectedList = name.takeUnless { it == ForgettyPreferences.ALL_TASKS }; renderLists(); updateApplyLabel() }
            })
            if (index < names.lastIndex) listContainer.addView(View(requireContext()).apply { setBackgroundColor(requireContext().getColor(R.color.forgetty_outline_variant)); layoutParams = LinearLayout.LayoutParams(-1, dp(1)) })
        }
    }

    private fun renderTags() {
        tagGroup.removeAllViews()
        val tags = arguments?.getStringArrayList(ARG_AVAILABLE_TAGS).orEmpty()
        tags.forEach { tag -> tagGroup.addView(Chip(requireContext()).apply { text = tag; isCheckable = true; isChecked = tag in selectedTags; setOnCheckedChangeListener { _, checked -> if (checked) selectedTags += tag else selectedTags -= tag; updateApplyLabel() }; chipBackgroundColor = requireContext().getColorStateList(R.color.home_list_chip_background); setTextColor(requireContext().getColorStateList(R.color.home_list_chip_text)) }) }
    }

    private fun updateApplyLabel() = applyButton.setText(getString(R.string.apply_filters, selectedTags.size + if (selectedList != null) 1 else 0))
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val TAG = "FilterBottomSheet"
        const val RESULT_KEY = "filter_result"
        const val RESULT_LIST = "filter_list"
        const val RESULT_TAGS = "filter_tags"
        private const val ARG_LIST = "selected_list"
        private const val ARG_TAGS = "selected_tags"
        private const val ARG_AVAILABLE_TAGS = "available_tags"

        fun show(fragmentManager: FragmentManager, list: String?, tags: Set<String>, lists: List<String>, availableTags: List<String>) {
            if (fragmentManager.findFragmentByTag(TAG) != null) return
            FilterBottomSheet().apply { arguments = Bundle().apply { putString(ARG_LIST, list ?: ForgettyPreferences.ALL_TASKS); putStringArrayList(ARG_TAGS, ArrayList(tags)); putStringArrayList(ARG_AVAILABLE_TAGS, ArrayList(availableTags)) } }.show(fragmentManager, TAG)
        }
    }
}
