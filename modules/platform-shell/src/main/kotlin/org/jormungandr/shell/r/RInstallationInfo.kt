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

package org.jormungandr.shell.r

import java.io.File

/**
 * Metadata descriptor for a detected R programming language installation.
 */
data class RInstallationInfo(
    val executablePath: String,
    val homePath: String,
    val version: String,
    val installedPackages: Set<String>,
    val isIRkernelAvailable: Boolean,
    val isAvailable: Boolean
) {
    val majorMinorVersion: String
        get() {
            val match = Regex("""(\d+\.\d+(?:\.\d+)?)""").find(version)
            return match?.value ?: "Unknown"
        }

    val hasGgplot2: Boolean
        get() = installedPackages.contains("ggplot2")

    val hasDplyr: Boolean
        get() = installedPackages.contains("dplyr")

    val hasArrow: Boolean
        get() = installedPackages.contains("arrow")

    companion object {
        fun unavailable(message: String = "R runtime not detected"): RInstallationInfo {
            return RInstallationInfo(
                executablePath = "",
                homePath = "",
                version = message,
                installedPackages = emptySet(),
                isIRkernelAvailable = false,
                isAvailable = false
            )
        }

        fun simulated(): RInstallationInfo {
            return RInstallationInfo(
                executablePath = "Rscript (Simulated Studio)",
                homePath = "internal://r-runtime",
                version = "R version 4.4.1 (2024-06-14 ucrt) [Jörmungandr Simulated Engine]",
                installedPackages = setOf("ggplot2", "dplyr", "tidyr", "readr", "arrow", "tibble", "stringr", "forcats", "IRkernel"),
                isIRkernelAvailable = true,
                isAvailable = true
            )
        }
    }
}

/**
 * Result of an executed R script or REPL snippet.
 */
data class RReplExecutionResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int,
    val generatedPlotFiles: List<File>,
    val exportedCsvFiles: List<File>,
    val executionTimeMs: Long
) {
    val isSuccess: Boolean
        get() = exitCode == 0
}
