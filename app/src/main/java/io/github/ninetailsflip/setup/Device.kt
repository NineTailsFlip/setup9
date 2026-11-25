package io.github.ninetailsflip.setup

import java.util.Locale

/** Everything the setup steps change on the phone. [AndroidDevice] does it with the system's
 *  own (partly hidden) APIs, which Setup may use as a platform-signed app; tests use a fake. */
interface Device {
    // Language
    fun supportedLocales(): List<Locale>
    fun currentLocale(): Locale
    fun setLocale(locale: Locale)

    // Wi-Fi
    fun isWifiOn(): Boolean
    fun setWifiOn(on: Boolean)
    fun scan()
    fun networks(): List<WifiNetwork>
    /** The network Wi-Fi is connected to, or null. */
    fun connectedSsid(): String?
    /** Saves the network and connects to it; false when Android refused it. */
    fun connect(network: WifiNetwork, password: String?): Boolean

    // Date and time
    fun autoTime(): Boolean
    fun setAutoTime(on: Boolean)
    fun autoZone(): Boolean
    fun setAutoZone(on: Boolean)
    fun zoneId(): String
    fun setZone(id: String)
    fun now(): Long
    fun setTime(millis: Long)

    // Screen lock
    fun hasPin(): Boolean
    /** Sets a PIN where there's no lock yet; false when Android refused it. */
    fun setPin(pin: String): Boolean

    // Keyboard
    /** Turns on the keyboard [imeId] ("package/service") and makes it the one in use; false when
     *  it isn't installed. */
    fun useKeyboard(imeId: String): Boolean
    /** K9's layouts (its languages), each with whether it's on; empty when K9 isn't installed. */
    fun keyboardLayouts(): List<KeyboardLayout>
    /** Turns on exactly [ids] in K9; false when K9 refused or isn't there. */
    fun setKeyboardLayouts(ids: List<Int>): Boolean

    /** Marks the phone set up (so the status bar, notifications and the launcher come alive),
     *  takes Setup off the home screen and opens the launcher. */
    fun finishSetup()
}

/** One of K9's layouts: [name] in its own language, [localeTag] a BCP 47 tag. */
data class KeyboardLayout(val id: Int, val name: String, val localeTag: String, val enabled: Boolean)

data class WifiNetwork(val ssid: String, val security: WifiSecurity, val level: Int)

enum class WifiSecurity { OPEN, PSK, SAE, OTHER }
