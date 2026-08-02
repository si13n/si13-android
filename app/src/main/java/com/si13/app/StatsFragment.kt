package com.si13.app

import android.graphics.Color
import android.graphics.Typeface
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class StatsFragment : Fragment(R.layout.fragment_stats) {
    private lateinit var content: LinearLayout
    private lateinit var repository: TaskRepository

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        content = view.findViewById(R.id.stats_content)
        repository = TaskRepository.create(requireContext().applicationContext)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeTasks().collect { render(it) }
            }
        }
    }

    private fun render(tasks: List<Task>) {
        content.removeAllViews()
        title("Stats", 28)
        val today = LocalDate.now()
        val completed = tasks.count { it.completed }
        val active = tasks.size - completed
        val completedToday = tasks.count { it.completed && updatedDate(it) == today }
        val rate = if (tasks.isEmpty()) 0 else completed * 100 / tasks.size
        val overdue = tasks.filter { !it.completed && it.dueDate?.let { d -> runCatching { LocalDate.parse(d).isBefore(today) }.getOrDefault(false) } == true }

        val metrics = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        metricColumn(metrics, "Completed today", completedToday.toString())
        metricColumn(metrics, "Active tasks", active.toString())
        content.addView(metrics)
        val metrics2 = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL }
        metricColumn(metrics2, "Completion rate", "$rate%")
        metricColumn(metrics2, "Total tasks", tasks.size.toString())
        content.addView(metrics2)

        card("Overdue", tinted = overdue.isNotEmpty()).apply {
            val row = getChildAt(0) as LinearLayout

            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL

            row.getChildAt(0).layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )

            val value = TextView(context).apply {
                text = overdue.size.toString()
                textSize = 24f
                setTextColor(
                    if (overdue.isEmpty()) {
                        color(R.color.forgetty_text_secondary)
                    } else {
                        color(R.color.home_priority_high)
                    }
                )
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }

            row.addView(value)
        }.also(content::addView)

        activityCard(tasks, today)
        breakdownCard("By list", tasks.groupingBy { it.listName }.eachCount()) { name, count ->
            val done = tasks.count { it.listName == name && it.completed }
            "$done/$count"
        }
        tagsCard(tasks.flatMap { it.tags }.groupingBy { it }.eachCount())
        breakdownCard("Priority", mapOf("High" to tasks.count { it.priority == TaskPriority.HIGH && !it.completed }, "Normal" to tasks.count { it.priority != TaskPriority.HIGH && !it.completed }, "Completed" to completed)) { _, count -> count.toString() }
    }

    private fun title(text: String, size: Int) = content.addView(TextView(requireContext()).apply { this.text = text; textSize = size.toFloat(); setTypeface(null, Typeface.BOLD); setTextColor(color(R.color.forgetty_text_primary)); setPadding(0, 0, 0, 16) })
    private fun metricColumn(parent: LinearLayout, label: String, value: String) { parent.addView(card(null).apply { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }; addText(value, 26, color(R.color.home_accent), true); addText(label, 12) }) }
    private fun card(heading: String?, tinted: Boolean = false): MaterialCardView = MaterialCardView(requireContext()).apply { radius = dp(16).toFloat(); cardElevation = 0f; setCardBackgroundColor(if (tinted) Color.rgb(255, 241, 243) else color(R.color.profile_surface)); strokeWidth = if (tinted) dp(1) else 0; if (tinted) strokeColor = Color.rgb(245, 190, 196); layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, dp(7), 0, dp(7)) }; val box = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(14), dp(16), dp(14)); if (heading != null) addTextToBox(this, heading, 18, if (tinted) Color.rgb(190, 28, 34) else color(R.color.forgetty_text_primary), true) }; addView(box) }
    private fun addTextToBox(box: LinearLayout, text: String, size: Int, textColor: Int, bold: Boolean = false) { box.addView(TextView(box.context).apply { this.text = text; textSize = size.toFloat(); setTextColor(textColor); if (bold) setTypeface(null, Typeface.BOLD); setPadding(0, dp(3), 0, dp(3)) }) }
    private fun MaterialCardView.addText(text: String, size: Int, textColor: Int = color(R.color.forgetty_text_secondary), bold: Boolean = false) { (getChildAt(0) as LinearLayout).addView(TextView(context).apply { this.text = text; textSize = size.toFloat(); setTextColor(textColor); if (bold) setTypeface(null, Typeface.BOLD); setPadding(0, dp(3), 0, dp(3)) }) }
    private fun activityCard(tasks: List<Task>, today: LocalDate) { val card = card("Activity — last 7 days"); val row = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM; minimumHeight = dp(70) }; val counts = (6 downTo 0).map { offset -> tasks.count { it.completed && updatedDate(it) == today.minusDays(offset.toLong()) } }; val maxCount = counts.maxOrNull()?.coerceAtLeast(1) ?: 1; counts.forEachIndexed { index, count -> val offset = 6 - index; val column = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; layoutParams = LinearLayout.LayoutParams(0, dp(66), 1f).apply { marginEnd = dp(5) } }; column.addView(View(requireContext()).apply { setBackgroundColor(color(if (offset == 0) R.color.home_accent else R.color.forgetty_secondary_container)); layoutParams = LinearLayout.LayoutParams(dp(8), dp(if (count == 0) 5 else 8 + 34 * count / maxCount)) }); column.addView(TextView(requireContext()).apply { text = today.minusDays(offset.toLong()).dayOfWeek.name.take(1); textSize = 10f; gravity = Gravity.CENTER; setTextColor(color(if (offset == 0) R.color.home_accent else R.color.forgetty_text_secondary)); layoutParams = LinearLayout.LayoutParams(-1, dp(18)) }); row.addView(column) }; (card.getChildAt(0) as LinearLayout).addView(row); content.addView(card) }
    private fun breakdownCard(heading: String, values: Map<String, Int>, suffix: (String, Int) -> String) { val card = card(heading); val box = card.getChildAt(0) as LinearLayout; val total = values.values.sum().coerceAtLeast(1); values.entries.sortedByDescending { it.value }.forEach { (name, count) -> val line = LinearLayout(requireContext()).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }; addTextToBox(line, name, 14, color(R.color.forgetty_text_primary)); addTextToBox(line, suffix(name, count), 13, color(R.color.forgetty_text_secondary)); (line.getChildAt(0) as TextView).layoutParams = LinearLayout.LayoutParams(0, -2, 1f); box.addView(line); box.addView(LinearProgressIndicator(requireContext()).apply { this.max = total; setProgressCompat(count, false); trackThickness = dp(6); setIndicatorColor(color(if (heading == "Priority" && name == "High") R.color.home_priority_high else R.color.home_accent)); layoutParams = LinearLayout.LayoutParams(-1, dp(6)).apply { bottomMargin = dp(8) } }) }; content.addView(card) }
    private fun tagsCard(values: Map<String, Int>) { val card = card("Tags"); val box = card.getChildAt(0) as LinearLayout; val chips = ChipGroup(requireContext()).apply { isSingleLine = false; isSelectionRequired = false; chipSpacingHorizontal = dp(8); chipSpacingVertical = dp(6) }; values.entries.sortedByDescending { it.value }.forEach { (name, count) -> chips.addView(Chip(requireContext()).apply { text = "$name  $count"; textSize = 14f; isClickable = false; isCheckable = false; chipMinHeight = dp(40).toFloat(); setTextColor(color(R.color.forgetty_text_primary)); chipBackgroundColor = ColorStateList.valueOf(color(R.color.forgetty_primary_container)); layoutParams = ChipGroup.LayoutParams(-2, -2) }) }; if (values.isEmpty()) addTextToBox(box, "No tags used yet.", 13, color(R.color.forgetty_text_secondary)); else box.addView(chips); content.addView(card) }
    private fun updatedDate(task: Task): LocalDate? = task.updatedAt.takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
    private fun taskDate(task: Task): LocalDate? = (task.completedAt ?: task.updatedAt).takeIf { it > 0 }?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
    private fun daysLate(task: Task, today: LocalDate): Long = runCatching { today.toEpochDay() - LocalDate.parse(task.dueDate).toEpochDay() }.getOrDefault(0)
    private fun color(id: Int) = requireContext().getColor(id)
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
