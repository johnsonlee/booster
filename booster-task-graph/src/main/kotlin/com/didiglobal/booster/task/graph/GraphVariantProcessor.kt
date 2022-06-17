package com.didiglobal.booster.task.graph

import com.android.build.gradle.api.BaseVariant
import com.didiglobal.booster.gradle.getResolvedArtifactResults
import com.didiglobal.booster.gradle.project
import com.didiglobal.booster.graph.Edge
import com.didiglobal.booster.graph.Graph
import com.didiglobal.booster.graph.dot.DotGraph
import com.didiglobal.booster.kotlinx.file
import com.didiglobal.booster.task.spi.VariantProcessor
import com.google.auto.service.AutoService
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import java.util.Stack

@AutoService(VariantProcessor::class)
class GraphVariantProcessor : VariantProcessor {

    override fun process(variant: BaseVariant) {
        val project = variant.project
        project.gradle.taskGraph.whenReady {
            variant.generateTaskGraph()
            variant.generateProjectGraph()
        }
    }

}

private fun BaseVariant.generateTaskGraph() {
    val taskNames = project.gradle.startParameter.taskNames
    val dot = project.rootProject.buildDir.file(name, "${taskNames.joinToString("-") { it.replace(":", "") }}.dot")
    val title = "./gradlew ${taskNames.joinToString(" ")}"
    val graph = project.gradle.taskGraph.allTasks.map { task ->
        task.taskDependencies.getDependencies(task).map { dep ->
            task to dep
        }
    }.flatten().map { (dep, task) ->
        Edge(TaskNode(dep.path), TaskNode(task.path))
    }.fold(Graph.Builder<TaskNode>().setTitle(title)) { builder, edge ->
        builder.addEdge(edge)
        builder
    }.build()

    try {
        DotGraph.DIGRAPH.visualize(graph, dot)
    } catch (e: Exception) {
        project.logger.error(e.message)
    }
}

private fun BaseVariant.generateProjectGraph() {
    val rootProject = project.rootProject
    val graph = Graph.Builder<ProjectNode>().setTitle(project.toString())
    val stack = Stack<ProjectNode>().apply {
        add(ProjectNode(project.path))
    }

    while (stack.isNotEmpty()) {
        val from = stack.pop()
        rootProject.project(from.path).getNoneTransitiveUpstreamProjects(this).filter { to ->
            !graph.hasEdge(from, to)
        }.takeIf(List<ProjectNode>::isNotEmpty)?.forEach { to ->
            stack.push(to)
            graph.addEdge(from, to)
        }
    }

    try {
        val dot = project.buildDir.file(name, "dependencies.dot")
        DotGraph.DIGRAPH.visualize(graph.build(), dot, DotGraph.DotOptions(format = "svg", rankdir = "LR"))
    } catch (e: Throwable) {
        project.logger.error(e.message)
    }
}

private fun Project.getNoneTransitiveUpstreamProjects(variant: BaseVariant): Set<ProjectNode> {
    return getResolvedArtifactResults(false, variant).map {
        it.id.componentIdentifier
    }.filterIsInstance<ProjectComponentIdentifier>().map {
        ProjectNode(it.projectPath)
    }.toSet()
}