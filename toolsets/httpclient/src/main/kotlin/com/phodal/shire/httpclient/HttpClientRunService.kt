package com.phodal.shire.httpclient

import com.intellij.execution.Executor
import com.intellij.execution.RunManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.configurations.RunProfile
import com.intellij.httpClient.http.request.run.HttpRequestExecutorExtensionFactory
import com.intellij.httpClient.http.request.run.HttpRequestRunConfigurationExecutor
import com.intellij.httpClient.http.request.run.config.HttpRequestRunConfiguration
import com.intellij.httpClient.http.request.run.config.HttpRequestRunConfigurationType
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchRootType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.phodal.shirecore.provider.RunService

class HttpClientRunService : RunService {
    override fun runConfigurationClass(project: Project): Class<out RunProfile>? {
        return HttpRequestRunConfiguration::class.java
    }

    override fun runFile(project: Project, virtualFile: VirtualFile, psiElement: PsiElement?): String? {
        val originFile: PsiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: return null
        val text = originFile.text

        // create temporary file for http request
        ScratchFileService.getInstance()
        val scratchFile = ScratchRootType.getInstance()
            .createScratchFile(project, "autodev-http-request.http", originFile.language, text) ?: return null

        val psiFile = PsiManager.getInstance(project).findFile(scratchFile) ?: return null

        val runner: RunnerAndConfigurationSettings = ConfigurationContext(psiFile)
            .configurationsFromContext
            ?.firstOrNull()
            ?.configurationSettings ?: return null


        val factory = HttpRequestRunConfigurationType.getInstance().configurationFactories[0]
        val configuration = HttpRequestRunConfiguration(project, factory, "HttpRequest")

        val runManager: RunManager = RunManager.getInstance(project)

        configuration.settings.filePath = virtualFile.path

        runManager.setUniqueNameIfNeeded(configuration)
        runner.isTemporary = true
        runManager.addConfiguration(runner)

        val selectedRunner = runManager.selectedConfiguration
        if ((selectedRunner == null || selectedRunner.isTemporary) && runManager.shouldSetRunConfigurationFromContext()) {
            runManager.selectedConfiguration = runner
        }

        val executor: Executor = HttpRequestExecutorExtensionFactory.getRunExtension().executor ?: return null
        HttpRequestRunConfigurationExecutor.getInstance().execute(
            project, runner, executor
        )

        return "Run Success"
    }
}
