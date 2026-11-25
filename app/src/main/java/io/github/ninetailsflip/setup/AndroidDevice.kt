package io.github.ninetailsflip.setup

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.net.wifi.SupplicantState
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.os.LocaleList
import android.os.Process
import android.provider.Settings
import android.util.Log
import java.util.Locale
import java.util.TimeZone

/** [Device] on the phone. The hidden APIs are the ones Settings and LineageSetupWizard use:
 *  LocalePicker for the language, LockPatternUtils for the PIN. Setup is signed with the
 *  platform key, which both lets it call them and grants the permissions they check. */
@SuppressLint("PrivateApi") // LocalePicker and LockPatternUtils: see above.
class AndroidDevice(private val context: Context) : Device {
    private val resolver = context.contentResolver
    private val wifi = context.getSystemService(WifiManager::class.java)

    // ── Language ────────────────────────────────────────────────────────────────────────────

    override fun supportedLocales(): List<Locale> {
        @Suppress("UNCHECKED_CAST")
        val tags = runCatching {
            localePicker().getMethod("getSupportedLocales", Context::class.java).invoke(null, context) as Array<String>
        }.getOrNull() ?: Resources.getSystem().assets.locales
        return tags.asSequence()
            .map { Locale.forLanguageTag(it.replace('_', '-')) }
            .filter { it.language.isNotEmpty() && it.country.isNotEmpty() }
            .distinct()
            .sortedBy { it.getDisplayName(it).lowercase(it) }
            .toList()
    }

    override fun currentLocale(): Locale = Resources.getSystem().configuration.locales[0]

    override fun setLocale(locale: Locale) = safely("set the language") {
        localePicker().getMethod("updateLocales", LocaleList::class.java).invoke(null, LocaleList(locale))
    }

    private fun localePicker() = Class.forName("com.android.internal.app.LocalePicker")

    // ── Wi-Fi ───────────────────────────────────────────────────────────────────────────────

    override fun isWifiOn() = wifi.isWifiEnabled

    @Suppress("DEPRECATION") // Still allowed for an app holding NETWORK_SETTINGS.
    override fun setWifiOn(on: Boolean) = safely("turn Wi-Fi ${if (on) "on" else "off"}") {
        wifi.setWifiEnabled(on)
    }

    @Suppress("DEPRECATION")
    override fun scan() = safely("scan for networks") {
        wifi.startScan()
    }

    // NETWORK_SETTINGS lets Setup see scan results without location access, as Settings does.
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION") // ScanResult.SSID: the plain name is all a list row needs.
    override fun networks(): List<WifiNetwork> = (runCatching { wifi.scanResults }.getOrNull() ?: emptyList())
        .filter { !it.SSID.isNullOrEmpty() }
        .groupBy { it.SSID }
        .map { (ssid, results) ->
            val best = results.maxBy { it.level }
            WifiNetwork(ssid, security(best.capabilities), wifi.calculateSignalLevel(best.level).coerceIn(0, 4))
        }
        .sortedByDescending { it.level }

    private fun security(capabilities: String): WifiSecurity = when {
        "EAP" in capabilities || "WEP" in capabilities -> WifiSecurity.OTHER
        // A WPA2/WPA3 network ("PSK" and "SAE") takes a WPA2 password too.
        "PSK" in capabilities -> WifiSecurity.PSK
        "SAE" in capabilities -> WifiSecurity.SAE
        else -> WifiSecurity.OPEN
    }

    @Suppress("DEPRECATION")
    override fun connectedSsid(): String? {
        val info = wifi.connectionInfo ?: return null
        if (info.supplicantState != SupplicantState.COMPLETED) return null
        return info.ssid?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() && it != WifiManager.UNKNOWN_SSID }
    }

    @Suppress("DEPRECATION") // addNetwork/enableNetwork: still Settings' way for NETWORK_SETTINGS holders.
    override fun connect(network: WifiNetwork, password: String?): Boolean = try {
        join(network, password)
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't join ${network.ssid}", e)
        false
    }

    @Suppress("DEPRECATION")
    private fun join(network: WifiNetwork, password: String?): Boolean {
        val config = WifiConfiguration().apply {
            SSID = "\"${network.ssid}\""
            when (network.security) {
                WifiSecurity.OPEN -> setSecurityParams(WifiConfiguration.SECURITY_TYPE_OPEN)
                WifiSecurity.PSK -> {
                    setSecurityParams(WifiConfiguration.SECURITY_TYPE_PSK)
                    preSharedKey = "\"$password\""
                }
                WifiSecurity.SAE -> {
                    setSecurityParams(WifiConfiguration.SECURITY_TYPE_SAE)
                    preSharedKey = "\"$password\""
                }
                WifiSecurity.OTHER -> return false
            }
        }
        val id = wifi.addNetwork(config)
        if (id < 0) return false
        return wifi.enableNetwork(id, true)
    }

    // ── Date and time ───────────────────────────────────────────────────────────────────────

    override fun autoTime() = Settings.Global.getInt(resolver, Settings.Global.AUTO_TIME, 1) == 1

    override fun setAutoTime(on: Boolean) = safely("set automatic time") {
        Settings.Global.putInt(resolver, Settings.Global.AUTO_TIME, if (on) 1 else 0)
    }

    override fun autoZone() = Settings.Global.getInt(resolver, Settings.Global.AUTO_TIME_ZONE, 1) == 1

    override fun setAutoZone(on: Boolean) = safely("set automatic time zone") {
        Settings.Global.putInt(resolver, Settings.Global.AUTO_TIME_ZONE, if (on) 1 else 0)
    }

    override fun zoneId(): String = TimeZone.getDefault().id

    override fun setZone(id: String) = safely("set the time zone") {
        context.getSystemService(AlarmManager::class.java).setTimeZone(id)
        TimeZone.setDefault(TimeZone.getTimeZone(id))
    }

    override fun now() = System.currentTimeMillis()

    override fun setTime(millis: Long) = safely("set the time") {
        context.getSystemService(AlarmManager::class.java).setTime(millis)
    }

    // ── Screen lock ─────────────────────────────────────────────────────────────────────────

    override fun hasPin() = context.getSystemService(KeyguardManager::class.java).isDeviceSecure

    override fun setPin(pin: String): Boolean = try {
        val utils = Class.forName("com.android.internal.widget.LockPatternUtils").getConstructor(Context::class.java).newInstance(context)
        val credential = Class.forName("com.android.internal.widget.LockscreenCredential")
        val newPin = credential.getMethod("createPin", CharSequence::class.java).invoke(null, pin)
        val none = credential.getMethod("createNone").invoke(null)
        val userId = Process.myUid() / PER_USER_RANGE
        utils.javaClass.getMethod("setLockCredential", credential, credential, Int::class.javaPrimitiveType)
            .invoke(utils, newPin, none, userId) as Boolean
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't set the PIN", e)
        false
    }

    // ── Keyboard ────────────────────────────────────────────────────────────────────────────

    override fun useKeyboard(imeId: String): Boolean = try {
        context.packageManager.getPackageInfo(imeId.substringBefore('/'), 0)
        // "id;subtype;subtype:id": the enabled keyboards, each with its enabled subtypes.
        val enabled = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_INPUT_METHODS).orEmpty()
            .split(':').filter { it.isNotEmpty() }
        if (enabled.none { it.substringBefore(';') == imeId }) {
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_INPUT_METHODS, (enabled + imeId).joinToString(":"))
        }
        Settings.Secure.putString(resolver, Settings.Secure.DEFAULT_INPUT_METHOD, imeId)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    } catch (e: Exception) {
        Log.w(TAG, "Couldn't switch to the keyboard $imeId", e)
        false
    }

    // ── Done ────────────────────────────────────────────────────────────────────────────────

    override fun finishSetup() {
        safely("mark the phone set up") {
            Settings.Global.putInt(resolver, Settings.Global.DEVICE_PROVISIONED, 1)
            Settings.Secure.putInt(resolver, USER_SETUP_COMPLETE, 1)
        }
        context.packageManager.setComponentEnabledSetting(
            ComponentName(context, SetupActivity::class.java),
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        context.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Setup is the home screen until it's done: a step that fails must not take it down with
     *  it, or the phone has no screen to go to. */
    private inline fun safely(what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't $what", e)
        }
    }

    private companion object {
        const val TAG = "Setup"
        const val PER_USER_RANGE = 100_000
        /** Settings.Secure.USER_SETUP_COMPLETE, a hidden constant. */
        const val USER_SETUP_COMPLETE = "user_setup_complete"
    }
}
