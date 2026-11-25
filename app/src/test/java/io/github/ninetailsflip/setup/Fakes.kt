package io.github.ninetailsflip.setup

import java.util.Locale

class FakeDevice : Device {
    var locales = listOf(Locale.forLanguageTag("de-DE"), Locale.forLanguageTag("en-US"), Locale.forLanguageTag("ru-RU"))
    var currentLocaleValue: Locale = Locale.forLanguageTag("en-US")
    var wifiEnabled = true
    var scanned = emptyList<WifiNetwork>()
    var connected: String? = null
    val joined = mutableListOf<Pair<String, String?>>()
    var auto = true
    var autoZoneOn = true
    var zoneValue = "Europe/Berlin"
    var clock = 1_764_000_000_000L
    var pin: String? = null
    var finished = false
    var keyboardInstalled = true
    val keyboards = mutableListOf<String>()
    var layouts = listOf(
        KeyboardLayout(1, "English", "en", true),
        KeyboardLayout(2, "Deutsch", "de", false),
        KeyboardLayout(3, "Русский", "ru", false),
    )
    var layoutsAccepted = true

    override fun supportedLocales() = locales
    override fun currentLocale() = currentLocaleValue
    override fun setLocale(locale: Locale) { currentLocaleValue = locale }
    override fun isWifiOn() = wifiEnabled
    override fun setWifiOn(on: Boolean) { wifiEnabled = on }
    override fun scan() {}
    override fun networks() = scanned
    override fun connectedSsid() = connected
    override fun connect(network: WifiNetwork, password: String?): Boolean { joined += network.ssid to password; return true }
    override fun autoTime() = auto
    override fun setAutoTime(on: Boolean) { auto = on }
    override fun autoZone() = autoZoneOn
    override fun setAutoZone(on: Boolean) { autoZoneOn = on }
    override fun zoneId() = zoneValue
    override fun setZone(id: String) { zoneValue = id }
    override fun now() = clock
    override fun setTime(millis: Long) { clock = millis }
    override fun hasPin() = pin != null
    override fun setPin(pin: String): Boolean { this.pin = pin; return true }
    override fun useKeyboard(imeId: String): Boolean { keyboards += imeId; return keyboardInstalled }
    override fun keyboardLayouts() = layouts
    override fun setKeyboardLayouts(ids: List<Int>): Boolean {
        if (!layoutsAccepted) return false
        layouts = layouts.map { it.copy(enabled = it.id in ids) }
        return true
    }
    override fun finishSetup() { finished = true }
}

/** Stands in for the screen: keeps the field a step asks for, and "types" into it. */
class FakeHost : StepHost {
    var asked: FieldRequest? = null
    var closed = false
    var restarts = 0

    override fun showStep() {}
    override fun refresh() {}
    override fun ask(request: FieldRequest) { asked = request }
    override fun restart() { restarts++ }
    override fun close() { closed = true }

    fun type(text: String) {
        val request = checkNotNull(asked) { "nothing was asked" }
        asked = null
        request.onDone(text)
    }
}
