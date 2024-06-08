package com.phodal.shirecore.runner

import com.intellij.execution.ExecutionException
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunnerAndConfigurationSettings
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.runners.ExecutionEnvironmentBuilder
import com.intellij.execution.runners.ProgramRunner
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsAdapter
import com.intellij.execution.testframework.sm.runner.SMTRunnerEventsListener
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.messages.MessageBusConnection
import java.util.concurrent.TimeUnit

interface ConfigurationRunner {
    fun runnerId() = DefaultRunExecutor.EXECUTOR_ID
    /**
     * This function defines a process run completion action to be executed once a process run by the program runner completes.
     * It is designed to handle the aftermath of a process execution, including stopping the process and notifying the run context.
     *
     * @param runContext The context in which the run operation is being executed. It provides the necessary information
     *                   and handles to manage the run process, including a latch to synchronize the completion of the run.
     *                   The run context is also responsible for disposing of resources once the run completes.
     *
     * Note: This function uses the 'return@Callback' syntax to exit the lambda expression early in case of a null descriptor.
     */
    fun processRunCompletionAction(runContext: RunContext) = ProgramRunner.Callback { descriptor ->
        // Descriptor can be null in some cases.
        // For example, IntelliJ Rust's test runner provides null here if compilation fails
        if (descriptor == null) {
            runContext.latch.countDown()
            return@Callback
        }

        Disposer.register(runContext) {
            ExecutionManagerImpl.stopProcess(descriptor)
        }
        val processHandler = descriptor.processHandler
        if (processHandler != null) {
            processHandler.addProcessListener(object : ProcessAdapter() {
                override fun processTerminated(event: ProcessEvent) {
                    runContext.latch.countDown()
                }
            })
            runContext.processListener?.let {
                processHandler.addProcessListener(it)
            }
        }
    }

    @Throws(ExecutionException::class)
    fun RunnerAndConfigurationSettings.startRunConfigurationExecution(runContext: RunContext): Boolean {
        val runner = ProgramRunner.getRunner(runnerId(), configuration)
        val env =
            ExecutionEnvironmentBuilder.create(DefaultRunExecutor.getRunExecutorInstance(), this)
                .activeTarget()
                .build(processRunCompletionAction(runContext))

        if (runner == null || env.state == null) {
            runContext.latch.countDown()
            return false
        }

        runContext.environments.add(env)
        runner.execute(env)
        return true
    }

    fun executeRunConfigurations(
        connection: MessageBusConnection,
        configurations: RunnerAndConfigurationSettings,
        runContext: RunContext,
        testEventsListener: SMTRunnerEventsListener?,
        indicator: ProgressIndicator?,
    ) {
        testEventsListener?.let {
            connection.subscribe(SMTRunnerEventsListener.TEST_STATUS, it)
        }
        Disposer.register(connection, runContext)

        runInEdt {
            connection.subscribe(
                ExecutionManager.EXECUTION_TOPIC,
                CheckExecutionListener(runnerId(), runContext)
            )

            try {
                configurations.startRunConfigurationExecution(runContext)
            } catch (e: ExecutionException) {
                runContext.latch.countDown()
            }
        }

        while (indicator?.isCanceled != true) {
            val result = runContext.latch.await(100, TimeUnit.MILLISECONDS)
            if (result) break
        }

        if (indicator?.isCanceled == true) {
            Disposer.dispose(runContext)
        }
    }

    fun executeRunConfigures(
        project: Project,
        settings: RunnerAndConfigurationSettings,
        runContext: RunContext,
        testEventsListener: SMTRunnerEventsAdapter,
        indicator: ProgressIndicator?,
    ) {
        val connection = project.messageBus.connect()
        try {
            return executeRunConfigurations(connection, settings, runContext, testEventsListener, indicator)
        } finally {
            connection.disconnect()
        }
    }
}

