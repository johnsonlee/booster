package com.didiglobal.booster.task.graph

import com.android.build.api.variant.Variant
import com.didiglobal.booster.gradle.filterByNameOrBuildType
import com.didiglobal.booster.gradle.getUpstreamProjects
import com.didiglobal.booster.graph.Graph
import com.didiglobal.booster.graph.dot.DotGraph
import com.didiglobal.booster.kotlinx.file
import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import java.util.Stack

abstract class GenerateProjectGraph : DefaultTask() {
    @get:Internal
    abstract val variant: Property<Variant>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val inputJars: ListProperty<RegularFile>

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDirectories: ListProperty<Directory>

    @TaskAction
    fun generate() {
        val rootProject = project.rootProject
        val filter = variant.get().filterByNameOrBuildType()
        val graph = Graph.Builder<ProjectNode>().setTitle(project.toString())
        val stack = Stack<ProjectNode>().apply {
            add(ProjectNode(project.path))
        }

        while (stack.isNotEmpty()) {
            val from = stack.pop()
            rootProject.project(from.path).getUpstreamProjects(false, filter).map {
                ProjectNode(it.path)
            }.filter { to ->
                !graph.hasEdge(from, to)
            }.takeIf(List<ProjectNode>::isNotEmpty)?.forEach { to ->
                stack.push(to)
                graph.addEdge(from, to)
            }
        }

        try {
            val dot = project.buildDir.file(variant.get().name, "dependencies.dot")
            DotGraph.DIGRAPH.visualize(graph.build(), dot, DotGraph.DotOptions(format = "svg", rankdir = "LR"))
        } catch (e: Throwable) {
            project.logger.error(e.message)
        }
    }
}