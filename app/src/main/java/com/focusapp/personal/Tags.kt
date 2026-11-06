package com.focusapp.personal

import android.content.Context
import android.text.InputType
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog

/** Session / slot labels: a built-in list, plus custom labels you type once and reuse. */
object Tags {

    private val defaults = listOf(
        "Study", "Work", "Deep Work", "Reading", "Coding", "Assignment", "Revision",
        "Exam Prep", "Project", "Writing", "Meeting", "Business", "Exercise", "Meditation"
    )
    const val CUSTOM = "Custom..."
    private const val MAX_LEN = 24

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("custom_tags", Context.MODE_PRIVATE)

    private fun saved(ctx: Context): List<String> =
        (prefs(ctx).getStringSet("tags", emptySet()) ?: emptySet()).sortedBy { it.lowercase() }

    private fun addCustom(ctx: Context, name: String) {
        val set = HashSet(prefs(ctx).getStringSet("tags", emptySet()) ?: emptySet())
        set.add(name)
        prefs(ctx).edit().putStringSet("tags", set).apply()
    }

    private fun items(ctx: Context): List<String> = defaults + saved(ctx) + CUSTOM

    /** The label currently chosen in [spinner]; never returns the "Custom..." placeholder. */
    fun selected(spinner: Spinner): String {
        val s = spinner.selectedItem?.toString() ?: return "Focus"
        return if (s == CUSTOM) "Focus" else s
    }

    /** Fills [spinner] with all labels. Choosing "Custom..." asks you to type your own. */
    fun attach(ctx: Context, spinner: Spinner, initial: String? = null) {
        val list = ArrayList(items(ctx))
        val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_dropdown_item, list)
        spinner.adapter = adapter
        var last = 0
        if (initial != null && initial in list) {
            last = list.indexOf(initial)
            spinner.setSelection(last)
        }
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (list[position] != CUSTOM) {
                    last = position
                    return
                }
                askName(ctx,
                    onName = { name ->
                        addCustom(ctx, name)
                        list.clear()
                        list.addAll(items(ctx))
                        adapter.notifyDataSetChanged()
                        spinner.setSelection(list.indexOf(name).coerceAtLeast(0))
                    },
                    onCancel = { spinner.setSelection(last) })
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun askName(ctx: Context, onName: (String) -> Unit, onCancel: () -> Unit) {
        val input = EditText(ctx).apply {
            hint = "e.g. Thesis, Gym, Client work"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            maxLines = 1
            setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 16), Ui.dp(ctx, 20), Ui.dp(ctx, 16))
        }
        var done = false
        AlertDialog.Builder(ctx)
            .setTitle("Your own label")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val name = input.text.toString().trim().take(MAX_LEN)
                if (name.isEmpty() || name.equals(CUSTOM, true)) {
                    onCancel()
                } else {
                    done = true
                    onName(name)
                }
            }
            .setNegativeButton("Cancel") { _, _ -> onCancel() }
            .setOnDismissListener { if (!done) onCancel() }
            .show()
    }
}
