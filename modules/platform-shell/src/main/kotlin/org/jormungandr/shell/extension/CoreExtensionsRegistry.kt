package org.jormungandr.shell.extension

import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jormungandr.core.extension.ExtensionContext
import org.jormungandr.core.extension.ExtensionId
import org.jormungandr.core.extension.ExtensionManager
import org.jormungandr.core.extension.JormungandrExtension
import org.jormungandr.core.theme.ThemeManager
import org.jormungandr.shell.theme.ThemeExtension

/**
 * Standard implementation of [ExtensionContext] for shell-managed extensions.
 */
class ShellExtensionContext(
    override val extensionId: ExtensionId,
    override val scope: CoroutineScope,
    override val project: Project? = null
) : ExtensionContext {
    private val log = Logger.getInstance(ShellExtensionContext::class.java)

    override fun logInfo(message: String) = log.info("[${extensionId.value}] $message")
    override fun logWarn(message: String) = log.warn("[${extensionId.value}] $message")
    override fun logError(message: String, throwable: Throwable?) {
        if (throwable != null) log.error("[${extensionId.value}] $message", throwable)
        else log.error("[${extensionId.value}] $message")
    }
}

/**
 * Bootstraps and registers core modular extensions into [ExtensionManager].
 */
object CoreExtensionsRegistry {

    private val LOG = Logger.getInstance(CoreExtensionsRegistry::class.java)
    val EP_NAME = ExtensionPointName.create<JormungandrExtension>("org.jormungandr.ide.extension")

    /**
     * Ensures all built-in core extensions are registered and activated.
     */
    fun ensureCoreExtensionsRegistered(
        extensionManager: ExtensionManager,
        scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
    ) {
        val themeService = runCatching {
            (ApplicationManager.getApplication()?.getService(ThemeManager::class.java) as? ThemeExtension)
                ?: ApplicationManager.getApplication()?.getService(ThemeExtension::class.java)
        }.getOrNull() ?: ThemeExtension()

        val coreExtensions = mutableListOf<JormungandrExtension>()
        coreExtensions.add(themeService)

        // 1. Discover extensions registered via IntelliJ Extension Point
        val epExtensions = runCatching { EP_NAME.extensionList }.getOrElse { emptyList() }
        coreExtensions.addAll(epExtensions)

        // 2. Discover via dynamic plugin classloaders if not already registered
        val knownSubsystems = mapOf(
            "org.jormungandr.dataframe.DataFrameViewerExtension" to "org.jormungandr.dataframe",
            "org.jormungandr.database.DatabaseSuiteExtension" to "org.jormungandr.database",
            "org.jormungandr.jupyter.JupyterExtension" to "org.jormungandr.jupyter"
        )
        for ((fqcn, pluginIdStr) in knownSubsystems) {
            val alreadyPresent = coreExtensions.any { it.javaClass.name == fqcn }
            if (!alreadyPresent) {
                loadSubsystem(fqcn, pluginIdStr)?.let { coreExtensions.add(it) }
            }
        }

        for (ext in coreExtensions) {
            if (extensionManager.getExtension(ext.id) == null) {
                extensionManager.registerExtension(ext)
                LOG.info("Registered core extension: ${ext.metadata.displayName} (${ext.id.value})")

                scope.launch {
                    val context = ShellExtensionContext(
                        extensionId = ext.id,
                        scope = scope
                    )
                    runCatching {
                        extensionManager.initializeExtension(ext.id, context)
                        extensionManager.activateExtension(ext.id)
                    }.onFailure { err ->
                        LOG.warn("Failed to activate core extension: ${ext.id.value}", err)
                    }
                }
            }
        }
    }

    private fun loadSubsystem(className: String, pluginIdStr: String): JormungandrExtension? {
        return runCatching {
            val appCl = CoreExtensionsRegistry::class.java.classLoader
            val clazz = runCatching { Class.forName(className, true, appCl) }.getOrNull()
                ?: runCatching {
                    val pid = PluginId.getId(pluginIdStr)
                    val plugin = PluginManager.getInstance().findEnabledPlugin(pid)
                    plugin?.pluginClassLoader?.loadClass(className)
                }.getOrNull()
                ?: PluginManager.getLoadedPlugins().firstNotNullOfOrNull { p ->
                    runCatching { p.pluginClassLoader?.loadClass(className) }.getOrNull()
                }
            clazz?.getDeclaredConstructor()?.newInstance() as? JormungandrExtension
        }.getOrNull()
    }
}
