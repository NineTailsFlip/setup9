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

    /** Marks the phone set up (so the status bar, notifications and the launcher come alive),
     *  takes Setup off the home screen and opens the launcher. */
    fun finishSetup()
}

data class WifiNetwork(val ssid: String, val security: WifiSecurity, val level: Int)

enum class WifiSecurity { OPEN, PSK, SAE, OTHER }
