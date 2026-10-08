/*
 * Copyright (c) 2025-2026 indoctrinatedrecluse
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jormungandr.core.license

import java.net.InetAddress
import java.security.MessageDigest

/**
 * Supported license tiers for Jörmungandr.
 */
enum class LicenseType(
    val prefix: String,
    val displayName: String,
    val description: String,
    val badgeTier: Int // 0: Trial, 1: User, 2: Dev, 3: Admin
) {
    ADMIN("ADM", "Administrator License", "Full root administrative & testing privileges", 3),
    DEVELOPER("DEV", "Developer Edition", "Internal developer, partner & QA privileges", 2),
    USER("USER", "Commercial User License", "Standard commercial subscriber / retail license", 1),
    TRIAL("TRIAL", "60-Day Evaluation Trial", "Evaluation trial mode (designed extensions gated)", 0);

    companion object {
        fun fromKey(key: String): LicenseType {
            val upper = key.trim().uppercase()
            return when {
                upper.startsWith("ADM-") -> ADMIN
                upper.startsWith("DEV-") -> DEVELOPER
                upper.startsWith("USER-") -> USER
                else -> TRIAL
            }
        }
    }
}

/**
 * Current operational status of a license.
 */
enum class LicenseStatus(val displayName: String) {
    ACTIVE("Active"),
    TRIAL("Trial Active"),
    EXPIRED("Expired"),
    SUSPENDED("Suspended"),
    REVOKED("Revoked"),
    INVALID("Invalid")
}

/**
 * Comprehensive snapshot of license state and machine binding.
 */
data class LicenseInfo(
    val licenseKey: String,
    val licenseType: LicenseType,
    val username: String,
    val hwid: String,
    val status: LicenseStatus,
    val activatedAt: Long,
    val expiresAt: Long?,
    val trialDaysRemaining: Int,
    val graceDaysLeft: Int? = null,
    val isActivated: Boolean,
    val message: String = ""
) {
    /** True if licensed under ANY of the 3 commercial tiers (USER, DEV, ADM) */
    val isFullyLicensed: Boolean
        get() = isActivated && status == LicenseStatus.ACTIVE && licenseType != LicenseType.TRIAL

    /** Returns masked version of key (e.g. USER-••••-••••-••••) */
    fun getMaskedKey(): String {
        if (licenseKey.isBlank() || licenseType == LicenseType.TRIAL) {
            return "NO-ACTIVE-LICENSE"
        }
        val parts = licenseKey.split("-")
        if (parts.size <= 1) return "••••••••••••••••"
        val prefix = parts[0]
        val masked = parts.drop(1).joinToString("-") { "••••" }
        return "$prefix-$masked"
    }
}

/**
 * Computes deterministic machine hardware fingerprint (HWID).
 */
object HwidGenerator {

    @Volatile
    private var cachedHwid: String? = null

    fun getHwid(): String {
        cachedHwid?.let { return it }

        val hostname = runCatching { InetAddress.getLocalHost().hostName }.getOrDefault(System.getenv("COMPUTERNAME") ?: "localhost")
        val osName = System.getProperty("os.name", "Windows")
        val osArch = System.getProperty("os.arch", "amd64")
        val userName = System.getProperty("user.name", "user")

        val raw = "$hostname-$osName-$osArch-$userName".lowercase()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02X".format(it) }

        // Formatted as HWID-XXXX-XXXX-XXXX
        val chunk1 = hex.substring(0, 4)
        val chunk2 = hex.substring(4, 8)
        val chunk3 = hex.substring(8, 12)
        val chunk4 = hex.substring(12, 16)
        val formatted = "HWID-$chunk1-$chunk2-$chunk3-$chunk4"

        cachedHwid = formatted
        return formatted
    }
}
