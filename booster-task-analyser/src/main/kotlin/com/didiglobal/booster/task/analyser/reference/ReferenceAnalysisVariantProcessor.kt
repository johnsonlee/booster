package com.didiglobal.booster.task.analyser.reference

import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.didiglobal.booster.BOOSTER
import com.didiglobal.booster.gradle.getAndroid
import com.didiglobal.booster.gradle.getTaskName
import com.didiglobal.booster.gradle.isAndroid
import com.didiglobal.booster.gradle.isJava
import com.didiglobal.booster.gradle.isJavaLibrary
import com.didiglobal.booster.gradle.javaCompilerTaskProvider
import com.didiglobal.booster.gradle.project
import com.didiglobal.booster.kotlinx.file
import com.didiglobal.booster.task.analyser.Build
import com.didiglobal.booster.task.spi.VariantProcessor
import com.google.auto.service.AutoService
import org.gradle.api.Project
import org.gradle.api.UnknownTaskException
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.TaskProvider
import java.io.File

@AutoService(VariantProcessor::class)
class ReferenceAnalysisVariantProcessor : VariantProcessor {

    override fun process(variant: BaseVariant) {
        variant.project.gradle.projectsEvaluated { gradle ->
            val prerequisites = gradle.rootProject.allprojects.map(Project::prerequisites).flatten()
            gradle.rootProject.allprojects { project ->
                project.setup(prerequisites)
            }
        }
    }

}

private fun Project.setup(prerequisites: List<TaskProvider<*>>) {
    try {
        project.tasks.named(TASK_ANALYSE_REFERENCE)
    } catch (e: UnknownTaskException) {
        when {
            isAndroid -> setupAndroid(prerequisites)
            isJavaLibrary || isJava -> setupJava(prerequisites)
        }
    }
}

private fun Project.setupJava(prerequisites: List<TaskProvider<*>>) {
    tasks.register(TASK_ANALYSE_REFERENCE, ReferenceAnalysisTask::class.java) {
        it.group = BOOSTER
        it.description = "Analyses class reference for Java projects"
        it.variant = DEFAULT_VARIANT
        it.configureReportConvention(DEFAULT_VARIANT)
    }.dependsOn(prerequisites)
}

private fun Project.setupAndroid(prerequisites: List<TaskProvider<*>>) {
    val subtasks = when (val android = getAndroid<BaseExtension>()) {
        is LibraryExtension -> android.libraryVariants
        is AppExtension -> android.applicationVariants
        else -> emptyList<BaseVariant>()
    }.map { variant ->
        tasks.register("${TASK_ANALYSE_REFERENCE}${variant.name.capitalize()}", ReferenceAnalysisTask::class.java) {
            it.group = BOOSTER
            it.description = "Analyses class reference for ${variant.name}"
            it.variant = variant.name
            it.configureReportConvention(variant.name)
        }.dependsOn(prerequisites)
    }
    tasks.register(TASK_ANALYSE_REFERENCE) {
        it.group = BOOSTER
        it.description = "Analyses class reference"
    }.dependsOn(subtasks)
}

private fun ReferenceAnalysisTask.configureReportConvention(variant: String) {
    reports.all {
        it.outputLocation.convention(project.layout.projectDirectory.file(project.provider<String> {
            val base = project.extensions.getByType(ReportingExtension::class.java).baseDir
            base.file(Build.ARTIFACT, "reference", variant, it.name, "index.${it.name}").absolutePath
        }))
    }
}

/**
 * Prerequisite tasks for reference analysing
 */
private val Project.prerequisites: List<TaskProvider<*>>
    get() = when {
        isAndroid -> when (val android = getAndroid<BaseExtension>()) {
            is LibraryExtension -> android.libraryVariants.map { v ->
                tasks.named(v.getTaskName(TASK_CREATE_FULL_JAR))
            }
            is AppExtension -> android.applicationVariants.map { v ->
                v.javaCompilerTaskProvider
            }
            else -> emptyList()
        }
        isJavaLibrary -> listOf(tasks.named(JavaPlugin.JAR_TASK_NAME))
        else -> emptyList()
    }
