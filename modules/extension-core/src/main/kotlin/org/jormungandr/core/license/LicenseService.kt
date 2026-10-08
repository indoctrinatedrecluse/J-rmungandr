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

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Central licensing service governing license state, online validation,
 * and extension gating across Jörmungandr.
 */
@Service(Service.Level.APP)
class LicenseService(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {

    private val log = Logger.getInstance(LicenseService::class.java)
    private val apiClient = LicensorApiClient()
    val hwid: String = HwidGenerator.getHwid()

    private val _currentLicense = MutableStateFlow(LicensingRegistryStore.loadStoredLicense(hwid))
    val currentLicense: StateFlow<LicenseInfo> = _currentLicense.asStateFlow()

    private val listeners = CopyOnWriteArrayList<(LicenseInfo) -> Unit>()

    init {
        log.info("LicenseService initialized. Active HWID: $hwid, License: ${_currentLicense.value.licenseType} (${_currentLicense.value.status})")
        // Perform non-blocking background validation on launch if key is active
        if (_currentLicense.value.licenseKey.isNotBlank()) {
            scope.launch {
                validateCurrentLicense()
            }
        }
    }

    fun addListener(listener: (LicenseInfo) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (LicenseInfo) -> Unit) {
        listeners.remove(listener)
    }

    /**
     * Checks if Jörmungandr currently has a valid commercial license (USER, DEV, or ADM).
     */
    fun isLicensed(): Boolean {
        return _currentLicense.value.isFullyLicensed
    }

    /**
     * Returns true if the specified extension is unlocked.
     * Default IntelliJ plugins and third-party marketplace extensions are always free.
     * Designed Jörmungandr extensions require a valid commercial license.
     */
    fun isExtensionUnlocked(extensionId: String): Boolean {
        if (!isDesignedExtension(extensionId)) {
            return true
        }
        return isLicensed()
    }

    /**
     * Identifies proprietary Jörmungandr extensions.
     */
    fun isDesignedExtension(extensionId: String): Boolean {
        val lower = extensionId.lowercase()
        return lower.contains("org.jormungandr.jupyter") ||
                lower.contains("org.jormungandr.dataframe") ||
                lower.contains("org.jormungandr.database") ||
                lower.contains("duckdb") ||
                lower.contains("pipeline") ||
                lower.contains("lakehouse") ||
                lower.contains("copilot") ||
                lower.contains("telemetry")
    }

    /**
     * Activates a new license key via the Licensor backend.
     */
    suspend fun activateLicense(
        licenseKey: String,
        username: String,
        password: String
    ): Result<LicenseInfo> {
        val result = apiClient.activate(licenseKey, username, password, hwid)
        if (result.isSuccess) {
            val info = result.getOrThrow()
            LicensingRegistryStore.saveLicense(info)
            _currentLicense.value = info
            listeners.forEach { runCatching { it(info) } }
            log.info("License successfully activated: ${info.licenseType} for user ${info.username}")
        }
        return result
    }

    /**
     * Validates the currently registered license against the Licensor backend.
     */
    suspend fun validateCurrentLicense(): Result<LicenseInfo> {
        val current = _currentLicense.value
        if (current.licenseKey.isBlank()) {
            return Result.success(current)
        }

        val result = apiClient.validate(current.licenseKey, current.username, hwid)
        if (result.isSuccess) {
            val validated = result.getOrThrow()
            LicensingRegistryStore.saveLicense(validated)
            _currentLicense.value = validated
            listeners.forEach { runCatching { it(validated) } }
        } else {
            log.warn("License heartbeat validation failed: ${result.exceptionOrNull()?.message}")
        }
        return result
    }

    /**
     * Removes the current commercial license and reverts to the 60-day trial mode.
     */
    fun removeLicense(): LicenseInfo {
        val trialInfo = LicensingRegistryStore.removeLicense()
        _currentLicense.value = trialInfo
        listeners.forEach { runCatching { it(trialInfo) } }
        log.info("License removed. Reverted to trial mode (Days remaining: ${trialInfo.trialDaysRemaining})")
        return trialInfo
    }

    companion object {
        fun getInstance(): LicenseService {
            return ApplicationManager.getApplication()?.getService(LicenseService::class.java)
                ?: LicenseService()
        }
    }
}
