package com.didiglobal.booster.task.analyser.reference

import com.didiglobal.booster.graph.Graph
import com.didiglobal.booster.graph.dot.DotGraph
import com.didiglobal.booster.graph.json.JsonGraphRender
import com.didiglobal.booster.kotlinx.NCPU
import com.didiglobal.booster.kotlinx.touch
import com.didiglobal.booster.task.analyser.reference.reporting.ReferencePageRenderer
import com.didiglobal.booster.task.analyser.reference.reporting.ReferenceReports
import com.didiglobal.booster.task.analyser.reference.reporting.ReferenceReportsImpl
import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.reporting.Reporting
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.TaskAction
import org.gradle.reporting.HtmlReportRenderer
import org.gradle.util.ClosureBackedAction
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Analysing the class reference for current variant to determine each class in this module is referenced by which class in which module
 *
 * @author johnsonlee
 */
open class ReferenceAnalysisTask : DefaultTask(), Reporting<ReferenceReports> {

    @get:Internal
    lateinit var variant: String

    @get:Internal
    val _reports: ReferenceReports by lazy {
        project.objects.newInstance(ReferenceReportsImpl::class.java, this)
    }

    @TaskAction
    fun analyse() {
        val graph = ReferenceAnalyser(project, variant).analyse()
        val executor = Executors.newFixedThreadPool(reports.size.coerceAtMost(NCPU))

        try {
            arrayOf(::generateHtmlReport, ::generateDotReport, ::generateJsonReport).map { render ->
                executor.submit {
                    render(graph)
                }
            }.forEach {
                it.get()
            }
        } finally {
            executor.shutdown()
            executor.awaitTermination(1, TimeUnit.MINUTES)
        }
    }

    @Nested
    override fun getReports(): ReferenceReports = _reports

    override fun reports(closure: Closure<*>): ReferenceReports {
        return reports(ClosureBackedAction(closure))
    }

    override fun reports(configureAction: Action<in ReferenceReports>): ReferenceReports {
        configureAction.execute(_reports)
        return _reports
    }

    private fun generateDotReport(graph: Graph<ReferenceNode>) {
        if (!reports.dot.isEnabled) return

        try {
            val options = DotGraph.DotOptions(rankdir = "LR", format = "svg")
            DotGraph.DIGRAPH.visualize(graph, reports.dot.destination.touch(), options)
        } catch (e: Exception) {
            logger.error(e.message)
        }
    }

    private fun generateHtmlReport(graph: Graph<ReferenceNode>) {
        if (!reports.html.isEnabled) return
        HtmlReportRenderer().renderSinglePage(graph, ReferencePageRenderer(project, variant), reports.html.destination)
    }

    private fun generateJsonReport(graph: Graph<ReferenceNode>) {
        if (!reports.json.isEnabled) return

        val json = JsonGraphRender.render(graph) { node ->
            """{"component": "${node.component}", "variant": "${node.variant}", "class": "${node.klass}"}"""
        }.toString()
        reports.json.destination.touch().writeText(json)
    }

}
