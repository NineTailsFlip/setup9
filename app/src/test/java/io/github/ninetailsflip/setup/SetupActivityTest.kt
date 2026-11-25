package io.github.ninetailsflip.setup

import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.TextView
import io.github.ninetailsflip.update.SetupChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class SetupActivityTest {
    private val app = RuntimeEnvironment.getApplication() as SetupApp

    private fun texts(view: android.view.View): List<String> = when (view) {
        is TextView -> listOf(view.text.toString())
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }

    private fun press(activity: SetupActivity, keyCode: Int) {
        activity.onKeyDown(keyCode, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
    }

    @Test
    fun `opens on the language step and OK moves on`() {
        val device = FakeDevice()
        app.device = device
        val activity = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        val shown = texts(activity.findViewById(android.R.id.content))
        assertTrue(shown.contains("Language"))
        assertTrue(shown.any { it.startsWith("1 of 7") })

        press(activity, KeyEvent.KEYCODE_DPAD_DOWN)
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        assertEquals("ru-RU", device.currentLocaleValue.toLanguageTag())
        assertTrue(texts(activity.findViewById(android.R.id.content)).contains("Wi-Fi"))
    }

    @Test
    fun `the provider serves nothing until setup is done, then the look`() {
        val resolver = app.contentResolver
        Robolectric.setupContentProvider(ChoicesProvider::class.java, SetupChoices.AUTHORITY)
        fun served() = resolver.query(SetupChoices.URI, null, null, null, null)!!.use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }
        assertTrue(served().isEmpty())
        Choices.setHintBar(app, false)
        Choices.publish(app, 42L)
        assertEquals(mapOf("revision" to "42", "use_monospace_font" to "true", "show_hint_bar" to "false"), served())
    }

    @Test
    fun `a step that breaks still leaves a way on`() {
        app.device = object : Device by FakeDevice() {
            override fun supportedLocales(): List<java.util.Locale> = throw IllegalStateException("no locales")
        }
        val activity = Robolectric.buildActivity(SetupActivity::class.java).setup().get()
        assertTrue(texts(activity.findViewById(android.R.id.content)).contains("Continue"))
        press(activity, KeyEvent.KEYCODE_DPAD_CENTER)
        assertTrue(texts(activity.findViewById(android.R.id.content)).contains("Wi-Fi"))
    }
}
