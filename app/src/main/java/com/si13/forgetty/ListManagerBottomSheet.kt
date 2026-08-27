package com.si13.forgetty

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

/** Native list manager matching the Figma list, editor, and confirmation states. */
class ListManagerBottomSheet : BottomSheetDialogFragment() {
    private enum class Mode { LIST, CREATE, EDIT }

    private lateinit var store: TaskListStore
    private lateinit var repository: TaskRepository
    private lateinit var preferences: ForgettyPreferences
    private lateinit var content: LinearLayout
    private lateinit var headerTitle: TextView
    private lateinit var backButton: ImageButton
    private lateinit var headerSpacer: Space
    private var mode = Mode.LIST
    private var editTargetId: String? = null
    private var inputName = ""
    private var inputColor = TaskListStore.COLORS.first()
    private var taskCounts: Map<String, Int> = emptyMap()
    private var deleteDialog: Dialog? = null
    private var saving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = TaskListStore.create(requireContext())
        repository = TaskRepository.create(requireContext())
        preferences = ForgettyPreferences.create(requireContext())
        mode = savedInstanceState?.getString(STATE_MODE)
            ?.let { runCatching { Mode.valueOf(it) }.getOrNull() } ?: Mode.LIST
        editTargetId = savedInstanceState?.getString(STATE_EDIT_TARGET)
        inputName = savedInstanceState?.getString(STATE_INPUT_NAME).orEmpty()
        inputColor = savedInstanceState?.getString(STATE_INPUT_COLOR) ?: TaskListStore.COLORS.first()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = BottomSheetDialog(requireContext())
        val root = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_list_manager, null)
        dialog.setContentView(root)
        configureWindow(dialog.window)
        dialog.setOnShowListener {
            val sheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?: return@setOnShowListener
            sheet.setBackgroundColor(Color.TRANSPARENT)
            BottomSheetBehavior.from(sheet).apply {
                isFitToContents = true
                skipCollapsed = true
                state = BottomSheetBehavior.STATE_EXPANDED
            }
            bind(root)
        }
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP && mode != Mode.LIST) {
                showListMode()
                true
            } else {
                false
            }
        }
        return dialog
    }

    private fun configureWindow(window: Window?) {
        window ?: return
        window.navigationBarColor = requireContext().getColor(R.color.forgetty_surface)
        window.setDimAmount(0.36f)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightNavigationBars =
            resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK !=
                android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun bind(root: View) {
        content = root.findViewById(R.id.list_manager_content)
        headerTitle = root.findViewById(R.id.list_manager_title)
        backButton = root.findViewById(R.id.list_manager_back)
        headerSpacer = root.findViewById(R.id.list_manager_header_spacer)
        root.findViewById<View>(R.id.list_manager_close).setOnClickListener { dismiss() }
        backButton.setOnClickListener { showListMode() }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val navigation = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.updatePadding(bottom = navigation.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
        renderMode()
        lifecycleScope.launch {
            taskCounts = repository.getTasks().groupingBy(Task::listName).eachCount()
            if (isAdded && mode == Mode.LIST) renderList()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_MODE, mode.name)
        outState.putString(STATE_EDIT_TARGET, editTargetId)
        outState.putString(STATE_INPUT_NAME, inputName)
        outState.putString(STATE_INPUT_COLOR, inputColor)
    }

    override fun onDestroyView() {
        deleteDialog?.dismiss()
        deleteDialog = null
        super.onDestroyView()
    }

    private fun renderMode() {
        headerTitle.setText(
            when (mode) {
                Mode.LIST -> R.string.manage_lists
                Mode.CREATE -> R.string.new_list
                Mode.EDIT -> R.string.edit_list
            }
        )
        backButton.isVisible = mode != Mode.LIST
        headerSpacer.isVisible = mode == Mode.LIST
        if (mode == Mode.LIST) renderList() else renderEditor()
    }

    private fun showListMode() {
        hideKeyboard()
        mode = Mode.LIST
        editTargetId = null
        saving = false
        renderMode()
    }

    private fun openCreate() {
        mode = Mode.CREATE
        editTargetId = null
        inputName = ""
        inputColor = TaskListStore.COLORS.first()
        renderMode()
    }

    private fun openEdit(list: TaskListDefinition) {
        mode = Mode.EDIT
        editTargetId = list.id
        inputName = list.name
        inputColor = list.color
        renderMode()
    }

    private fun renderList() {
        content.removeAllViews()
        val lists = store.getLists()
        val card = MaterialCardView(requireContext()).apply {
            id = R.id.list_manager_list_card
            radius = dp(16).toFloat()
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(context.getColor(R.color.forgetty_surface_low))
        }
        val rows = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        lists.forEachIndexed { index, list ->
            rows.addView(listRow(list))
            if (index < lists.lastIndex) rows.addView(divider())
        }
        card.addView(rows)
        content.addView(card, matchWidth().apply { bottomMargin = dp(12) })

        content.addView(MaterialButton(requireContext()).apply {
            id = R.id.list_manager_create
            setText(R.string.create_new_list)
            setTextColor(context.getColor(R.color.forgetty_primary))
            textSize = 15f
            letterSpacing = 0f
            isAllCaps = false
            typeface = Typeface.create(typeface, Typeface.BOLD)
            icon = ContextCompat.getDrawable(context, R.drawable.ic_add)
            iconTint = ColorStateList.valueOf(context.getColor(R.color.forgetty_primary))
            iconPadding = dp(8)
            backgroundTintList = null
            setBackgroundResource(R.drawable.bg_list_create_button)
            minimumHeight = dp(52)
            insetTop = 0
            insetBottom = 0
            contentDescription = getString(R.string.create_new_list)
            setOnClickListener { openCreate() }
        }, matchWidth(dp(52)))
    }

    private fun listRow(list: TaskListDefinition): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(68)
        setPadding(dp(14), 0, dp(4), 0)

        addView(colorDot(list.color), LinearLayout.LayoutParams(dp(10), dp(10)).apply {
            marginEnd = dp(12)
        })

        val identity = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = list.name
                textSize = 15f
                setTextColor(context.getColor(R.color.forgetty_text_primary))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            if (list.shared) addView(TextView(context).apply {
                setText(R.string.shared_list)
                textSize = 11f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(context.getColor(R.color.forgetty_secondary))
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(6)
            })
        }
        addView(identity, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        addView(TextView(context).apply {
            text = (taskCounts[list.name] ?: 0).toString()
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(context.getColor(R.color.forgetty_text_secondary))
        }, LinearLayout.LayoutParams(dp(36), dp(48)))

        addView(iconButton(R.drawable.ic_edit, getString(R.string.edit)).apply {
            tag = "list-edit-${list.name}"
            setOnClickListener { openEdit(list) }
        })
        addView(iconButton(R.drawable.ic_delete, getString(R.string.delete_list_accessibility, list.name)).apply {
            ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(context.getColor(R.color.forgetty_error)))
            setOnClickListener { showDeleteConfirmation(list) }
        })
    }

    private fun renderEditor() {
        content.removeAllViews()
        val field = TextInputLayout(requireContext()).apply {
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
            hint = getString(R.string.list_name)
            setBoxCornerRadii(dp(14f), dp(14f), dp(14f), dp(14f))
        }
        val nameInput = TextInputEditText(field.context).apply {
            id = R.id.list_manager_name
            setText(inputName)
            setSelection(text?.length ?: 0)
            hint = getString(R.string.list_name_example)
            filters = arrayOf(InputFilter.LengthFilter(MAX_LIST_NAME_LENGTH))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 1
            imeOptions = EditorInfo.IME_ACTION_DONE
        }
        field.addView(nameInput)
        content.addView(field, matchWidth().apply { bottomMargin = dp(20) })

        content.addView(TextView(requireContext()).apply {
            setText(R.string.list_color)
            textSize = 12f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(context.getColor(R.color.forgetty_text_secondary))
        }, matchWidth().apply { bottomMargin = dp(10) })

        val colorGrid = GridLayout(requireContext()).apply {
            id = R.id.list_manager_colors
            columnCount = colorColumnCount()
            alignmentMode = GridLayout.ALIGN_BOUNDS
        }
        TaskListStore.COLORS.forEach { color ->
            val swatch = ListColorView(requireContext()).apply {
                colorValue = Color.parseColor(color)
                isChecked = color.equals(inputColor, ignoreCase = true)
                contentDescription = getString(R.string.select_list_color, color)
                setOnClickListener {
                    inputColor = color
                    renderEditor()
                }
            }
            colorGrid.addView(swatch, ViewGroup.LayoutParams(dp(48), dp(48)))
        }
        content.addView(colorGrid, matchWidth().apply { bottomMargin = dp(24) })

        val preview = MaterialCardView(requireContext()).apply {
            id = R.id.list_manager_preview
            radius = dp(14).toFloat()
            cardElevation = 0f
            strokeWidth = 0
            setCardBackgroundColor(context.getColor(R.color.forgetty_surface_low))
            isVisible = inputName.trim().isNotEmpty()
            contentDescription = getString(R.string.list_preview)
        }
        preview.addView(LinearLayout(requireContext()).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), 0, dp(16), 0)
            addView(colorDot(inputColor), LinearLayout.LayoutParams(dp(10), dp(10)).apply { marginEnd = dp(10) })
            addView(TextView(context).apply {
                text = inputName
                textSize = 15f
                setTextColor(context.getColor(R.color.forgetty_text_primary))
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        })
        content.addView(preview, matchWidth(dp(52)).apply { bottomMargin = dp(20) })

        val saveButton = MaterialButton(requireContext()).apply {
            id = R.id.list_manager_save
            setText(if (mode == Mode.CREATE) R.string.create_list else R.string.save_changes)
            textSize = 16f
            letterSpacing = 0f
            isAllCaps = false
            typeface = Typeface.create(typeface, Typeface.BOLD)
            cornerRadius = dp(16)
            minimumHeight = dp(52)
            insetTop = 0
            insetBottom = 0
            isEnabled = inputName.trim().isNotEmpty() && !saving
            setOnClickListener { saveEditor() }
        }
        content.addView(saveButton, matchWidth(dp(52)))

        nameInput.doAfterTextChanged { editable ->
            inputName = editable?.toString().orEmpty()
            saveButton.isEnabled = inputName.trim().isNotEmpty() && !saving
            preview.isVisible = inputName.trim().isNotEmpty()
            (preview.getChildAt(0) as LinearLayout).getChildAt(1).let { it as TextView }.text = inputName
        }
        nameInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE && saveButton.isEnabled) {
                saveButton.performClick()
                true
            } else {
                false
            }
        }
        nameInput.requestFocus()
        nameInput.post {
            requireContext().getSystemService(InputMethodManager::class.java)
                ?.showSoftInput(nameInput, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    private fun saveEditor() {
        val cleanName = inputName.trim()
        if (cleanName.isEmpty() || saving) return
        saving = true
        if (mode == Mode.CREATE) {
            store.create(cleanName, inputColor)
            notifyChanged()
            showListMode()
            return
        }
        val id = editTargetId ?: run { saving = false; return }
        val change = store.update(id, cleanName, inputColor) ?: run { saving = false; return }
        lifecycleScope.launch {
            if (change.oldName != change.updated.name) {
                repository.renameList(change.oldName, change.updated.name)
                taskCounts = taskCounts.toMutableMap().apply {
                    val count = remove(change.oldName) ?: 0
                    put(change.updated.name, count)
                }
            }
            notifyChanged()
            showListMode()
        }
    }

    private fun showDeleteConfirmation(list: TaskListDefinition) {
        deleteDialog?.dismiss()
        val count = taskCounts[list.name] ?: 0
        val card = MaterialCardView(requireContext()).apply {
            radius = dp(20).toFloat()
            cardElevation = dp(8).toFloat()
            setCardBackgroundColor(context.getColor(R.color.forgetty_surface))
        }
        val body = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(TextView(context).apply {
                text = getString(R.string.delete_list_title, list.name)
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(context.getColor(R.color.forgetty_text_primary))
            }, matchWidth().apply { bottomMargin = dp(8) })
            addView(TextView(context).apply {
                setText(R.string.delete_list_move_tasks)
                textSize = 14f
                setLineSpacing(0f, 1.25f)
                setTextColor(context.getColor(R.color.forgetty_text_secondary))
            }, matchWidth().apply { bottomMargin = if (count > 0) dp(6) else dp(20) })
            if (count > 0) addView(TextView(context).apply {
                text = resources.getQuantityString(R.plurals.delete_list_unassigned_tasks, count, count)
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(context.getColor(R.color.forgetty_warning))
            }, matchWidth().apply { bottomMargin = dp(20) })
        }
        val buttons = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val cancel = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            setText(R.string.cancel)
            isAllCaps = false
            textSize = 14f
            letterSpacing = 0f
            cornerRadius = dp(12)
            minimumHeight = dp(48)
            insetTop = 0
            insetBottom = 0
        }
        val delete = MaterialButton(requireContext()).apply {
            setText(R.string.delete)
            isAllCaps = false
            textSize = 14f
            letterSpacing = 0f
            cornerRadius = dp(12)
            minimumHeight = dp(48)
            insetTop = 0
            insetBottom = 0
            backgroundTintList = ColorStateList.valueOf(context.getColor(R.color.forgetty_error))
        }
        buttons.addView(cancel, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(5) })
        buttons.addView(delete, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(5) })
        body.addView(buttons, matchWidth(dp(48)))
        card.addView(body)

        deleteDialog = Dialog(requireContext()).apply {
            setContentView(card)
            setCanceledOnTouchOutside(true)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                setDimAmount(0.5f)
            }
            cancel.setOnClickListener { dismiss() }
            delete.setOnClickListener {
                delete.isEnabled = false
                lifecycleScope.launch {
                    repository.moveTasksFromList(list.name, NO_TASK_LIST)
                    store.delete(list.id)
                    val remaining = store.getLists()
                    if (preferences.defaultList == list.name) {
                        preferences.defaultList = remaining.firstOrNull()?.name ?: NO_TASK_LIST
                    }
                    if (preferences.selectedList == list.name) preferences.selectedList = ForgettyPreferences.ALL_TASKS
                    taskCounts = repository.getTasks().groupingBy(Task::listName).eachCount()
                    notifyChanged()
                    dismiss()
                    deleteDialog = null
                    if (isAdded) renderList()
                }
            }
            setOnDismissListener { if (deleteDialog === this) deleteDialog = null }
            show()
            window?.setLayout(
                resources.displayMetrics.widthPixels - dp(48),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun colorDot(color: String) = View(requireContext()).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(runCatching { Color.parseColor(color) }.getOrDefault(Color.GRAY))
        }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun iconButton(icon: Int, label: String) = ImageButton(requireContext()).apply {
        setImageResource(icon)
        contentDescription = label
        background = selectableBorderless()
        setPadding(dp(14), dp(14), dp(14), dp(14))
        scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
        ImageViewCompat.setImageTintList(this, ColorStateList.valueOf(context.getColor(R.color.forgetty_text_secondary)))
        layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
    }

    private fun divider() = View(requireContext()).apply {
        setBackgroundColor(context.getColor(R.color.forgetty_outline_variant))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1))
    }

    private fun selectableBorderless(): android.graphics.drawable.Drawable? {
        val value = TypedValue()
        requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)
        return ContextCompat.getDrawable(requireContext(), value.resourceId)
    }

    private fun colorColumnCount(): Int = if (resources.displayMetrics.widthPixels / resources.displayMetrics.density >= 380f) 7 else 6

    private fun hideKeyboard() {
        dialog?.currentFocus?.let { focused ->
            requireContext().getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(focused.windowToken, 0)
        }
    }

    private fun matchWidth(height: Int = ViewGroup.LayoutParams.WRAP_CONTENT) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun notifyChanged() {
        parentFragmentManager.setFragmentResult(RESULT_KEY, bundleOf("changed" to true))
    }

    companion object {
        const val RESULT_KEY = "forgetty_lists_changed"
        private const val TAG = "ListManagerBottomSheet"
        private const val MAX_LIST_NAME_LENGTH = 40
        private const val STATE_MODE = "list_manager_mode"
        private const val STATE_EDIT_TARGET = "list_manager_edit_target"
        private const val STATE_INPUT_NAME = "list_manager_input_name"
        private const val STATE_INPUT_COLOR = "list_manager_input_color"

        fun show(manager: FragmentManager) = ListManagerBottomSheet().show(manager, TAG)
    }
}

private class ListColorView(context: android.content.Context) : View(context) {
    var colorValue: Int = Color.GRAY
        set(value) { field = value; invalidate() }
    var isChecked: Boolean = false
        set(value) { field = value; isSelected = value; invalidate() }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val checkPath = Path()

    init {
        isClickable = true
        isFocusable = true
        foreground = selectableForeground()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = resources.displayMetrics.density * 18f
        if (isChecked) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = resources.displayMetrics.density * 3f
            paint.color = colorValue
            canvas.drawCircle(cx, cy, radius + resources.displayMetrics.density * 3f, paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(cx, cy, radius - resources.displayMetrics.density * 2f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = resources.displayMetrics.density * 2.5f
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = Color.WHITE
            checkPath.reset()
            checkPath.moveTo(cx - dp(7f), cy)
            checkPath.lineTo(cx - dp(2f), cy + dp(5f))
            checkPath.lineTo(cx + dp(8f), cy - dp(6f))
            canvas.drawPath(checkPath, paint)
        } else {
            paint.style = Paint.Style.FILL
            paint.color = colorValue
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }

    private fun selectableForeground(): android.graphics.drawable.Drawable? {
        val value = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)
        return ContextCompat.getDrawable(context, value.resourceId)
    }

    private fun dp(value: Float) = value * resources.displayMetrics.density
}
