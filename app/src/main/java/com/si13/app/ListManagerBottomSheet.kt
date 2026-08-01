package com.si13.app

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** Native list-management surface backed by the same task repository as Home. */
class ListManagerBottomSheet : BottomSheetDialogFragment() {
    private lateinit var store: TaskListStore
    private lateinit var repository: TaskRepository
    private lateinit var rows: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TaskListStore.create(requireContext())
        repository = TaskRepository.create(requireContext())
    }

    override fun onCreateView(inflater: android.view.LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val density = resources.displayMetrics.density
        rows = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((20 * density).toInt(), (12 * density).toInt(), (20 * density).toInt(), (28 * density).toInt())
        }
        val handle = View(requireContext()).apply {
            background = com.google.android.material.shape.MaterialShapeDrawable().apply {
                fillColor = android.content.res.ColorStateList.valueOf(requireContext().getColor(R.color.forgetty_outline))
                shapeAppearanceModel = shapeAppearanceModel.toBuilder().setAllCornerSizes(8 * density).build()
            }
            layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), (4 * density).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (16 * density).toInt()
            }
        }
        rows.addView(handle)
        rows.addView(TextView(requireContext()).apply {
            setText(R.string.manage_lists)
            textSize = 22f
            setTextColor(requireContext().getColor(R.color.forgetty_text_primary))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        rows.addView(TextView(requireContext()).apply {
            setText(R.string.manage_lists_description)
            textSize = 13f
            setTextColor(requireContext().getColor(R.color.forgetty_text_secondary))
            setPadding(0, (4 * density).toInt(), 0, (12 * density).toInt())
        })
        renderRows()
        return ScrollView(requireContext()).apply { addView(rows) }
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.behavior?.state = BottomSheetBehavior.STATE_EXPANDED
    }

    private fun renderRows() {
        while (rows.childCount > 2) rows.removeViewAt(2)
        store.getLists().forEach { definition -> rows.addView(listRow(definition)) }
        rows.addView(MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            setText(R.string.create_new_list)
            icon = requireContext().getDrawable(R.drawable.ic_add)
            isAllCaps = false
            setOnClickListener { showNameDialog(null) }
        })
    }

    private fun listRow(definition: TaskListDefinition): View {
        val density = resources.displayMetrics.density
        return LinearLayout(requireContext()).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = (64 * density).toInt()
            val colorDot = TextView(context).apply {
                text = "●"
                textSize = 20f
                setTextColor(runCatching { Color.parseColor(definition.color) }.getOrDefault(Color.GRAY))
                contentDescription = null
            }
            addView(colorDot, LinearLayout.LayoutParams((32 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(TextView(context).apply {
                text = if (definition.shared) "${definition.name}\n${getString(R.string.shared_list)}" else definition.name
                textSize = 15f
                setTextColor(context.getColor(R.color.forgetty_text_primary))
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(MaterialButton(context).apply {
                text = getString(R.string.edit)
                isAllCaps = false
                minWidth = (48 * density).toInt()
                setOnClickListener { showNameDialog(definition) }
            })
            if (!definition.protected) addView(MaterialButton(context).apply {
                setIconResource(R.drawable.ic_delete)
                text = ""
                contentDescription = getString(R.string.delete_list_accessibility, definition.name)
                minWidth = (48 * density).toInt()
                setOnClickListener { confirmDelete(definition) }
            })
        }
    }

    private fun showNameDialog(existing: TaskListDefinition?) {
        val input = TextInputEditText(requireContext()).apply {
            setText(existing?.name.orEmpty())
            setSelection(text?.length ?: 0)
            maxLines = 1
        }
        val field = TextInputLayout(requireContext()).apply {
            hint = getString(R.string.list_name)
            setPadding(24, 4, 24, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(if (existing == null) R.string.create_new_list else R.string.rename_list)
            .setView(field)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = input.text?.toString().orEmpty()
                if (existing == null) {
                    store.create(name, TaskListStore.COLORS[store.getLists().size % TaskListStore.COLORS.size])
                    notifyChanged()
                    renderRows()
                } else {
                    store.rename(existing.id, name)?.let { (oldName, updated) ->
                        lifecycleScope.launch {
                            repository.renameList(oldName, updated.name)
                            notifyChanged()
                            renderRows()
                        }
                    }
                }
            }.show()
    }

    private fun confirmDelete(definition: TaskListDefinition) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_list_title, definition.name))
            .setMessage(R.string.delete_list_move_tasks)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                lifecycleScope.launch {
                    repository.moveTasksFromList(definition.name)
                    store.delete(definition.id)
                    notifyChanged()
                    renderRows()
                }
            }.show()
    }

    private fun notifyChanged() {
        parentFragmentManager.setFragmentResult(RESULT_KEY, bundleOf("changed" to true))
    }

    companion object {
        const val RESULT_KEY = "forgetty_lists_changed"
        private const val TAG = "ListManagerBottomSheet"
        fun show(manager: FragmentManager) = ListManagerBottomSheet().show(manager, TAG)
    }
}
