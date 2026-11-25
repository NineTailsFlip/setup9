package io.github.ninetailsflip.setup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private fun onOff(on: Boolean) = if (on) "on" else "off"

/** The next row (from [from], in [direction]) whose key differs from the current row's: the first
 *  letter of a name, a time zone's offset. Wraps around; null when every row has the same key. */
fun <K> jumpByKey(keys: List<K>, from: Int, direction: Int): Int? {
    if (keys.isEmpty()) return null
    val start = keys[from.coerceIn(0, keys.lastIndex)]
    var i = from
    repeat(keys.size) {
        i = (i + direction + keys.size) % keys.size
        if (keys[i] != start) {
            if (direction > 0) return i
            // Going back lands on the first row of the previous group, not its last.
            var first = i
            while (first > 0 && keys[first - 1] == keys[i]) first--
            return first
        }
    }
    return null
}

// ── 1. Language ─────────────────────────────────────────────────────────────────────────────

class LanguageStep : Step("Language") {
    private val locales by lazy { device.supportedLocales() }
    private fun name(locale: Locale) = locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    private fun isCurrent(locale: Locale) = locale.toLanguageTag() == device.currentLocale().toLanguageTag()

    override fun subtitle() = "the phone's language · the Nine apps stay in English"
    override fun hint() = "ok choose · ←→ next letter · * menu"

    override fun rows() = locales.map { locale ->
        Row(name(locale), locale.getDisplayName(Locale.ENGLISH), marked = isCurrent(locale)) {
            if (!isCurrent(locale)) device.setLocale(locale)
            next()
        }
    }

    override fun initialRow() = locales.indexOfFirst(::isCurrent).coerceAtLeast(0)

    override fun jump(from: Int, direction: Int) =
        jumpByKey(locales.map { name(it).first().uppercaseChar() }, from, direction)
}

// ── 2. Wi-Fi ────────────────────────────────────────────────────────────────────────────────

class WifiStep : Step("Wi-Fi") {
    private var networks: List<WifiNetwork> = emptyList()
    private var connected: String? = null
    private var connecting: String? = null
    private var listening = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reload()
    }

    override fun subtitle() = "join a network now, or skip and do it later in Settings"

    override fun onShow() {
        if (!listening) {
            val filter = IntentFilter().apply {
                addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
                addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            }
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            listening = true
        }
        if (device.isWifiOn()) device.scan()
        reload()
    }

    override fun onHide() {
        if (listening) {
            runCatching { context.unregisterReceiver(receiver) }
            listening = false
        }
    }

    private fun reload() {
        networks = if (device.isWifiOn()) device.networks() else emptyList()
        connected = device.connectedSsid()
        if (connected != null && connected == connecting) connecting = null
        host?.refresh()
    }

    override fun rows(): List<Row> = buildList {
        add(Row("Continue", connected?.let { "connected to $it" } ?: "without Wi-Fi for now") { next() })
        val on = device.isWifiOn()
        add(
            Row("Wi-Fi", onOff(on)) {
                device.setWifiOn(!on)
                if (!on) say("turning Wi-Fi on…")
                reload()
            },
        )
        if (!on) return@buildList
        if (networks.isEmpty()) add(Row("Looking for networks…"))
        networks.forEach { network ->
            add(Row(network.ssid, describe(network), marked = network.ssid == connected) { choose(network) })
        }
    }

    private fun describe(network: WifiNetwork): String = listOfNotNull(
        when (network.ssid) {
            connected -> "connected"
            connecting -> "connecting…"
            else -> null
        },
        when (network.security) {
            WifiSecurity.OPEN -> "open"
            WifiSecurity.PSK, WifiSecurity.SAE -> "password"
            WifiSecurity.OTHER -> "set up in Settings"
        },
        "▂▄▆█".take(network.level.coerceAtLeast(1)),
    ).joinToString(" · ")

    private fun choose(network: WifiNetwork) {
        if (network.ssid == connected) return next()
        when (network.security) {
            WifiSecurity.OPEN -> join(network, null)
            WifiSecurity.OTHER -> say("this network needs a certificate: set it up later in Settings")
            WifiSecurity.PSK, WifiSecurity.SAE -> askPassword(network)
        }
    }

    private fun askPassword(network: WifiNetwork) {
        host?.ask(
            FieldRequest("Password for ${network.ssid}", FieldKind.PASSWORD) { password ->
                if (password.length < 8) {
                    say("a Wi-Fi password has at least 8 characters")
                    askPassword(network)
                } else {
                    join(network, password)
                }
            },
        )
    }

    private fun join(network: WifiNetwork, password: String?) {
        if (device.connect(network, password)) {
            connecting = network.ssid
            say("connecting to ${network.ssid}…")
        } else {
            say("Android didn't take this network")
        }
        reload()
    }

    override fun menu() = listOf<Pair<String, () -> Unit>>(
        "Look for networks again" to {
            device.scan()
            say("looking for networks…")
        },
    )
}

// ── 3. Keyboard layouts ─────────────────────────────────────────────────────────────────────

/** Which of K9's layouts to type in: any number, at least one. Starts from what K9 has on, plus
 *  the layout of the phone's language. Saved in K9 on Continue, so going back and forth costs
 *  nothing; Settings → Keyboard changes it later. */
class KeyboardStep : Step("Keyboard") {
    private var layouts: List<KeyboardLayout> = emptyList()
    private var chosen: MutableSet<Int>? = null

    override fun subtitle() = "the layouts K9 types in · tick as many as you like"
    override fun hint() = "ok tick · ←→ next letter · * menu"

    override fun onShow() {
        layouts = device.keyboardLayouts()
        if (chosen == null && layouts.isNotEmpty()) chosen = initialChoice(layouts, device.currentLocale())
    }

    override fun rows(): List<Row> {
        val chosen = chosen
        if (layouts.isEmpty() || chosen == null) {
            return listOf(Row("Continue", "K9 isn't installed: the phone's keyboard stays") { next() })
        }
        val names = layouts.filter { it.id in chosen }.joinToString(", ") { it.name }
        return buildList {
            add(Row("Continue", names) { save(chosen) })
            layouts.forEach { layout ->
                add(Row(layout.name, Locale.forLanguageTag(layout.localeTag).getDisplayName(Locale.ENGLISH), marked = layout.id in chosen) {
                    toggle(chosen, layout)
                })
            }
        }
    }

    private fun toggle(chosen: MutableSet<Int>, layout: KeyboardLayout) {
        if (layout.id in chosen) {
            if (chosen.size == 1) return say("at least one layout stays on")
            chosen.remove(layout.id)
        } else {
            chosen.add(layout.id)
        }
        host?.refresh()
    }

    private fun save(chosen: Set<Int>) {
        if (device.setKeyboardLayouts(layouts.map { it.id }.filter { it in chosen })) next()
        else say("K9 didn't take the layouts")
    }

    override fun jump(from: Int, direction: Int) =
        jumpByKey(listOf(' ') + layouts.map { it.name.first().uppercaseChar() }, from, direction)

    companion object {
        /** What K9 has on, plus the layout of the phone's language when K9 has one for it. */
        fun initialChoice(layouts: List<KeyboardLayout>, locale: Locale): MutableSet<Int> {
            val chosen = layouts.filter { it.enabled }.map { it.id }.toMutableSet()
            val own = layouts.firstOrNull { Locale.forLanguageTag(it.localeTag).language == locale.language }
            if (own != null) chosen.add(own.id)
            if (chosen.isEmpty()) layouts.firstOrNull()?.let { chosen.add(it.id) }
            return chosen
        }
    }
}

// ── 4. Date and time ────────────────────────────────────────────────────────────────────────

class TimeZoneEntry(val id: String, val offsetMs: Int) {
    val city: String = id.substringAfterLast('/').replace('_', ' ')
    val region: String = id.substringBefore('/')
    val offsetLabel: String
        get() {
            val minutes = offsetMs / 60_000
            val sign = if (minutes < 0) "−" else "+"
            return "UTC$sign%02d:%02d".format(kotlin.math.abs(minutes) / 60, kotlin.math.abs(minutes) % 60)
        }
}

private val ZONE_REGIONS = setOf("Africa", "America", "Antarctica", "Asia", "Atlantic", "Australia", "Europe", "Indian", "Pacific")

/** Every city time zone, west to east, then by city. */
fun timeZones(now: Long): List<TimeZoneEntry> = TimeZone.getAvailableIDs()
    .filter { it.substringBefore('/') in ZONE_REGIONS && '/' in it }
    .map { TimeZoneEntry(it, TimeZone.getTimeZone(it).getOffset(now)) }
    .sortedWith(compareBy({ it.offsetMs }, { it.city }))

class TimeStep : Step("Date and time") {
    private var pickingZone = false
    private var zones: List<TimeZoneEntry> = emptyList()

    private fun format(pattern: String) = SimpleDateFormat(pattern, Locale.ENGLISH)
        .apply { timeZone = TimeZone.getTimeZone(device.zoneId()) }
        .format(Date(device.now()))

    private fun zoneLabel(id: String): String {
        val zone = TimeZoneEntry(id, TimeZone.getTimeZone(id).getOffset(device.now()))
        return "${zone.offsetLabel} · ${zone.city}"
    }

    override fun subtitle() = if (pickingZone) "pick your time zone" else "set by the network, or by hand"
    override fun hint() = if (pickingZone) "ok choose · ←→ next offset · ‹ back" else super.hint()

    override fun rows(): List<Row> = if (pickingZone) zoneRows() else timeRows()

    private fun timeRows(): List<Row> = buildList {
        val auto = device.autoTime()
        val autoZone = device.autoZone()
        add(Row("Continue", format("EEE d MMM yyyy, HH:mm")) { next() })
        add(Row("Automatic date and time", onOff(auto)) { device.setAutoTime(!auto) })
        add(Row("Automatic time zone", onOff(autoZone)) { device.setAutoZone(!autoZone) })
        add(Row("Time zone", zoneLabel(device.zoneId())) { openZones() })
        if (!auto) {
            add(Row("Date", "‹ ${format("EEE d MMM yyyy")} ›", adjust = { shift(it * DAY) }))
            add(Row("Hour", "‹ ${format("HH")} ›", adjust = { shift(it * HOUR) }))
            add(Row("Minute", "‹ ${format("mm")} ›", adjust = { shift(it * MINUTE) }))
        }
    }

    private fun shift(by: Long) = device.setTime(device.now() + by)

    private fun openZones() {
        zones = timeZones(device.now())
        pickingZone = true
        host?.showStep()
    }

    private fun zoneRows() = zones.map { zone ->
        Row("${zone.offsetLabel} · ${zone.city}", zone.region, marked = zone.id == device.zoneId()) {
            device.setAutoZone(false)
            device.setZone(zone.id)
            closeZones()
        }
    }

    private fun closeZones() {
        pickingZone = false
        host?.showStep()
    }

    override fun initialRow() = if (pickingZone) zones.indexOfFirst { it.id == device.zoneId() }.coerceAtLeast(0) else 0

    override fun back(): Boolean {
        if (!pickingZone) return false
        closeZones()
        return true
    }

    override fun jump(from: Int, direction: Int) = if (pickingZone) jumpByKey(zones.map { it.offsetMs }, from, direction) else null

    override fun onHide() {
        pickingZone = false
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}

// ── 5. Screen lock ──────────────────────────────────────────────────────────────────────────

class PinStep : Step("Screen lock") {
    override fun subtitle() = "a PIN keeps the phone yours if it's lost"

    override fun rows(): List<Row> = if (device.hasPin()) {
        listOf(
            Row("Continue", "the PIN is set: change it later in Settings") { next() },
        )
    } else {
        listOf(
            Row("Set a PIN", "4 to 16 digits") { askNew() },
            Row("No PIN", "anyone who opens the phone can use it") { next() },
        )
    }

    private fun askNew() {
        host?.ask(
            FieldRequest("New PIN, 4 to 16 digits", FieldKind.PIN) { first ->
                if (!isValidPin(first)) {
                    say("a PIN is 4 to 16 digits")
                    askNew()
                } else {
                    askRepeat(first)
                }
            },
        )
    }

    private fun askRepeat(first: String) {
        host?.ask(
            FieldRequest("The same PIN again", FieldKind.PIN) { second ->
                when {
                    second != first -> {
                        say("the two PINs differ: once more")
                        askNew()
                    }
                    device.setPin(first) -> {
                        say("PIN set")
                        next()
                    }
                    else -> say("Android didn't take the PIN")
                }
            },
        )
    }

    companion object {
        fun isValidPin(pin: String) = pin.length in 4..16 && pin.all { it in '0'..'9' }
    }
}

// ── 6. The Nine apps' look ──────────────────────────────────────────────────────────────────

class LookStep : Step("The Nine apps") {
    override fun subtitle() = "how L9, C9, P9, M9, G9, TG9 and K9 look"

    override fun rows(): List<Row> {
        val monospace = Choices.monospace(context)
        val hintBar = Choices.hintBar(context)
        return listOf(
            Row("Font", if (monospace) "monospace, as now" else "proportional, as now") {
                Choices.setMonospace(context, !monospace)
                host?.restart()
            },
            Row("Hint bar", if (hintBar) "on: the keys for each screen, at the bottom" else "off") {
                Choices.setHintBar(context, !hintBar)
                host?.restart()
            },
            Row("Continue", "each app's Settings → Theme can change this later") { next() },
        )
    }
}

// ── 7. Done ─────────────────────────────────────────────────────────────────────────────────

class DoneStep : Step("All set") {
    override fun subtitle() = "OK on the first row starts the phone"

    override fun rows(): List<Row> {
        val locale = device.currentLocale()
        return listOf(
            Row("Start using the phone", "the home screen opens") {
                Choices.publish(context, device.now())
                device.finishSetup()
                wizard.finished()
            },
            Row("Language", locale.getDisplayName(locale)),
            Row("Wi-Fi", device.connectedSsid()?.let { "connected to $it" } ?: "not connected"),
            Row("Keyboard", device.keyboardLayouts().filter { it.enabled }.joinToString(", ") { it.name }.ifEmpty { "the phone's own" }),
            Row("Screen lock", if (device.hasPin()) "PIN" else "none"),
            Row("Nine apps", listOf(
                if (Choices.monospace(context)) "monospace font" else "proportional font",
                if (Choices.hintBar(context)) "hint bar on" else "hint bar off",
            ).joinToString(" · ")),
        )
    }
}
