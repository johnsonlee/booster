package com.didiglobal.booster.task.analyser

import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.didiglobal.booster.gradle.getAndroid
import com.didiglobal.booster.gradle.getJarTaskProviders
import com.didiglobal.booster.gradle.getTaskName
import com.didiglobal.booster.gradle.getUpstreamProjects
import com.didiglobal.booster.gradle.isAndroid
import com.didiglobal.booster.gradle.isJava
import com.didiglobal.booster.gradle.isJavaLibrary
import com.didiglobal.booster.gradle.project
import com.didiglobal.booster.task.analyser.performance.PerformanceAnalysisTask
import com.didiglobal.booster.task.analyser.reference.ReferenceAnalysisTask
import com.didiglobal.booster.task.spi.VariantProcessor
import com.google.auto.service.AutoService
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.tasks.TaskProvider
import kotlin.reflect.KClass

@AutoService(VariantProcessor::class)
class AnalysisVariantProcessor : VariantProcessor {

    override fun process(variant: BaseVariant) {
        variant.project.gradle.projectsEvaluated { gradle ->
            gradle.rootProject.allprojects(Project::setup)
        }
    }

}

private fun Project.setup() {
    try {
        tasks.named(PerformanceAnalysisTask::class.taskName)
    } catch (e: UnknownTaskException) {
        when {
            isAndroid -> setupAndroid<PerformanceAnalysisTask>()
            else -> logger.warn("${PerformanceAnalysisTask::class.taskName} task is not supported for $this")
        }
    }

    try {
        tasks.named(ReferenceAnalysisTask::class.taskName)
    } catch (e: UnknownTaskException) {
        when {
            isAndroid -> setupAndroid<ReferenceAnalysisTask>()
            isJavaLibrary || isJava -> setupTasks<ReferenceAnalysisTask>()
            else -> logger.warn("${ReferenceAnalysisTask::class.taskName} task is not supported for $this")
        }
    }
}

private inline fun <reified T: AnalysisTask> Project.setupTasks(variant: BaseVariant? = null): TaskProvider<out Task> {
    val name = T::class.taskName
    return tasks.register(variant?.getTaskName(name) ?: name, T::class.java) {
        it.variant = variant
    }.dependsOn(getUpstreamProjects(false, variant).map { project ->
        project.getJarTaskProviders(variant)
    }.flatten() + getJarTaskProviders(variant))
}

private inline fun <reified T: AnalysisTask> Project.setupAndroid() {
    when (val android = getAndroid<BaseExtension>()) {
        is LibraryExtension -> android.libraryVariants
        is AppExtension -> android.applicationVariants
        else -> emptyList<BaseVariant>()
    }.map {
        setupTasks<T>(it)
    }
}

internal inline val <reified T: AnalysisTask> KClass<T>.category: String
    get() = T::class.java.simpleName.substringBefore(AnalysisTask::class.java.simpleName).toLowerCase()

internal inline val <reified T: AnalysisTask> KClass<T>.taskName: String
    get() = "analyse${category.capitalize()}"
