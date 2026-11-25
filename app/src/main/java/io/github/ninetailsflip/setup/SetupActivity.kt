package io.github.ninetailsflip.setup

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import life.furrer.nine.core.ui.Dimens
import life.furrer.nine.core.ui.HintBar
import life.furrer.nine.core.ui.MenuItem
import life.furrer.nine.core.ui.MenuSheet
import life.furrer.nine.core.ui.NineActivity
import life.furrer.nine.core.ui.Palette
import life.furrer.nine.core.ui.RowBinder
import life.furrer.nine.core.ui.RowListView
import life.furrer.nine.core.ui.dp
import life.furrer.nine.core.ui.relaunch

/** The setup's one screen: the current step's title, what it's for, its rows, and - when a step
 *  asks for a password or a PIN - a field under them. Up/Down move, OK chooses, Left/Right change
 *  a value, Back is the previous step, `*` the menu (skip a step). It's the home screen until
 *  setup is done, so Home brings it back and Back never leaves it. */
class SetupActivity : NineActivity(), StepHost {

    private val wizard by lazy { (application as SetupApp).wizard }
    private var rows: List<Row> = emptyList()
    private var field: FieldRequest? = null

    private lateinit var title: TextView
    private lateinit var subtitle: TextView
    private lateinit var rowListView: RowListView
    private lateinit var input: EditText
    private lateinit var hintBar: HintBar
    private lateinit var menuSheet: MenuSheet

    private val binder = object : RowBinder {
        override fun rowCount() = rows.size
        override fun createRowView(context: Context): View = TwoLineRowView(context)
        override fun bind(view: View, index: Int, selected: Boolean) {
            val row = rows[index]
            (view as TwoLineRowView).bind(row.title, row.meta, selected, row.marked)
        }
        override fun rowHeight(index: Int) = TWO_LINE_ROW_DP
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = TextView(this).apply {
            textSize = 20f
            setTextColor(Palette.WHITE)
            setPadding(dp(Dimens.SIDE_PADDING_DP), dp(12), dp(Dimens.SIDE_PADDING_DP), 0)
        }
        subtitle = TextView(this).apply {
            textSize = 12f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setTextColor(Palette.GRAY_MUTED)
            setPadding(dp(Dimens.SIDE_PADDING_DP), dp(2), dp(Dimens.SIDE_PADDING_DP), dp(8))
        }
        rowListView = RowListView(this).apply { setBinder(binder) }
        input = EditText(this).apply {
            textSize = 18f
            setTextColor(Color.WHITE)
            setHintTextColor(Palette.GRAY_FAINT)
            setBackgroundColor(Palette.BLACK)
            setPadding(dp(Dimens.SIDE_PADDING_DP), dp(8), dp(Dimens.SIDE_PADDING_DP), dp(8))
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true
            visibility = View.GONE
            // Not focusable until a step asks: a focusable field in a fresh window takes the
            // keyboard's connection at once, and the keypad's * and # with it.
            isFocusable = false
            setOnEditorActionListener { _, _, _ -> submit(); true }
        }
        hintBar = HintBar(this)
        menuSheet = MenuSheet(this)
        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(Palette.BLACK)
                importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                addView(
                    LinearLayout(this@SetupActivity).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(title)
                        addView(subtitle)
                        addView(rowListView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                        addView(input)
                        addView(hintBar)
                    },
                    FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
                )
                addView(menuSheet, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            },
        )
        wizard.host = this
        showStep(intent.getIntExtra(EXTRA_ROW, -1))
    }

    override fun onDestroy() {
        if (wizard.host === this) wizard.host = null
        super.onDestroy()
    }

    // ── StepHost ────────────────────────────────────────────────────────────────────────────

    override fun showStep() = showStep(-1)

    private fun showStep(row: Int) {
        rows = wizard.current.rows()
        rowListView.notifyDataChanged()
        rowListView.setSelectedIndex(if (row >= 0) row.coerceAtMost(rows.lastIndex) else wizard.current.initialRow())
        render()
    }

    override fun refresh() {
        rows = wizard.current.rows()
        rowListView.notifyDataChanged()
        if (rowListView.selectedIndex > rows.lastIndex) rowListView.setSelectedIndex(rows.lastIndex)
        render()
    }

    private fun render() {
        val step = wizard.current
        title.text = step.title
        subtitle.text = "${wizard.index + 1} of ${wizard.steps.size} · ${step.subtitle()}"
        hintBar.setText(
            wizard.message ?: if (field != null) "type · ok done · ‹ on an empty field: cancel" else step.hint(),
        )
    }

    override fun ask(request: FieldRequest) {
        field = request
        input.setText("")
        input.hint = request.prompt
        input.inputType = when (request.kind) {
            FieldKind.PIN -> InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            // Shown as typed: on a keypad, seeing each letter is how you know the right one came.
            FieldKind.PASSWORD -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        input.visibility = View.VISIBLE
        input.isFocusable = true
        input.isFocusableInTouchMode = true
        input.requestFocus()
        render()
    }

    /** Hands the text to the step. Unless it asks again (the PIN's second time), a fresh screen
     *  takes the keyboard's connection away, so `*` and `#` work again (core's relaunch). */
    private fun submit() {
        val request = field ?: return
        field = null
        request.onDone(input.text.toString())
        if (field == null) restartAt(rowListView.selectedIndex)
    }

    private fun cancelField() {
        field = null
        wizard.clearMessage()
        restartAt(rowListView.selectedIndex)
    }

    override fun restart() = restartAt(rowListView.selectedIndex)

    private fun restartAt(row: Int) = relaunch(Intent(this, SetupActivity::class.java).putExtra(EXTRA_ROW, row))

    override fun close() = finish()

    // ── Keys ────────────────────────────────────────────────────────────────────────────────

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (field != null && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> if (input.text.isEmpty()) {
                    cancelField()
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    submit()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (field != null) return super.onKeyDown(keyCode, event)
        if (menuSheet.handleKeyDown(keyCode, event)) return true
        wizard.clearMessage()
        val step = wizard.current
        val index = rowListView.selectedIndex
        val row = rows.getOrNull(index)
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> rowListView.moveSelection(-1)
            KeyEvent.KEYCODE_DPAD_DOWN -> rowListView.moveSelection(1)
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                val direction = if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1 else 1
                val adjust = row?.adjust
                if (adjust != null) adjust(direction) else step.jump(index, direction)?.let(rowListView::setSelectedIndex)
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> row?.ok?.invoke()
            KeyEvent.KEYCODE_BACK -> wizard.back()
            KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_MENU -> openMenu()
            else -> return super.onKeyDown(keyCode, event)
        }
        // The step may have moved on (or the screen restarted) in the meantime.
        if (!isFinishing) refresh()
        return true
    }

    private fun openMenu() {
        val items = wizard.current.menu().map { (label, action) -> MenuItem(label) { action(); refresh() } }.toMutableList()
        if (wizard.index < wizard.steps.lastIndex) items += MenuItem("Skip this step") { wizard.next() }
        if (wizard.index > 0) items += MenuItem("The previous step") { wizard.back() }
        if (items.isNotEmpty()) menuSheet.show(items)
    }

    private companion object {
        const val EXTRA_ROW = "row"
    }
}
