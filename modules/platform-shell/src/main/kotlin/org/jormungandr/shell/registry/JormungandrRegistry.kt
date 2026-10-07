package org.jormungandr.shell.registry

import com.intellij.openapi.diagnostic.Logger
import java.util.prefs.Preferences

/**
 * Manages Windows Registry and Java Preferences entries isolated specifically for Jörmungandr.
 *
 * Dedicated storage nodes:
 * - Preferences.userRoot().node("jormungandr") -> HKCU:\SOFTWARE\JavaSoft\Prefs\jormungandr
 * - Preferences.userRoot().node("indoctrinatedrecluse/jormungandr") -> HKCU:\SOFTWARE\JavaSoft\Prefs\indoctrinatedrecluse\jormungandr
 *
 * This guarantees complete separation from JetBrains Enterprise installations
 * (DataGrip, PyCharm, GoLand, Rider, etc.) while allowing clean standalone distribution.
 */
object JormungandrRegistry {

    private val LOG = Logger.getInstance(JormungandrRegistry::class.java)

    const val ROOT_NODE = "jormungandr"
    const val VENDOR_ROOT_NODE = "indoctrinatedrecluse/jormungandr"

    const val KEY_EUA_ACCEPTED_VERSION = "eua_accepted_version"
    const val KEY_PRIVACY_POLICY_ACCEPTED_VERSION = "privacy_policy_accepted_version"
    const val KEY_FIRST_RUN_COMPLETED = "first_run_completed"
    const val KEY_APP_VERSION = "version"
    const val KEY_VENDOR = "vendor"
    const val KEY_INSTALL_TIMESTAMP = "install_timestamp"

    const val CURRENT_EUA_VERSION = "2.0"
    const val CURRENT_APP_VERSION = "1.0.0"
    const val VENDOR_NAME = "indoctrinatedrecluse"

    /**
     * Initializes Jörmungandr-specific preference nodes in the user registry hive.
     */
    fun initialize() {
        try {
            val root = Preferences.userRoot()
            val jormNode = root.node(ROOT_NODE)
            jormNode.put(KEY_VENDOR, VENDOR_NAME)
            jormNode.put(KEY_APP_VERSION, CURRENT_APP_VERSION)
            jormNode.put(KEY_EUA_ACCEPTED_VERSION, CURRENT_EUA_VERSION)
            jormNode.put(KEY_PRIVACY_POLICY_ACCEPTED_VERSION, CURRENT_EUA_VERSION)
            jormNode.putBoolean(KEY_FIRST_RUN_COMPLETED, true)
            if (jormNode.getLong(KEY_INSTALL_TIMESTAMP, 0L) == 0L) {
                jormNode.putLong(KEY_INSTALL_TIMESTAMP, System.currentTimeMillis())
            }

            // Subnode for privacy policy & agreements
            val privacyNode = jormNode.node("privacy_policy")
            privacyNode.put(KEY_EUA_ACCEPTED_VERSION, CURRENT_EUA_VERSION)
            privacyNode.put(KEY_PRIVACY_POLICY_ACCEPTED_VERSION, CURRENT_EUA_VERSION)

            // Vendor-namespaced node
            val vendorNode = root.node(VENDOR_ROOT_NODE)
            vendorNode.put(KEY_VENDOR, VENDOR_NAME)
            vendorNode.put(KEY_APP_VERSION, CURRENT_APP_VERSION)
            vendorNode.put(KEY_EUA_ACCEPTED_VERSION, CURRENT_EUA_VERSION)

            jormNode.flush()
            vendorNode.flush()
            LOG.info("Jörmungandr isolated registry nodes initialized under '$ROOT_NODE' and '$VENDOR_ROOT_NODE'.")
        } catch (e: Exception) {
            LOG.warn("Failed to initialize Jörmungandr registry preferences", e)
        }
    }

    /**
     * Checks if the End User Agreement has been accepted for Jörmungandr.
     */
    fun isEuaAccepted(): Boolean {
        return try {
            val node = Preferences.userRoot().node(ROOT_NODE)
            node.get(KEY_EUA_ACCEPTED_VERSION, "") == CURRENT_EUA_VERSION
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Sets arbitrary preference key-value pair under Jörmungandr's isolated registry tree.
     */
    fun setPreference(key: String, value: String) {
        try {
            val node = Preferences.userRoot().node(ROOT_NODE)
            node.put(key, value)
            node.flush()
        } catch (e: Exception) {
            LOG.warn("Failed to write Jörmungandr preference '$key'", e)
        }
    }

    /**
     * Gets preference value from Jörmungandr's isolated registry tree.
     */
    fun getPreference(key: String, defaultValue: String = ""): String {
        return try {
            Preferences.userRoot().node(ROOT_NODE).get(key, defaultValue)
        } catch (e: Exception) {
            defaultValue
        }
    }
}
