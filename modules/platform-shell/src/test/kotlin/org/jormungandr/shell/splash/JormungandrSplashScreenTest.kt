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
    fun `test minimum runtime threshold is at least 3000ms`() {
        assertEquals(3000L, JormungandrSplashScreen.MIN_RUNTIME_MS, "Minimum display duration must be 3,000ms (3 seconds)")
    }

    @Test
    fun `test remaining delay calculation enforces 3s minimum duration even if UI loads early`() {
        val mockStartTime = 10_000L

        // Simulate UI fully loading after only 400ms
        val fastUiReadyTime = mockStartTime + 400L

        // Set splash startTime internally
        JormungandrSplashScreen.forceClose()
        JormungandrSplashScreen.show() // sets startTimeMs

        // We can test calculateRemainingDelay logic
        val now = JormungandrSplashScreen.startTimeMs + 400L
        val remaining = JormungandrSplashScreen.calculateRemainingDelay(now)
        assertEquals(2600L, remaining, "Should hold splash screen for remaining 2,600ms when UI loads in 400ms")

        // Simulate UI loading after 3,200ms
        val slowUiReadyTime = JormungandrSplashScreen.startTimeMs + 3200L
        val remainingSlow = JormungandrSplashScreen.calculateRemainingDelay(slowUiReadyTime)
        assertEquals(0L, remainingSlow, "Should immediately dismiss when UI loading took longer than 3,000ms")

        JormungandrSplashScreen.forceClose()
    }
}
