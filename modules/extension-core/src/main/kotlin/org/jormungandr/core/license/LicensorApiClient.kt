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

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

/**
 * High-performance REST client for the centralized Licensor service.
 * Base URL: https://licensor-h5zdysrkqa-uc.a.run.app
 */
class LicensorApiClient(
    val baseUrl: String = "https://licensor-h5zdysrkqa-uc.a.run.app",
    private val productId: String = "jormungandr"
) {

    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(6))
        .build()

    private val gson = Gson()

    /**
     * Checks if the online Licensor service is reachable and healthy.
     */
    fun checkHealth(): Boolean {
        return runCatching {
            val req = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/api/v1/health"))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build()
            val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
            resp.statusCode() == 200
        }.getOrDefault(false)
    }

    /**
     * Activates a license key for this machine HWID.
     * For ADM- licenses, username/password can be blank.
     */
    fun activate(
        licenseKey: String,
        username: String,
        password: String,
        hwid: String
    ): Result<LicenseInfo> {
        val trimmedKey = licenseKey.trim()
        val type = LicenseType.fromKey(trimmedKey)
        val isAdmin = type == LicenseType.ADMIN

        val effUsername = if (isAdmin && username.isBlank()) "admin" else username.trim()
        val effPassword = if (isAdmin && password.isBlank()) "" else password

        val payload = JsonObject().apply {
            addProperty("license_key", trimmedKey)
            addProperty("product_id", productId)
            addProperty("username", effUsername)
            if (effPassword.isNotBlank()) {
                addProperty("password", effPassword)
            }
            addProperty("hwid", hwid)
            addProperty("machine_name", System.getenv("COMPUTERNAME") ?: "Workstation")
            addProperty("platform", "windows-amd64")
            addProperty("app_version", "1.0.0")
        }

        return runCatching {
            val req = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/api/v1/license/activate"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(8))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build()

            val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
            val body = resp.body()

            if (resp.statusCode() == 200) {
                val json = gson.fromJson(body, JsonObject::class.java)
                val statusStr = json.get("status")?.asString ?: "active"
                val expiresAtStr = json.get("expires_at")?.takeIf { !it.isJsonNull }?.asString
                val expiresAtMillis = expiresAtStr?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }

                val licenseInfo = LicenseInfo(
                    licenseKey = trimmedKey,
                    licenseType = type,
                    username = effUsername,
                    hwid = hwid,
                    status = LicenseStatus.ACTIVE,
                    activatedAt = System.currentTimeMillis(),
                    expiresAt = expiresAtMillis,
                    trialDaysRemaining = 60,
                    isActivated = true,
                    message = json.get("message")?.asString ?: "Machine activated successfully"
                )
                licenseInfo
            } else {
                val errorMsg = runCatching {
                    gson.fromJson(body, JsonObject::class.java).get("error")?.asString
                }.getOrNull() ?: "Activation failed with HTTP ${resp.statusCode()}"
                throw IllegalArgumentException(errorMsg)
            }
        }
    }

    /**
     * Validates an active license against the server.
     */
    fun validate(
        licenseKey: String,
        username: String,
        hwid: String
    ): Result<LicenseInfo> {
        val trimmedKey = licenseKey.trim()
        val type = LicenseType.fromKey(trimmedKey)
        val isAdmin = type == LicenseType.ADMIN
        val effUsername = if (isAdmin && username.isBlank()) "admin" else username.trim()

        val payload = JsonObject().apply {
            addProperty("license_key", trimmedKey)
            addProperty("product_id", productId)
            addProperty("hwid", hwid)
            addProperty("username", effUsername)
        }

        return runCatching {
            val req = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/api/v1/license/validate"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(6))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build()

            val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
            val body = resp.body()

            if (resp.statusCode() == 200) {
                val json = gson.fromJson(body, JsonObject::class.java)
                val isValid = json.get("valid")?.asBoolean ?: false
                val statusStr = json.get("status")?.asString ?: "unknown"
                val message = json.get("message")?.asString ?: ""
                val expiresAtStr = json.get("expires_at")?.takeIf { !it.isJsonNull }?.asString
                val expiresAtMillis = expiresAtStr?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                val graceDays = json.get("grace_days_left")?.takeIf { !it.isJsonNull }?.asInt

                if (isValid) {
                    LicenseInfo(
                        licenseKey = trimmedKey,
                        licenseType = type,
                        username = effUsername,
                        hwid = hwid,
                        status = LicenseStatus.ACTIVE,
                        activatedAt = System.currentTimeMillis(),
                        expiresAt = expiresAtMillis,
                        trialDaysRemaining = 60,
                        graceDaysLeft = graceDays,
                        isActivated = true,
                        message = message
                    )
                } else {
                    val status = when (statusStr) {
                        "expired" -> LicenseStatus.EXPIRED
                        "suspended" -> LicenseStatus.SUSPENDED
                        "revoked" -> LicenseStatus.REVOKED
                        else -> LicenseStatus.INVALID
                    }
                    LicenseInfo(
                        licenseKey = trimmedKey,
                        licenseType = type,
                        username = effUsername,
                        hwid = hwid,
                        status = status,
                        activatedAt = 0L,
                        expiresAt = expiresAtMillis,
                        trialDaysRemaining = 0,
                        isActivated = false,
                        message = message
                    )
                }
            } else {
                val errorMsg = runCatching {
                    gson.fromJson(body, JsonObject::class.java).get("error")?.asString
                }.getOrNull() ?: "Validation failed (HTTP ${resp.statusCode()})"
                throw IllegalStateException(errorMsg)
            }
        }
    }

    /**
     * Deactivates a machine seat from the license key.
     */
    fun deactivate(
        licenseKey: String,
        username: String,
        password: String,
        hwid: String
    ): Result<Unit> {
        val trimmedKey = licenseKey.trim()
        val type = LicenseType.fromKey(trimmedKey)
        val isAdmin = type == LicenseType.ADMIN

        val payload = JsonObject().apply {
            addProperty("license_key", trimmedKey)
            addProperty("product_id", productId)
            addProperty("hwid", hwid)
            addProperty("username", if (isAdmin && username.isBlank()) "admin" else username.trim())
            if (password.isNotBlank()) {
                addProperty("password", password)
            }
        }

        return runCatching {
            val req = HttpRequest.newBuilder()
                .uri(URI.create("$baseUrl/api/v1/license/deactivate"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(6))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(payload)))
                .build()

            val resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() == 200) {
                Unit
            } else {
                val errorMsg = runCatching {
                    gson.fromJson(resp.body(), JsonObject::class.java).get("error")?.asString
                }.getOrNull() ?: "Deactivation failed (HTTP ${resp.statusCode()})"
                throw IllegalStateException(errorMsg)
            }
        }
    }
}
