package com.focusapp.personal

import android.app.TimePickerDialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.materialswitch.MaterialSwitch

/** Tab 2: plan the whole week, then lock it. A locked week runs every slot in strict mode. */
class ScheduleFragment : Fragment(R.layout.fragment_schedule) {

    private var selectedDay = 0 // ISO day, Monday = 1
    private val chips = ArrayList<TextView>()
    private lateinit var ss: ScheduleStore

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()
        ss = ScheduleStore(ctx)
        if (selectedDay == 0) selectedDay = Dates.isoDay(ss.now())

        buildChips(view)

        view.findViewById<View>(R.id.addSlot).setOnClickListener { showAddDialog() }
        view.findViewById<View>(R.id.lockButton).setOnClickListener { onLockClicked() }
        view.findViewById<View>(R.id.emergencyBtn).setOnClickListener { onEmergency() }
        view.findViewById<View>(R.id.pickApps).setOnClickListener { pickApps() }

        val reels = view.findViewById<MaterialSwitch>(R.id.schedReels)
        val shorts = view.findViewById<MaterialSwitch>(R.id.schedShorts)
        reels.isChecked = ss.blockReels
        shorts.isChecked = ss.blockShorts
        reels.setOnCheckedChangeListener { _, on -> if (!ss.frozen()) ss.blockReels = on }
        shorts.setOnCheckedChangeListener { _, on -> if (!ss.frozen()) ss.blockShorts = on }
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    // ------------------------------------------------------------ rendering

    private fun buildChips(v: View) {
        val ctx = requireContext()
        val row = v.findViewById<LinearLayout>(R.id.dayRow)
        row.removeAllViews()
        chips.clear()
        for (iso in 1..7) {
            val chip = TextView(ctx).apply {
                gravity = Gravity.CENTER
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, Ui.dp(ctx, 58), 1f).also {
                    it.setMargins(Ui.dp(ctx, 3), 0, Ui.dp(ctx, 3), 0)
                }
                setOnClickListener {
                    selectedDay = iso
                    renderChips()
                    renderSlots()
                }
            }
            chips.add(chip)
            row.addView(chip)
        }
    }

    private fun render() {
        val v = view ?: return
        val ctx = requireContext()
        val frozen = ss.frozen()
        val now = ss.now()

        val monday = Dates.mondayOf(now)
        v.findViewById<TextView>(R.id.weekRange).text =
            "${Dates.pretty(monday)} to ${Dates.pretty(Dates.addDays(monday, 6))}"

        val status = v.findViewById<TextView>(R.id.lockStatus)
        val lockBtn = v.findViewById<MaterialButton>(R.id.lockButton)
        if (frozen) {
            val starts = if (ss.lockStart > now) " Starts ${Dates.pretty(ss.lockStart)}." else ""
            status.text = "Locked through ${Dates.pretty(Dates.addDays(ss.lockEnd, -1))}.$starts " +
                "Every slot runs in strict mode and the plan cannot be changed."
            lockBtn.text = "Locked"
            lockBtn.isEnabled = false
        } else {
            status.text = if (ss.lockEnd > 0L) {
                "Your last lock has ended. Check permissions in Setup, review the plan, then lock again."
            } else {
                "Plan your weekly slots, then lock the days you want. Locking turns on strict mode for every slot."
            }
            lockBtn.text = "Lock schedule"
            lockBtn.isEnabled = true
        }

        v.findViewById<View>(R.id.emergencyBtn).visibility =
            if (frozen && ScheduleStore.ALLOW_EMERGENCY_UNLOCK) View.VISIBLE else View.GONE

        val add = v.findViewById<MaterialButton>(R.id.addSlot)
        add.isEnabled = !frozen
        add.text = if (frozen) "Plan is locked" else "Add time slot"

        val pick = v.findViewById<MaterialButton>(R.id.pickApps)
        pick.isEnabled = !frozen
        pick.text = "Apps to block (${ss.blocked.size})"
        v.findViewById<MaterialSwitch>(R.id.schedReels).isEnabled = !frozen
        v.findViewById<MaterialSwitch>(R.id.schedShorts).isEnabled = !frozen

        renderChips()
        renderSlots()
    }

    private fun renderChips() {
        val ctx = requireContext()
        val all = ScheduleEngine.slots(ctx)
        for (i in chips.indices) {
            val iso = i + 1
            val count = all.count { it.dayOfWeek == iso }
            val selected = iso == selectedDay
            chips[i].text = Dates.dayName(iso) + "\n" + (if (count > 0) count.toString() else "-")
            chips[i].setBackgroundResource(if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip)
            chips[i].setTextColor(ctx.getColor(if (selected) R.color.white else R.color.text))
        }
    }

    private fun renderSlots() {
        val v = view ?: return
        val ctx = requireContext()
        val list = v.findViewById<LinearLayout>(R.id.slotList)
        list.removeAllViews()
        val frozen = ss.frozen()
        val day = ScheduleEngine.slots(ctx).filter { it.dayOfWeek == selectedDay }.sortedBy { it.startMin }

        for (slot in day) {
            val row = layoutInflater.inflate(R.layout.row_slot, list, false)
            val mins = slot.endMin - slot.startMin
            row.findViewById<TextView>(R.id.slotTime).text =
                "${Dates.hhmm(slot.startMin)} to ${Dates.hhmm(slot.endMin)}"
            row.findViewById<TextView>(R.id.slotTag).text =
                "${slot.tag}, ${if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"}"
            val del = row.findViewById<View>(R.id.slotDelete)
            if (frozen) {
                del.visibility = View.GONE
            } else {
                del.setOnClickListener {
                    AppDb.get(ctx).dao().deleteSlot(slot.id)
                    renderChips()
                    renderSlots()
                }
            }
            list.addView(row)
        }
        v.findViewById<View>(R.id.emptyText).visibility = if (day.isEmpty()) View.VISIBLE else View.GONE
    }

    // ------------------------------------------------------------ add a slot

    private fun showAddDialog() {
        val ctx = requireContext()
        if (ss.frozen()) return
        val dv = layoutInflater.inflate(R.layout.dialog_add_slot, null)
        var startMin = 9 * 60
        var endMin = 10 * 60

        val startBtn = dv.findViewById<MaterialButton>(R.id.startBtn)
        val endBtn = dv.findViewById<MaterialButton>(R.id.endBtn)
        startBtn.text = Dates.hhmm(startMin)
        endBtn.text = Dates.hhmm(endMin)
        startBtn.setOnClickListener {
            TimePickerDialog(ctx, { _, h, m ->
                startMin = h * 60 + m
                startBtn.text = Dates.hhmm(startMin)
            }, startMin / 60, startMin % 60, true).show()
        }
        endBtn.setOnClickListener {
            TimePickerDialog(ctx, { _, h, m ->
                endMin = h * 60 + m
                endBtn.text = Dates.hhmm(endMin)
            }, endMin / 60, endMin % 60, true).show()
        }

        val spinner = dv.findViewById<Spinner>(R.id.tagSpin)
        Tags.attach(ctx, spinner)

        val chosen = BooleanArray(8)
        chosen[selectedDay] = true
        val daysRow = dv.findViewById<LinearLayout>(R.id.daysRow)
        val dayViews = ArrayList<TextView>()
        fun paint(tv: TextView, iso: Int) {
            tv.setBackgroundResource(if (chosen[iso]) R.drawable.bg_chip_selected else R.drawable.bg_chip)
            tv.setTextColor(ctx.getColor(if (chosen[iso]) R.color.white else R.color.text))
        }
        for (iso in 1..7) {
            val tv = TextView(ctx).apply {
                text = Dates.dayName(iso).take(2)
                gravity = Gravity.CENTER
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, Ui.dp(ctx, 40), 1f).also {
                    it.setMargins(Ui.dp(ctx, 2), 0, Ui.dp(ctx, 2), 0)
                }
            }
            paint(tv, iso)
            tv.setOnClickListener {
                chosen[iso] = !chosen[iso]
                paint(tv, iso)
            }
            dayViews.add(tv)
            daysRow.addView(tv)
        }

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("New time slot")
            .setView(dv)
            .setPositiveButton("Add", null)
            .setNegativeButton("Cancel", null)
            .create()
        dialog.show()
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
            if (endMin <= startMin) {
                Toast.makeText(ctx, "End must be after start (a slot cannot cross midnight)", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (endMin - startMin < 5) {
                Toast.makeText(ctx, "A slot must be at least 5 minutes", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val days = (1..7).filter { chosen[it] }
            if (days.isEmpty()) {
                Toast.makeText(ctx, "Pick at least one day", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val dao = AppDb.get(ctx).dao()
            val existing = dao.slots()
            for (d in days) {
                val clash = existing.firstOrNull {
                    it.dayOfWeek == d && startMin < it.endMin && endMin > it.startMin
                }
                if (clash != null) {
                    Toast.makeText(
                        ctx,
                        "${Dates.dayName(d)}: overlaps ${Dates.hhmm(clash.startMin)} to ${Dates.hhmm(clash.endMin)}",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
            }
            val tag = Tags.selected(spinner)
            for (d in days) dao.insertSlot(ScheduleSlot(dayOfWeek = d, startMin = startMin, endMin = endMin, tag = tag))
            dialog.dismiss()
            renderChips()
            renderSlots()
        }
    }

    // ------------------------------------------------------------ apps to block

    private fun pickApps() {
        val ctx = requireContext()
        if (ss.frozen()) return
        AppPicker.show(ctx, ss.blocked) {
            ss.blocked = it
            render()
        }
    }

    // ------------------------------------------------------------ lock the week

    private fun onLockClicked() {
        val ctx = requireContext()
        if (ss.frozen()) return
        val slots = ScheduleEngine.slots(ctx)
        if (slots.isEmpty()) {
            Toast.makeText(ctx, "Add at least one time slot first", Toast.LENGTH_LONG).show()
            return
        }
        if (ss.blocked.isEmpty() && !ss.blockReels && !ss.blockShorts) {
            Toast.makeText(ctx, "Pick apps to block, or turn on Reels/Shorts blocking", Toast.LENGTH_LONG).show()
            return
        }
        val missing = mutableListOf<String>()
        if (!Perms.usage(ctx)) missing.add("Usage access")
        if (!Perms.overlay(ctx)) missing.add("Draw over other apps")
        if (!Perms.accessibility(ctx)) missing.add("Accessibility service")
        if (!Perms.deviceAdmin(ctx)) missing.add("Device admin")
        if (missing.isNotEmpty()) {
            AlertDialog.Builder(ctx)
                .setTitle("Grant permissions first")
                .setMessage("Open the Setup tab and grant:\n\n" + missing.joinToString("\n") { "- $it" })
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val today = Dates.startOfDay(ss.now())
        val sunday = Dates.addDays(Dates.mondayOf(today), 6)
        val nextMonday = Dates.addDays(Dates.mondayOf(today), 7)
        AlertDialog.Builder(ctx)
            .setTitle("How long should the lock run?")
            .setItems(
                arrayOf(
                    "Pick dates on the calendar",
                    "Rest of this week (until Sunday)",
                    "Next week (Monday to Sunday)"
                )
            ) { _, which ->
                when (which) {
                    0 -> showRangePicker()
                    1 -> confirmLock(today, sunday)
                    else -> confirmLock(nextMonday, Dates.addDays(nextMonday, 6))
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRangePicker() {
        val today = Dates.startOfDay(ss.now())
        val todayUtc = Dates.toPickerUtc(today)
        val constraints = CalendarConstraints.Builder()
            .setStart(todayUtc)
            .setValidator(DateValidatorPointForward.from(todayUtc))
            .build()
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select the days to lock (max ${ScheduleStore.MAX_LOCK_DAYS})")
            .setCalendarConstraints(constraints)
            .setSelection(androidx.core.util.Pair(todayUtc, Dates.toPickerUtc(Dates.addDays(today, 6))))
            .build()
        picker.addOnPositiveButtonClickListener { sel ->
            val start = Dates.fromPickerUtc(sel.first)
            val end = Dates.fromPickerUtc(sel.second)
            val days = Dates.daysBetween(start, end) + 1
            if (days > ScheduleStore.MAX_LOCK_DAYS) {
                Toast.makeText(
                    requireContext(),
                    "Choose at most ${ScheduleStore.MAX_LOCK_DAYS} days",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                confirmLock(start, end)
            }
        }
        picker.show(parentFragmentManager, "lockRange")
    }

    /** [startDay] and [endDay] are local midnights, both included in the lock. */
    private fun confirmLock(startDay: Long, endDay: Long) {
        val ctx = requireContext()
        val days = Dates.daysBetween(startDay, endDay) + 1
        val extra = if (ScheduleStore.ALLOW_EMERGENCY_UNLOCK) {
            "\n\nOnly escape: Emergency unlock, which needs a 10 minute wait and works between slots."
        } else {
            "\n\nThere is no way out until then."
        }
        AlertDialog.Builder(ctx)
            .setTitle("Lock $days day${if (days == 1) "" else "s"}?")
            .setMessage(
                "From ${Dates.pretty(startDay)} to ${Dates.pretty(endDay)}, every slot runs in Strict Mode. " +
                    "You will not be able to edit the plan, end a slot early, or open FocusApp's pages " +
                    "in Settings or the installer until the lock ends." + extra
            )
            .setNegativeButton("Back", null)
            .setPositiveButton("Lock it") { _, _ ->
                ss.lockRange(startDay, endDay)
                ScheduleEngine.sync(ctx)
                render()
                if (SessionStore(ctx).active) {
                    startActivity(Intent(ctx, ActiveFocusActivity::class.java))
                } else {
                    Toast.makeText(ctx, "Locked. Your slots will start by themselves.", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }

    // ------------------------------------------------------------ emergency unlock

    private fun onEmergency() {
        val ctx = requireContext()
        if (!ss.frozen()) {
            render()
            return
        }
        if (ss.unlockRequestAt() == 0L) {
            AlertDialog.Builder(ctx)
                .setTitle("Emergency unlock")
                .setMessage(
                    "This breaks your own promise. First you must wait 10 minutes, then confirm by typing UNLOCK. " +
                        "Start the wait?"
                )
                .setNegativeButton("Keep the lock", null)
                .setPositiveButton("Start 10 minute wait") { _, _ ->
                    ss.requestUnlock()
                    Toast.makeText(ctx, "Come back in 10 minutes.", Toast.LENGTH_LONG).show()
                }
                .show()
            return
        }
        val left = ss.unlockWaitLeftMs()
        if (left > 0) {
            val mins = (left + 59_999L) / 60_000L
            Toast.makeText(ctx, "Wait $mins more minute(s).", Toast.LENGTH_LONG).show()
            return
        }
        val input = EditText(ctx).apply {
            hint = "Type UNLOCK"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setPadding(Ui.dp(ctx, 20), Ui.dp(ctx, 16), Ui.dp(ctx, 20), Ui.dp(ctx, 16))
        }
        AlertDialog.Builder(ctx)
            .setTitle("Type UNLOCK to confirm")
            .setView(input)
            .setNegativeButton("Keep the lock", null)
            .setPositiveButton("Unlock") { _, _ ->
                if (input.text.toString().trim() == "UNLOCK") {
                    ss.unlockNow()
                    ScheduleEngine.cancel(ctx)
                    render()
                    Toast.makeText(ctx, "Week unlocked.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(ctx, "Not unlocked. You must type UNLOCK.", Toast.LENGTH_LONG).show()
                }
            }
            .show()
    }
}
