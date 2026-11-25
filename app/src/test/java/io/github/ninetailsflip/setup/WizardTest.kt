package io.github.ninetailsflip.setup

import io.github.ninetailsflip.update.SetupChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class WizardTest {
    private val context = RuntimeEnvironment.getApplication()
    private val device = FakeDevice()
    private val host = FakeHost()
    private lateinit var wizard: Wizard

    @Before
    fun setUp() {
        wizard = Wizard(context, device).also { it.host = host }
    }

    private fun rows() = wizard.current.rows()
    private fun ok(title: String) = checkNotNull(rows().first { it.title.startsWith(title) }.ok) { "$title has no OK" }()
    private fun goTo(step: Class<out Step>) { while (!step.isInstance(wizard.current)) wizard.next() }

    @Test
    fun `the language list starts on the language in use, and choosing one moves on`() {
        assertTrue(wizard.current is LanguageStep)
        val start = wizard.current.initialRow()
        assertEquals("English (United States)", rows()[start].title)
        assertTrue(rows()[start].marked)

        ok("Русский")
        assertEquals(Locale.forLanguageTag("ru-RU"), device.currentLocaleValue)
        assertTrue(wizard.current is WifiStep)
    }

    @Test
    fun `back goes to the previous step, and the position survives a new process`() {
        wizard.next()
        wizard.next()
        assertTrue(wizard.current is TimeStep)
        wizard.back()
        assertTrue(wizard.current is WifiStep)
        assertTrue(Wizard(context, device).current is WifiStep)
    }

    @Test
    fun `a secured network asks for its password, and a short one again`() {
        device.scanned = listOf(WifiNetwork("Home", WifiSecurity.PSK, 3), WifiNetwork("Cafe", WifiSecurity.OPEN, 2))
        goTo(WifiStep::class.java)
        assertTrue(rows().any { it.title == "Home" && "password" in it.meta })

        ok("Home")
        host.type("short")
        assertEquals("a Wi-Fi password has at least 8 characters", wizard.message)
        assertNotNull("asked again", host.asked)
        host.type("long enough")
        assertEquals(listOf("Home" to "long enough"), device.joined)

        ok("Cafe")
        assertNull("an open network needs no password", host.asked)
        assertEquals("Cafe" to null, device.joined.last())
    }

    @Test
    fun `turning automatic time off offers date, hour and minute`() {
        goTo(TimeStep::class.java)
        assertFalse(rows().any { it.title == "Hour" })
        ok("Automatic date and time")
        assertFalse(device.auto)
        val before = device.clock
        checkNotNull(rows().first { it.title == "Hour" }.adjust)(1)
        assertEquals(before + 3_600_000L, device.clock)
    }

    @Test
    fun `a time zone picked by hand turns automatic time zone off`() {
        goTo(TimeStep::class.java)
        ok("Time zone")
        val step = wizard.current
        val zones = rows()
        assertTrue(zones.size > 100)
        assertTrue(zones[step.initialRow()].title.endsWith("· Berlin"))
        assertTrue(zones[step.initialRow()].marked)

        ok("UTC+03:00 · Moscow")
        assertEquals("Europe/Moscow", device.zoneValue)
        assertFalse(device.autoZoneOn)
        assertTrue(rows().any { it.title == "Automatic time zone" })
    }

    @Test
    fun `left and right jump between time zone offsets`() {
        goTo(TimeStep::class.java)
        ok("Time zone")
        val zones = rows()
        val from = wizard.current.initialRow()
        val next = checkNotNull(wizard.current.jump(from, 1))
        assertTrue(zones[next].title.substringBefore(" ·") != zones[from].title.substringBefore(" ·"))
        assertTrue(wizard.current.back())
        assertTrue(rows().first().title == "Continue")
    }

    @Test
    fun `a PIN is asked twice and must match`() {
        goTo(PinStep::class.java)
        ok("Set a PIN")
        host.type("12")
        assertEquals("a PIN is 4 to 16 digits", wizard.message)
        host.type("1234")
        host.type("4321")
        assertEquals("the two PINs differ: once more", wizard.message)
        assertNull(device.pin)
        host.type("2580")
        host.type("2580")
        assertEquals("2580", device.pin)
        assertTrue(wizard.current is LookStep)
    }

    @Test
    fun `the look changes Setup's own screen, then every Nine app's after setup`() {
        goTo(LookStep::class.java)
        assertTrue(Choices.monospace(context))
        ok("Font")
        assertFalse(Choices.monospace(context))
        assertEquals(1, host.restarts)
        assertTrue("nothing is served before setup is done", Choices.served(context).isEmpty())

        wizard.next()
        assertTrue(wizard.current is DoneStep)
        ok("Start using the phone")
        assertTrue(device.finished)
        assertTrue(host.closed)
        val served = Choices.served(context)
        assertEquals("false", served[SetupChoices.KEY_MONOSPACE_FONT])
        assertEquals("true", served[SetupChoices.KEY_SHOW_HINT_BAR])
        assertEquals(device.clock.toString(), served[SetupChoices.KEY_REVISION])
        assertTrue("a later setup starts over", Wizard(context, device).current is LanguageStep)
    }

    @Test
    fun `jumping by key wraps and lands on the start of a group`() {
        val keys = listOf('A', 'A', 'B', 'B', 'C')
        assertEquals(2, jumpByKey(keys, 0, 1))
        assertEquals(4, jumpByKey(keys, 2, 1))
        assertEquals(0, jumpByKey(keys, 4, 1))
        assertEquals(2, jumpByKey(keys, 4, -1))
        assertEquals(4, jumpByKey(keys, 0, -1))
        assertNull(jumpByKey(listOf('A', 'A'), 0, 1))
    }
}
