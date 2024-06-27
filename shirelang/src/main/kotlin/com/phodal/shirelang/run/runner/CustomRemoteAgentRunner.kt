package com.phodal.shirelang.run.runner

import com.intellij.execution.console.ConsoleViewWrapperBase
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.phodal.shirecore.agent.CustomAgent
import com.phodal.shire.agent.CustomAgentExecutor
import com.phodal.shirelang.ShireBundle
import com.phodal.shirelang.run.ShireConfiguration
import com.phodal.shirelang.run.flow.ShireConversationService
import com.phodal.shirelang.utils.ShireCoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class CustomRemoteAgentRunner(
    override val myProject: Project,
    override val configuration: ShireConfiguration,
    override val console: ConsoleViewWrapperBase,
    override val processHandler: ProcessHandler,
    override val prompt: String,
    private val agent: CustomAgent,
) : ShireRunner(configuration, processHandler, console, myProject, prompt) {
    override fun execute(postFunction: (response: String) -> Unit) {
        ApplicationManager.getApplication().invokeLater {
            val stringFlow: Flow<String>? = CustomAgentExecutor(project = myProject).execute(prompt, agent)

            if (stringFlow == null) {
                console.print(ShireBundle.message("shire.llm.notfound"), ConsoleViewContentType.ERROR_OUTPUT)
                processHandler.detachProcess()
                return@invokeLater
            }

            ShireCoroutineScope.scope(myProject).launch {
                val llmResult = StringBuilder()
                runBlocking {
                    stringFlow.collect {
                        llmResult.append(it)
                        console.print(it, ConsoleViewContentType.NORMAL_OUTPUT)
                    }
                }

                console.print("\nDone!", ConsoleViewContentType.SYSTEM_OUTPUT)
                val llmResponse = llmResult.toString()
                myProject.getService(ShireConversationService::class.java)
                    .refreshLlmResponseCache(configuration.getScriptPath(), llmResponse)

                postFunction(llmResponse)
                processHandler.detachProcess()
            }
        }
    }
}