package com.galaxymedia.tvsetup

import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The renewal reminder: the person picks a date (or a preset length) on their own stick. */
object ReminderPicker {

    fun parse(s: String): Date? =
        if (s.isBlank()) null else try { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(s) } catch (e: Exception) { null }

    fun nice(d: Date): String = SimpleDateFormat("MMM d", Locale.getDefault()).format(d)

    /** Whole days left until the end of that date (negative once it has passed). */
    fun daysLeft(d: Date): Int = Math.floorDiv(d.time + 86_399_000L - System.currentTimeMillis(), 86_400_000L).toInt()

    /**
     * Presets are counted from today. After paying (afterPaying = true) they are counted from the current
     * reminder date instead, if that date is still ahead, so paying a few days early does not lose time.
     */
    fun show(activity: AppCompatActivity, prefs: Prefs, afterPaying: Boolean, onDone: () -> Unit) {
        val existing = parse(prefs.reminderDate)
        val labels = mutableListOf("In 1 month", "In 3 months (quarter)", "In 6 months (semi-annual)", "In 1 year", "Pick a date…")
        if (existing != null) labels += "Clear the reminder"
        AlertDialog.Builder(activity)
            .setTitle(if (afterPaying) "Paid? Remind me again…" else "Set a reminder")
            .setItems(labels.toTypedArray()) { _, which ->
                when (which) {
                    0 -> setIn(activity, prefs, 1, afterPaying, existing, onDone)
                    1 -> setIn(activity, prefs, 3, afterPaying, existing, onDone)
                    2 -> setIn(activity, prefs, 6, afterPaying, existing, onDone)
                    3 -> setIn(activity, prefs, 12, afterPaying, existing, onDone)
                    4 -> pickDate(activity, prefs, onDone)
                    else -> {
                        prefs.reminderDate = ""
                        Toast.makeText(activity, "Reminder cleared.", Toast.LENGTH_SHORT).show()
                        onDone()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setIn(activity: AppCompatActivity, prefs: Prefs, months: Int, afterPaying: Boolean, existing: Date?, onDone: () -> Unit) {
        val cal = Calendar.getInstance()
        if (afterPaying && existing != null && daysLeft(existing) >= 0) cal.time = existing
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0)
        cal.add(Calendar.MONTH, months)
        save(activity, prefs, cal.time, onDone)
    }

    /**
     * Two plain lists, month then day. A spinner-style date picker is hard to finish with a TV remote
     * (the OK button cannot be reached), but a list can always be moved through and pressed.
     */
    private fun pickDate(activity: AppCompatActivity, prefs: Prefs, onDone: () -> Unit) {
        val today = Calendar.getInstance()
        val nameFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val firsts = ArrayList<Calendar>()
        val monthLabels = ArrayList<String>()
        val c = Calendar.getInstance()
        c.set(Calendar.DAY_OF_MONTH, 1)
        for (i in 0 until 24) {
            firsts += (c.clone() as Calendar)
            monthLabels += nameFmt.format(c.time)
            c.add(Calendar.MONTH, 1)
        }
        AlertDialog.Builder(activity)
            .setTitle("Pick the month")
            .setItems(monthLabels.toTypedArray()) { _, mi ->
                val first = firsts[mi]
                val startDay = if (mi == 0) today.get(Calendar.DAY_OF_MONTH) else 1
                val lastDay = first.getActualMaximum(Calendar.DAY_OF_MONTH)
                val days = (startDay..lastDay).map { it.toString() }
                AlertDialog.Builder(activity)
                    .setTitle("Pick the day · " + monthLabels[mi])
                    .setItems(days.toTypedArray()) { _, di ->
                        val d = first.clone() as Calendar
                        d.set(Calendar.DAY_OF_MONTH, startDay + di)
                        d.set(Calendar.HOUR_OF_DAY, 0); d.set(Calendar.MINUTE, 0); d.set(Calendar.SECOND, 0)
                        save(activity, prefs, d.time, onDone)
                    }
                    .setNegativeButton("Back") { _, _ -> pickDate(activity, prefs, onDone) }
                    .show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun save(activity: AppCompatActivity, prefs: Prefs, date: Date, onDone: () -> Unit) {
        prefs.reminderDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
        Toast.makeText(activity, "Reminder set for ${nice(date)}.", Toast.LENGTH_LONG).show()
        onDone()
    }
}
