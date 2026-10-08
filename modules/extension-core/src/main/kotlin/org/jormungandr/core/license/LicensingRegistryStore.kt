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

import java.io.BufferedReader
import java.io.InputStreamReader
import java.security.MessageDigest
import java.util.prefs.Preferences
import kotlin.math.max

/**
 * Manages persistent storage of license and trial data in the Windows Registry
 * (`HKCU\Software\Jormungandr\Licensing`) and Java Preferences.
 *
 * Designed to strictly persist trial timestamps and prevent unauthorized trial resets.
 */
object LicensingRegistryStore {

    private const val PREF_NODE = "jormungandr/licensing"
    private const val REG_PATH = """HKCU\Software\Jormungandr\Licensing"""

    private const val KEY_LICENSE_KEY = "LicenseKey"
    private const val KEY_LICENSE_TYPE = "LicenseType"
    private const val KEY_USERNAME = "Username"
    private const val KEY_HWID = "Hwid"
    private const val KEY_STATUS = "Status"
    private const val KEY_ACTIVATED_AT = "ActivatedAt"
    private const val KEY_EXPIRES_AT = "ExpiresAt"
    private const val KEY_TRIAL_FIRST_ACTIVATED = "TrialFirstActivatedAt"
    private const val KEY_TRIAL_CHECKSUM = "TrialChecksum"
    private const val KEY_LAST_VALIDATED = "LastValidatedAt"

    private const val TRIAL_DURATION_DAYS = 60
    private const val TRIAL_SALT = "JORMUNGANDR-INDOCTRINATEDRECLUSE-2025-2026-PERSISTENT-TRIAL-ANCHOR"

    private val isWindows = System.getProperty("os.name", "").lowercase().contains("windows")

    /**
     * Reads the current persistent license state from registry.
     */
    fun loadStoredLicense(hwid: String): LicenseInfo {
        val pref = Preferences.userRoot().node(PREF_NODE)

        // Read trial baseline
        var trialStart = readLong(pref, KEY_TRIAL_FIRST_ACTIVATED, 0L)
        var checksum = readString(pref, KEY_TRIAL_CHECKSUM, "")

        val now = System.currentTimeMillis()

        // If trial not initialized yet, initialize it now
        if (trialStart == 0L || !verifyChecksum(hwid, trialStart, checksum)) {
            trialStart = now
            checksum = computeChecksum(hwid, trialStart)
            writeLong(pref, KEY_TRIAL_FIRST_ACTIVATED, trialStart)
            writeString(pref, KEY_TRIAL_CHECKSUM, checksum)
        }

        // Check if user rolled back clock
        val effectiveTrialStart = if (now < trialStart) now else trialStart
        val elapsedMillis = max(0L, now - effectiveTrialStart)
        val elapsedDays = (elapsedMillis / (1000L * 60 * 60 * 24)).toInt()
        val trialDaysRemaining = max(0, TRIAL_DURATION_DAYS - elapsedDays)

        val storedKey = readString(pref, KEY_LICENSE_KEY, "").trim()
        val storedUsername = readString(pref, KEY_USERNAME, "").trim()
        val storedTypeStr = readString(pref, KEY_LICENSE_TYPE, LicenseType.TRIAL.name)
        val storedStatusStr = readString(pref, KEY_STATUS, "")
        val activatedAt = readLong(pref, KEY_ACTIVATED_AT, 0L)
        val expiresAtVal = readLong(pref, KEY_EXPIRES_AT, 0L)
        val expiresAt = if (expiresAtVal > 0L) expiresAtVal else null

        if (storedKey.isNotBlank()) {
            val type = LicenseType.fromKey(storedKey)
            val isExpired = expiresAt != null && now > expiresAt
            val status = if (isExpired) {
                LicenseStatus.EXPIRED
            } else if (storedStatusStr.isNotBlank()) {
                runCatching { LicenseStatus.valueOf(storedStatusStr) }.getOrDefault(LicenseStatus.ACTIVE)
            } else {
                LicenseStatus.ACTIVE
            }

            return LicenseInfo(
                licenseKey = storedKey,
                licenseType = type,
                username = storedUsername,
                hwid = hwid,
                status = status,
                activatedAt = activatedAt,
                expiresAt = expiresAt,
                trialDaysRemaining = trialDaysRemaining,
                isActivated = status == LicenseStatus.ACTIVE,
                message = if (status == LicenseStatus.ACTIVE) "License activated and valid" else "License $status"
            )
        }

        // No commercial key present -> Trial mode
        val trialStatus = if (trialDaysRemaining > 0) LicenseStatus.TRIAL else LicenseStatus.EXPIRED
        return LicenseInfo(
            licenseKey = "",
            licenseType = LicenseType.TRIAL,
            username = System.getProperty("user.name", "Trial User"),
            hwid = hwid,
            status = trialStatus,
            activatedAt = trialStart,
            expiresAt = trialStart + (TRIAL_DURATION_DAYS * 24L * 60 * 60 * 1000),
            trialDaysRemaining = trialDaysRemaining,
            isActivated = false,
            message = if (trialDaysRemaining > 0) "Evaluation trial active ($trialDaysRemaining days remaining)" else "Evaluation trial has expired"
        )
    }

    /**
     * Saves an active commercial license into the persistent registry.
     */
    fun saveLicense(info: LicenseInfo) {
        val pref = Preferences.userRoot().node(PREF_NODE)
        writeString(pref, KEY_LICENSE_KEY, info.licenseKey)
        writeString(pref, KEY_LICENSE_TYPE, info.licenseType.name)
        writeString(pref, KEY_USERNAME, info.username)
        writeString(pref, KEY_HWID, info.hwid)
        writeString(pref, KEY_STATUS, info.status.name)
        writeLong(pref, KEY_ACTIVATED_AT, info.activatedAt)
        writeLong(pref, KEY_EXPIRES_AT, info.expiresAt ?: 0L)
        writeLong(pref, KEY_LAST_VALIDATED, System.currentTimeMillis())
        pref.flush()
    }

    /**
     * Removes the active commercial license and reverts to the trial mode,
     * maintaining the initial trial start timestamp to prevent trial reset.
     */
    fun removeLicense(): LicenseInfo {
        val pref = Preferences.userRoot().node(PREF_NODE)
        writeString(pref, KEY_LICENSE_KEY, "")
        writeString(pref, KEY_LICENSE_TYPE, LicenseType.TRIAL.name)
        writeString(pref, KEY_USERNAME, "")
        writeString(pref, KEY_STATUS, LicenseStatus.TRIAL.name)
        writeLong(pref, KEY_ACTIVATED_AT, 0L)
        writeLong(pref, KEY_EXPIRES_AT, 0L)
        pref.flush()

        // Also delete from Windows registry key directly
        if (isWindows) {
            runRegDelete(KEY_LICENSE_KEY)
            runRegDelete(KEY_LICENSE_TYPE)
            runRegDelete(KEY_USERNAME)
            runRegDelete(KEY_STATUS)
            runRegDelete(KEY_ACTIVATED_AT)
            runRegDelete(KEY_EXPIRES_AT)
        }

        return loadStoredLicense(HwidGenerator.getHwid())
    }

    private fun computeChecksum(hwid: String, trialStart: Long): String {
        val raw = "$hwid:$trialStart:$TRIAL_SALT"
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02X".format(it) }
    }

    private fun verifyChecksum(hwid: String, trialStart: Long, checksum: String): Boolean {
        if (checksum.isBlank()) return false
        return computeChecksum(hwid, trialStart).equals(checksum, ignoreCase = true)
    }

    private fun readString(pref: Preferences, key: String, defaultVal: String): String {
        // Try native Windows registry first
        if (isWindows) {
            val regVal = runRegQuery(key)
            if (!regVal.isNullOrBlank()) return regVal
        }
        return pref.get(key, defaultVal)
    }

    private fun readLong(pref: Preferences, key: String, defaultVal: Long): Long {
        if (isWindows) {
            val regVal = runRegQuery(key)
            if (!regVal.isNullOrBlank()) {
                regVal.toLongOrNull()?.let { return it }
            }
        }
        return pref.getLong(key, defaultVal)
    }

    private fun writeString(pref: Preferences, key: String, value: String) {
        pref.put(key, value)
        if (isWindows) {
            runRegAdd(key, value)
        }
    }

    private fun writeLong(pref: Preferences, key: String, value: Long) {
        pref.putLong(key, value)
        if (isWindows) {
            runRegAdd(key, value.toString())
        }
    }

    private fun runRegQuery(valueName: String): String? {
        return runCatching {
            val process = ProcessBuilder("reg", "query", REG_PATH, "/v", valueName)
                .redirectErrorStream(true)
                .start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            var result: String? = null
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line!!.trim()
                if (trimmed.startsWith(valueName)) {
                    val parts = trimmed.split(Regex("""\s+"""), limit = 3)
                    if (parts.size >= 3) {
                        result = parts[2]
                    }
                }
            }
            process.waitFor()
            result
        }.getOrNull()
    }

    private fun runRegAdd(valueName: String, valueData: String) {
        runCatching {
            ProcessBuilder("reg", "add", REG_PATH, "/v", valueName, "/t", "REG_SZ", "/d", valueData, "/f")
                .redirectErrorStream(true)
                .start()
                .waitFor()
        }
    }

    private fun runRegDelete(valueName: String) {
        runCatching {
            ProcessBuilder("reg", "delete", REG_PATH, "/v", valueName, "/f")
                .redirectErrorStream(true)
                .start()
                .waitFor()
        }
    }
}
