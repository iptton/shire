package com.phodal.shirecore.agenttool.browse

import com.phodal.shirecore.agenttool.AgentToolContext
import com.phodal.shirecore.provider.agent.AgentTool
import com.phodal.shirecore.agenttool.AgentToolResult
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class BrowseTool : AgentTool {
    override val name: String get() = "Browse"
    override val description: String = "Get the content of a given URL."

    override fun execute(context: AgentToolContext): AgentToolResult {
        return AgentToolResult(
            isSuccess = true,
            output = parse(context.argument).body
        )
    }

    companion object {
        /**
         * Doc for parseHtml
         *
         * Intellij API: [com.intellij.inspectopedia.extractor.utils.HtmlUtils.cleanupHtml]
         */
        fun parse(url: String): DocumentContent {
            val doc: Document = Jsoup.connect(url).get()
            return DocumentCleaner().cleanHtml(doc)
        }
    }
}

