package com.focusapp.personal

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/** App chooser with app icons, a search box and a live "selected" counter. */
object AppPicker {

    data class AppItem(val pkg: String, val label: String, val info: ResolveInfo)

    fun homePackage(ctx: Context): String? =
        ctx.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName

    /** Launchable apps, without FocusApp itself and without the phone's home screen. */
    fun installed(ctx: Context): List<AppItem> {
        val pm = ctx.packageManager
        val home = homePackage(ctx)
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .map { AppItem(it.activityInfo.packageName, it.loadLabel(pm).toString(), it) }
            .filter { it.pkg != ctx.packageName && it.pkg != home }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }

    fun show(ctx: Context, preselected: Set<String>, onDone: (Set<String>) -> Unit) {
        val all = installed(ctx)
        val chosen = preselected.toMutableSet()
        val view = LayoutInflater.from(ctx).inflate(R.layout.dialog_app_picker, null)
        val search = view.findViewById<EditText>(R.id.searchBox)
        val count = view.findViewById<TextView>(R.id.selectedCount)
        val recycler = view.findViewById<RecyclerView>(R.id.appRecycler)

        fun updateCount() {
            count.text = "${chosen.size} selected"
        }

        val adapter = Adapter(ctx, all, chosen) { updateCount() }
        recycler.layoutManager = LinearLayoutManager(ctx)
        recycler.adapter = adapter
        search.doAfterTextChanged { adapter.filter(it?.toString() ?: "") }
        updateCount()

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("Apps to block")
            .setView(view)
            .setPositiveButton("Done") { _, _ -> onDone(chosen.toSet()) }
            .setNegativeButton("Cancel", null)
            .create()
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.show()
    }

    private class Adapter(
        private val ctx: Context,
        private val all: List<AppItem>,
        private val chosen: MutableSet<String>,
        private val onChange: () -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {

        private var shown: List<AppItem> = all
        private val icons = HashMap<String, Drawable>()

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val icon: ImageView = v.findViewById(R.id.appIcon)
            val name: TextView = v.findViewById(R.id.appName)
            val check: CheckBox = v.findViewById(R.id.appCheck)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.row_app, parent, false))

        override fun getItemCount(): Int = shown.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = shown[position]
            holder.name.text = item.label
            holder.icon.setImageDrawable(icons.getOrPut(item.pkg) { item.info.loadIcon(ctx.packageManager) })
            holder.check.isChecked = item.pkg in chosen
            holder.itemView.setOnClickListener {
                if (!chosen.add(item.pkg)) chosen.remove(item.pkg)
                holder.check.isChecked = item.pkg in chosen
                onChange()
            }
        }

        fun filter(query: String) {
            val q = query.trim().lowercase()
            shown = if (q.isEmpty()) all else all.filter {
                it.label.lowercase().contains(q) || it.pkg.lowercase().contains(q)
            }
            notifyDataSetChanged()
        }
    }
}
