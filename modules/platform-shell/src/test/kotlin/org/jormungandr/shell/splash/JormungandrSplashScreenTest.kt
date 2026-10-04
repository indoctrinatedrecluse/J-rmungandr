package org.jormungandr.shell.splash

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JormungandrSplashScreenTest {

    @Test
    fun `test splash GIF asset exists and is valid animated GIF89a`() {
        val stream = JormungandrSplashScreen::class.java.getResourceAsStream("/splash/splash.gif")
        assertNotNull(stream, "Animated splash screen resource /splash/splash.gif must exist on classpath")

        val bytes = stream!!.readBytes()
        assertTrue(bytes.size > 10_000, "Splash GIF should contain animation frame data")

        // GIF magic header: 'G' 'I' 'F' '8' '9' 'a'
        val header = String(bytes.sliceArray(0..5), Charsets.US_ASCII)
        assertEquals("GIF89a", header, "Splash resource must be a standard GIF89a format supporting animation and transparency")
    }

    @Test
    fun `test runtime threshold is 3000ms`() {
        assertEquals(3000L, JormungandrSplashScreen.MAX_RUNTIME_MS, "Max display duration must be 3,000ms (3 seconds)")
        assertEquals(3000L, JormungandrSplashScreen.MIN_RUNTIME_MS, "Backward-compatibility alias must be 3,000ms")
    }

    @Test
    fun `test remaining delay calculation counts down to max 3s timeout`() {
        JormungandrSplashScreen.forceClose()
        JormungandrSplashScreen.show() // sets startTimeMs and isDisplayed = true

        // 400ms into display, 2600ms remaining until timeout
        val now = JormungandrSplashScreen.startTimeMs + 400L
        val remaining = JormungandrSplashScreen.calculateRemainingDelay(now)
        assertEquals(2600L, remaining, "Should report 2,600ms remaining until 3s timeout when 400ms has elapsed")

        // 3200ms into display (past 3000ms threshold)
        val slowUiReadyTime = JormungandrSplashScreen.startTimeMs + 3200L
        val remainingSlow = JormungandrSplashScreen.calculateRemainingDelay(slowUiReadyTime)
        assertEquals(0L, remainingSlow, "Should report 0ms remaining when elapsed time exceeds 3,000ms")

        JormungandrSplashScreen.forceClose()
    }

    @Test
    fun `test dismiss immediately completes without holding delay when UI loads early`() {
        JormungandrSplashScreen.forceClose()
        JormungandrSplashScreen.show()
        assertTrue(JormungandrSplashScreen.isDisplayed, "Splash should be displayed initially")

        var dismissedCalled = false
        JormungandrSplashScreen.dismiss {
            dismissedCalled = true
        }

        // Give Swing EDT a brief moment to process the invokeLater
        Thread.sleep(150)
        assertFalse(JormungandrSplashScreen.isDisplayed, "Splash must be dismissed immediately without holding for 3s")
        assertTrue(dismissedCalled, "Dismiss callback should be invoked")
        assertEquals(0L, JormungandrSplashScreen.calculateRemainingDelay(), "Remaining delay should be 0 once dismissed")

        JormungandrSplashScreen.forceClose()
    }
}
