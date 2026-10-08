package org.jormungandr.shell.copilot.local

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.Messages
import javax.swing.SwingUtilities

/**
 * Contextual action to auto-generate data science docstring for selected code or current function.
 */
class GenerateDocstringAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: return
        val selectionModel = editor.selectionModel
        val selectedText = selectionModel.selectedText ?: editor.document.text.take(300)

        val prompt = "Generate a Google-style Python docstring detailing parameters, types, returns, and raises for this data pipeline function:\n\n$selectedText"
        val system = "You are a senior data engineer. Output only the Python docstring enclosed in triple quotes."

        val ollamaService = OllamaService.getInstance()
        val models = ollamaService.listLocalModels()
        val model = models.firstOrNull()?.name ?: "qwen2.5-coder:7b"

        val result = ollamaService.generate(model, prompt, system, temperature = 0.1)

        WriteCommandAction.runWriteCommandAction(project) {
            val offset = if (selectionModel.hasSelection()) selectionModel.selectionStart else editor.caretModel.offset
            editor.document.insertString(offset, "\n" + result.responseText.trim() + "\n")
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.EDITOR) != null
    }
}

/**
 * Contextual action to auto-generate pytest unit tests and synthetic DataFrame mock fixtures.
 */
class GeneratePytestAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val project = e.project ?: return
        val selectionModel = editor.selectionModel
        val selectedText = selectionModel.selectedText ?: editor.document.text.take(300)

        val prompt = "Generate a complete pytest unit test suite with synthetic Pandas DataFrame fixtures for this data pipeline code:\n\n$selectedText"
        val system = "You are a principal QA data engineer. Write robust pytest test cases with fixtures and edge assertions."

        val ollamaService = OllamaService.getInstance()
        val models = ollamaService.listLocalModels()
        val model = models.firstOrNull()?.name ?: "deepseek-coder:6.7b"

        val result = ollamaService.generate(model, prompt, system, temperature = 0.2)

        WriteCommandAction.runWriteCommandAction(project) {
            val offset = if (selectionModel.hasSelection()) selectionModel.selectionEnd else editor.document.textLength
            editor.document.insertString(offset, "\n\n# === Auto-Generated PyTest Suite ===\n" + result.responseText.trim() + "\n")
        }
        Messages.showInfoMessage(project, "PyTest test suite generated and appended to file (${result.tokenCount} tokens generated).", "Tests Generated")
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.getData(CommonDataKeys.EDITOR) != null
    }
}
