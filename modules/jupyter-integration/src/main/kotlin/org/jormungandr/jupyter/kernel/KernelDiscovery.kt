package org.jormungandr.jupyter.kernel

import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.SystemInfo
import org.jormungandr.jupyter.model.JupyterKernelSpec
import java.io.File

private val LOG = logger<KernelDiscovery>()

/**
 * Discovers Jupyter kernel specifications registered on the local machine
 * (Python, Conda, Virtualenvs, Julia, R).
 */
object KernelDiscovery {

    /**
     * Discovers all available kernel specifications across standard system paths and virtualenvs.
     */
    fun discoverKernels(): List<JupyterKernelSpec> {
        val specs = mutableListOf<JupyterKernelSpec>()
        val searchDirs = getKernelSearchDirectories()

        for (dir in searchDirs) {
            if (!dir.exists() || !dir.isDirectory) continue
            val subdirs = dir.listFiles { f -> f.isDirectory } ?: continue
            for (subdir in subdirs) {
                val kernelJson = File(subdir, "kernel.json")
                if (kernelJson.exists() && kernelJson.isFile) {
                    runCatching {
                        parseKernelSpec(subdir.name, kernelJson)?.let { specs.add(it) }
                    }.onFailure { err ->
                        LOG.warn("Failed to parse kernel spec at ${kernelJson.absolutePath}: ${err.message}")
                    }
                }
            }
        }

        // Always ensure a default Python 3 kernel spec is present
        if (specs.none { it.language.equals("python", ignoreCase = true) }) {
            specs.add(createDefaultPythonSpec())
        }

        return specs.distinctBy { it.id }
    }

    /**
     * Finds the primary Python executable available on PATH or system directories.
     */
    fun findPythonExecutable(): String {
        val pathExts = if (SystemInfo.isWindows) listOf(".exe", ".bat", ".cmd", "") else listOf("")
        val rawPathDirs = (System.getenv("PATH") ?: "").split(File.pathSeparator)
        val pathDirs = rawPathDirs.sortedBy { if (it.contains("WindowsApps", ignoreCase = true)) 1 else 0 }

        val candidateNames = if (SystemInfo.isWindows) {
            listOf("python", "python3", "py")
        } else {
            listOf("python3", "python")
        }

        for (dir in pathDirs) {
            for (name in candidateNames) {
                for (ext in pathExts) {
                    val file = File(dir, "$name$ext")
                    if (file.exists() && file.canExecute()) {
                        return file.absolutePath
                    }
                }
            }
        }

        // Default fallbacks
        return if (SystemInfo.isWindows) "python.exe" else "python3"
    }

    /**
     * Checks if the `ipykernel` module is installed and runnable with the given python executable.
     */
    fun hasIpykernel(pythonExe: String = findPythonExecutable()): Boolean {
        return try {
            val process = ProcessBuilder(pythonExe, "-m", "ipykernel", "--version")
                .redirectErrorStream(true)
                .start()
            val completed = process.waitFor(3, java.util.concurrent.TimeUnit.SECONDS)
            completed && process.exitValue() == 0
        } catch (e: Exception) {
            false
        }
    }

    private fun createDefaultPythonSpec(): JupyterKernelSpec {
        val pyExe = findPythonExecutable()
        return JupyterKernelSpec(
            id = "python3",
            displayName = "Python 3 (Interactive)",
            language = "python",
            argv = listOf(pyExe, "-m", "ipykernel_launcher", "-f", "{connection_file}")
        )
    }

    private fun parseKernelSpec(dirName: String, file: File): JupyterKernelSpec? {
        val content = file.readText(Charsets.UTF_8)
        val root = JsonParser.parseString(content).asJsonObject

        val displayName = root.get("display_name")?.asString ?: dirName
        val language = root.get("language")?.asString ?: "python"
        val argv = mutableListOf<String>()
        if (root.has("argv") && root.get("argv").isJsonArray) {
            for (elem in root.getAsJsonArray("argv")) {
                argv.add(elem.asString)
            }
        }
        val interruptMode = root.get("interrupt_mode")?.asString ?: "signal"

        val envMap = mutableMapOf<String, String>()
        if (root.has("env") && root.get("env").isJsonObject) {
            for ((k, v) in root.getAsJsonObject("env").entrySet()) {
                envMap[k] = v.asString
            }
        }

        return JupyterKernelSpec(
            id = dirName,
            displayName = displayName,
            language = language,
            argv = argv,
            interruptMode = interruptMode,
            env = envMap
        )
    }

    private fun getKernelSearchDirectories(): List<File> {
        val dirs = mutableListOf<File>()
        val userHome = System.getProperty("user.home") ?: ""

        if (SystemInfo.isWindows) {
            val appData = System.getenv("APPDATA")
            val progData = System.getenv("PROGRAMDATA")
            if (!appData.isNullOrBlank()) dirs.add(File(appData, "jupyter/kernels"))
            if (!progData.isNullOrBlank()) dirs.add(File(progData, "jupyter/kernels"))
            if (userHome.isNotBlank()) {
                dirs.add(File(userHome, ".jupyter/kernels"))
                dirs.add(File(userHome, "anaconda3/share/jupyter/kernels"))
                dirs.add(File(userHome, "miniconda3/share/jupyter/kernels"))
                dirs.add(File(userHome, "miniforge3/share/jupyter/kernels"))
            }
        } else {
            if (userHome.isNotBlank()) {
                dirs.add(File(userHome, ".local/share/jupyter/kernels"))
                dirs.add(File(userHome, ".jupyter/kernels"))
            }
            dirs.add(File("/usr/local/share/jupyter/kernels"))
            dirs.add(File("/usr/share/jupyter/kernels"))
        }

        return dirs
    }
}
