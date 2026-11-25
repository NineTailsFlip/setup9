package io.github.ninetailsflip.setup

import android.app.Application
import life.furrer.nine.core.KeyHelp
import life.furrer.nine.core.Nine
import life.furrer.nine.core.NineApp
import life.furrer.nine.core.NineSurface

class SetupApp : Application() {
    /** What the steps change; a test swaps in a fake before the screen opens. */
    var device: Device? = null
    /** The setup itself, for the life of the process: a relaunched screen (after typing) picks
     *  it up where it was. */
    val wizard: Wizard by lazy { Wizard(this, device ?: AndroidDevice(this).also { device = it }) }

    override fun onCreate() {
        super.onCreate()
        Nine.init(
            NineApp(
                id = "setup",
                name = "Setup",
                tagline = "The phone's first-time setup",
                surfaces = listOf(
                    NineSurface(
                        id = "",
                        name = "Setup",
                        introText = "Sets up the phone the first time it starts: language, Wi-Fi, date and time, a PIN and the look of the Nine apps.",
                        keyMap = listOf(
                            KeyHelp("Up / Down", "Move"),
                            KeyHelp("OK", "Choose"),
                            KeyHelp("Left / Right", "Change a value; jump by letter in long lists"),
                            KeyHelp("Back", "The previous step"),
                            KeyHelp("*", "Menu: skip a step"),
                        ),
                    ),
                ),
                websiteUrl = "https://github.com/NineTailsFlip",
                sourceUrl = "https://github.com/NineTailsFlip/setup9",
                trackerUrl = "https://github.com/NineTailsFlip/setup9/issues",
                settingsActivity = SetupActivity::class.java,
            ),
        )
    }
}
