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

package org.jormungandr.shell.license

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jormungandr.core.license.LicenseService

/**
 * Menu action to open the LicenseDialog modal.
 */
class ManageLicenseAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        LicenseDialog(project).show()
    }

    override fun update(e: AnActionEvent) {
        val current = LicenseService.getInstance().currentLicense.value
        e.presentation.text = "Manage Jörmungandr License [${current.licenseType.name}]..."
        e.presentation.description = "View, activate, or manage Jörmungandr commercial license"
        e.presentation.isEnabledAndVisible = true
    }
}
