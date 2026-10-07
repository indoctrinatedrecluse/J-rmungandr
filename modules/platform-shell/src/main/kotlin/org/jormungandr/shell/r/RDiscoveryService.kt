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

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Service responsible for detecting, probing, and managing local R installations.
 */
@Service(Service.Level.APP)
class RDiscoveryService {

    private val LOG = Logger.getInstance(RDiscoveryService::class.java)

    @Volatile
    private var cachedInfo: RInstallationInfo? = null

    @Volatile
    private var customExecutablePath: String? = null

    /**
     * Discovers or returns cached R installation information.
     */
    fun getInstallation(forceRefresh: Boolean = false): RInstallationInfo {
        if (!forceRefresh) {
            cachedInfo?.let { return it }
        }

        val discovered = discoverLocalInstallation()
        cachedInfo = discovered
        return discovered
    }

    /**
     * Manually overrides the R executable path (e.g. from user configuration dialog).
     */
    fun setCustomExecutable(path: String) {
        customExecutablePath = path.takeIf { it.isNotBlank() }
        cachedInfo = null
    }

    /**
     * Searches standard filesystem paths, environment variables, and PATH entries for R.
     */
    private fun discoverLocalInstallation(): RInstallationInfo {
        val candidates = mutableListOf<File>()

        // 1. User custom override
        customExecutablePath?.let { customPath ->
            val f = File(customPath)
            if (f.exists() && f.canExecute()) {
                candidates.add(f)
            }
        }

        // 2. R_HOME environment variable
        System.getenv("R_HOME")?.let { rHome ->
            candidates.addAll(findExecutablesUnderRHome(File(rHome)))
        }

        // 3. System PATH entries
        System.getenv("PATH")?.split(File.pathSeparator)?.forEach { pathDir ->
            val dir = File(pathDir)
            if (dir.isDirectory) {
                candidates.add(File(dir, "Rscript.exe"))
                candidates.add(File(dir, "R.exe"))
                candidates.add(File(dir, "Rscript"))
                candidates.add(File(dir, "R"))
            }
        }

        // 4. Standard Windows Installation Paths
        val progFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
        val rBaseDir = File(progFiles, "R")
        if (rBaseDir.isDirectory) {
            rBaseDir.listFiles()?.filter { it.isDirectory && it.name.startsWith("R-") }?.sortedDescending()?.forEach { versionDir ->
                candidates.addAll(findExecutablesUnderRHome(versionDir))
            }
        }

        // 5. Standard macOS / Linux paths
        candidates.add(File("/usr/bin/Rscript"))
        candidates.add(File("/usr/local/bin/Rscript"))
        candidates.add(File("/opt/homebrew/bin/Rscript"))

        // Find first executable candidate that exists
        val validExecutable = candidates.firstOrNull { it.exists() && it.isFile }
        if (validExecutable == null) {
            LOG.info("No native R installation detected on host system.")
            return RInstallationInfo.unavailable("R not detected on system PATH or Program Files.")
        }

        return probeExecutable(validExecutable)
    }

    private fun findExecutablesUnderRHome(rHome: File): List<File> {
        val list = mutableListOf<File>()
        list.add(File(rHome, "bin/x64/Rscript.exe"))
        list.add(File(rHome, "bin/Rscript.exe"))
        list.add(File(rHome, "bin/x64/R.exe"))
        list.add(File(rHome, "bin/R.exe"))
        list.add(File(rHome, "bin/Rscript"))
        list.add(File(rHome, "bin/R"))
        return list
    }

    /**
     * Probes the candidate executable to extract version and installed packages.
     */
    private fun probeExecutable(exe: File): RInstallationInfo {
        try {
            val rscript = if (exe.name.equals("R.exe", ignoreCase = true) || exe.name == "R") {
                val candidateRscript = File(exe.parentFile, if (exe.name.endsWith(".exe", ignoreCase = true)) "Rscript.exe" else "Rscript")
                if (candidateRscript.exists()) candidateRscript else exe
            } else {
                exe
            }

            val probeScript = "cat(R.version.string); cat(';;'); cat(paste(rownames(installed.packages()), collapse=','))"
            val process = ProcessBuilder(rscript.absolutePath, "-e", probeScript)
                .redirectErrorStream(false)
                .start()

            val stdout = process.inputStream.bufferedReader().readText().trim()
            val finished = process.waitFor(5, TimeUnit.SECONDS)

            if (!finished) {
                process.destroyForcibly()
                return RInstallationInfo.unavailable("R probe timed out.")
            }

            val parts = stdout.split(";;")
            val versionStr = parts.getOrNull(0)?.trim() ?: "R (detected at ${exe.path})"
            val packagesStr = parts.getOrNull(1)?.trim() ?: ""
            val packages = packagesStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

            val homeDir = exe.parentFile?.parentFile?.absolutePath ?: exe.parentFile?.absolutePath ?: ""
            val hasIRkernel = packages.contains("IRkernel")

            return RInstallationInfo(
                executablePath = rscript.absolutePath,
                homePath = homeDir,
                version = versionStr,
                installedPackages = packages,
                isIRkernelAvailable = hasIRkernel,
                isAvailable = true
            )
        } catch (e: Exception) {
            LOG.warn("Failed to probe R executable at ${exe.path}", e)
            return RInstallationInfo(
                executablePath = exe.absolutePath,
                homePath = exe.parentFile?.absolutePath ?: "",
                version = "R (probe error: ${e.message})",
                installedPackages = emptySet(),
                isIRkernelAvailable = false,
                isAvailable = true
            )
        }
    }

    companion object {
        @Volatile
        private var testFallback: RDiscoveryService? = null

        fun getInstance(): RDiscoveryService =
            com.intellij.openapi.application.ApplicationManager.getApplication()?.getService(RDiscoveryService::class.java)
                ?: (testFallback ?: RDiscoveryService().also { testFallback = it })
    }
}
