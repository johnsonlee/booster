package com.didiglobal.booster.task.graph

import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.BaseVariant
import com.didiglobal.booster.gradle.dependencies
import com.didiglobal.booster.gradle.getAndroid
import com.didiglobal.booster.gradle.isAndroid
import com.didiglobal.booster.gradle.isJavaLibrary
import com.didiglobal.booster.gradle.project
import com.didiglobal.booster.graph.Edge
import com.didiglobal.booster.graph.Graph
import com.didiglobal.booster.graph.dot.DotGraph
import com.didiglobal.booster.kotlinx.file
import com.didiglobal.booster.task.spi.VariantProcessor
import com.google.auto.service.AutoService
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.plugins.JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME
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
    val graph = Graph.Builder<ProjectNode>().setTitle(project.toString())
    val stack = Stack<Project>().apply {
        add(project)
    }

    while (stack.isNotEmpty()) {
        val from = stack.pop()
        val deps = from.dependencies(this).onEach { to ->
            stack.push(to)
            graph.addEdge(ProjectNode(from.path), ProjectNode(to.path))
        }
        project.logger.info("#### $from => ${deps.joinToString(", ", "{", "}")}")
    }

    try {
        val dot = project.buildDir.file("dependencies.dot")
        DotGraph.DIGRAPH.visualize(graph.build(), dot)
    } catch (e: Throwable) {
        project.logger.error(e.message)
    }
}

private fun Project.dependencies(variant: BaseVariant): Set<Project> = when {
    isAndroid -> {
        when (val android = getAndroid<BaseExtension>()) {
            is AppExtension -> android.applicationVariants
            is LibraryExtension -> android.libraryVariants
            else -> emptyList<BaseVariant>()
        }.let { variants ->
            variants.filter {
                it.name == variant.name
            }.takeIf {
                it.isNotEmpty()
            } ?: variants.filter {
                it.buildType.name == variant.buildType.name
            }
        }.map(BaseVariant::dependencies).flatten().map {
            it.id.componentIdentifier
        }
    }
    isJavaLibrary -> {
        configurations.getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME).resolvedConfiguration.resolvedArtifacts.map {
            it.id.componentIdentifier
        }
    }
    else -> emptyList()
}.filterIsInstance<ProjectComponentIdentifier>().map {
    project.rootProject.project(it.projectPath)
}.toSet()