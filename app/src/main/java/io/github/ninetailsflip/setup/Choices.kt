package io.github.ninetailsflip.setup

import android.annotation.SuppressLint
import android.content.Context
import io.github.ninetailsflip.update.SetupChoices
import life.furrer.nine.core.prefs.NinePrefs

/** The Nine suite's look as chosen in setup. Kept in Setup's own NinePrefs, so Setup's screens
 *  already show the font and hint bar while you choose; [publish] stamps them with a revision
 *  when setup is done, and from then on [ChoicesProvider] serves them to every Nine app. */
object Choices {
    private const val PREFS = "choices"
    private const val KEY_REVISION = "revision"

    fun monospace(context: Context) = NinePrefs.useMonospaceFont(context)
    fun setMonospace(context: Context, on: Boolean) = NinePrefs.setUseMonospaceFont(context, on)
    fun hintBar(context: Context) = NinePrefs.showHintBar(context)
    fun setHintBar(context: Context, on: Boolean) = NinePrefs.setShowHintBar(context, on)

    // On disk before setup ends: the Nine apps read it as soon as the launcher comes up.
    @SuppressLint("ApplySharedPref")
    fun publish(context: Context, now: Long) {
        prefs(context).edit().putLong(KEY_REVISION, now).commit()
    }

    /** What the provider serves, in nine-core's SetupChoices keys; empty until [publish]. */
    fun served(context: Context): Map<String, String> {
        val revision = prefs(context).getLong(KEY_REVISION, 0L)
        if (revision == 0L) return emptyMap()
        return mapOf(
            SetupChoices.KEY_REVISION to revision.toString(),
            SetupChoices.KEY_MONOSPACE_FONT to monospace(context).toString(),
            SetupChoices.KEY_SHOW_HINT_BAR to hintBar(context).toString(),
        )
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
