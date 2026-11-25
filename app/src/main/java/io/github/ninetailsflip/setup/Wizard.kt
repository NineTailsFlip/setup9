package io.github.ninetailsflip.setup

import android.annotation.SuppressLint
import android.content.Context

/** One row of a step: a title over a dim line of details. [ok] runs on OK; [adjust] on Left and
 *  Right (-1 / +1), for a value shown as "‹ value ›". A row with neither only informs. */
class Row(
    val title: String,
    val meta: String = "",
    val marked: Boolean = false,
    val adjust: ((Int) -> Unit)? = null,
    val ok: (() -> Unit)? = null,
)

enum class FieldKind { PASSWORD, PIN }

/** A line of text to type under the list (a Wi-Fi password, a PIN): OK hands it to [onDone]. */
class FieldRequest(val prompt: String, val kind: FieldKind, val onDone: (String) -> Unit)

/** The screen showing the wizard. */
interface StepHost {
    /** The step changed: show it from its first row (or [Step.initialRow]). */
    fun showStep()
    /** Something on the step changed: redraw its rows where they are. */
    fun refresh()
    fun ask(request: FieldRequest)
    /** A fresh screen, so a new font or hint bar shows. */
    fun restart()
    fun close()
}

abstract class Step(val title: String) {
    lateinit var wizard: Wizard
    protected val device get() = wizard.device
    protected val context get() = wizard.context
    protected val host get() = wizard.host

    abstract fun subtitle(): String
    abstract fun rows(): List<Row>
    open fun hint() = "ok choose · ‹ back · * menu"
    open fun initialRow() = 0
    open fun onShow() {}
    open fun onHide() {}
    /** Back inside the step (leaving a sub-list); false goes to the previous step. */
    open fun back() = false
    /** The step's own entries in the `*` menu. */
    open fun menu(): List<Pair<String, () -> Unit>> = emptyList()
    /** Left/Right on a row without [Row.adjust]: the row to jump to, or null. */
    open fun jump(from: Int, direction: Int): Int? = null

    protected fun say(text: String) = wizard.say(text)
    protected fun next() = wizard.next()
}

/** The setup's steps and where it is. The position survives a restart of the phone mid-setup. */
class Wizard(val context: Context, val device: Device) {
    val steps: List<Step> = listOf(LanguageStep(), WifiStep(), TimeStep(), PinStep(), LookStep(), DoneStep())
        .onEach { it.wizard = this }

    var host: StepHost? = null
        set(value) {
            field = value
            if (value != null && !started) {
                started = true
                current.onShow()
            }
        }
    private var started = false

    /** A message for the hint bar until the next key. */
    var message: String? = null
        private set

    private val prefs = context.getSharedPreferences("wizard", Context.MODE_PRIVATE)
    var index: Int = prefs.getInt(KEY_STEP, 0).coerceIn(0, steps.lastIndex)
        private set

    val current: Step get() = steps[index]

    fun next() {
        if (index < steps.lastIndex) go(index + 1)
    }

    /** Back: inside the step first, then the previous step. */
    fun back() {
        if (current.back()) return
        if (index > 0) go(index - 1)
    }

    fun say(text: String) {
        message = text
        host?.refresh()
    }

    fun clearMessage() {
        message = null
    }

    /** Setup is done: the next setup (after a reset) starts from the beginning. */
    @SuppressLint("ApplySharedPref") // Written before Setup's screen goes away for good.
    fun finished() {
        current.onHide()
        prefs.edit().putInt(KEY_STEP, 0).commit()
        host?.close()
    }

    private fun go(to: Int) {
        current.onHide()
        index = to
        prefs.edit().putInt(KEY_STEP, to).apply()
        message = null
        current.onShow()
        host?.showStep()
    }

    private companion object {
        const val KEY_STEP = "step"
    }
}
