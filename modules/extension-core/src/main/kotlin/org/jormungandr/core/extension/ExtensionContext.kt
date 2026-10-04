package org.jormungandr.core.extension

import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

/**
 * Execution environment and services provided to an extension on initialization.
 */
interface ExtensionContext {
    val project: Project?
    val scope: CoroutineScope
    val extensionId: ExtensionId

    fun logInfo(message: String)
    fun logWarn(message: String)
    fun logError(message: String, throwable: Throwable? = null)
}
