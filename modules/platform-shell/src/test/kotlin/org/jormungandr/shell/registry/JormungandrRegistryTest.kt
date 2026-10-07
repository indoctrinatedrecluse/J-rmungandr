package org.jormungandr.shell.registry

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.prefs.Preferences

class JormungandrRegistryTest {

    @Test
    fun `test registry initialization creates isolated product and vendor preference nodes`() {
        JormungandrRegistry.initialize()

        val root = Preferences.userRoot()
        val jormNode = root.node(JormungandrRegistry.ROOT_NODE)
        assertNotNull(jormNode)
        assertEquals("indoctrinatedrecluse", jormNode.get(JormungandrRegistry.KEY_VENDOR, ""))
        assertEquals("1.0.0", jormNode.get(JormungandrRegistry.KEY_APP_VERSION, ""))
        assertEquals("2.0", jormNode.get(JormungandrRegistry.KEY_EUA_ACCEPTED_VERSION, ""))
        assertEquals("2.0", jormNode.get(JormungandrRegistry.KEY_PRIVACY_POLICY_ACCEPTED_VERSION, ""))
        assertTrue(jormNode.getBoolean(JormungandrRegistry.KEY_FIRST_RUN_COMPLETED, false))
        assertTrue(jormNode.getLong(JormungandrRegistry.KEY_INSTALL_TIMESTAMP, 0L) > 0L)

        // Subnode verification
        val privacyNode = jormNode.node("privacy_policy")
        assertEquals("2.0", privacyNode.get(JormungandrRegistry.KEY_EUA_ACCEPTED_VERSION, ""))

        // Vendor node verification
        val vendorNode = root.node(JormungandrRegistry.VENDOR_ROOT_NODE)
        assertEquals("indoctrinatedrecluse", vendorNode.get(JormungandrRegistry.KEY_VENDOR, ""))

        assertTrue(JormungandrRegistry.isEuaAccepted())
    }

    @Test
    fun `test custom preference reading and writing`() {
        val testKey = "test_custom_setting"
        val testVal = "custom_value_42"

        JormungandrRegistry.setPreference(testKey, testVal)
        assertEquals(testVal, JormungandrRegistry.getPreference(testKey))
    }
}
