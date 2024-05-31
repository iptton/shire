package com.phodal.shirelang.compiler.exec

import com.phodal.shirelang.compiler.error.SHIRE_ERROR
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.phodal.shirelang.completion.dataprovider.FileFunc
import com.phodal.shirelang.utils.canBeAdded

class FileFuncInsCommand(val myProject: Project, val prop: String) : InsCommand {
    override suspend fun execute(): String? {
        val (functionName, args) = parseRegex(prop)
            ?: return """$SHIRE_ERROR: file-func is not in the format @file-func:<functionName>(<arg1>, <arg2>, ...)
            |Example: @file-func:regex(".*\.kt")
        """.trimMargin()

        val fileFunction = FileFunc.fromString(functionName) ?: return "$SHIRE_ERROR: Unknown function: $functionName"
        when (fileFunction) {
            FileFunc.Regex -> {
                try {
                    val regex = Regex(args[0])
                    return regexFunction(regex, myProject).joinToString(", ")
                } catch (e: Exception) {
                    return SHIRE_ERROR + ": ${e.message}"
                }
            }
        }
    }

    private fun regexFunction(regex: Regex, project: Project): MutableList<VirtualFile> {
        val files: MutableList<VirtualFile> = mutableListOf()
        ProjectFileIndex.getInstance(project).iterateContent {
            if (it.canBeAdded()) {
                if (regex.matches(it.path)) {
                    files.add(it)
                }
            }

            true
        }
        return files
    }

    companion object {
        /**
         * Parses a given property string to extract the function name and its arguments.
         *
         * The property string is in the format <functionName>(<arg1>, <arg2>, ...).
         *
         * @param prop The property string to parse.
         * @return The function name and the list of arguments as a Pair object.
         * @throws IllegalArgumentException if the property string has invalid regex pattern.
         */
        fun parseRegex(prop: String): Pair<String, List<String>>? {
            val regexPattern = Regex("""(\w+)\(([^)]+)\)""")
            val matchResult = regexPattern.find(prop)

            if (matchResult != null && matchResult.groupValues.size == 3) {
                val functionName = matchResult.groupValues[1]
                val args = matchResult.groupValues[2].split(',').map { it.trim() }
                return Pair(functionName, args)
            } else {
                return null
            }
        }
    }
}
