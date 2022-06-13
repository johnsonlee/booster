package com.didiglobal.booster.task.so

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.LibraryVariant
import com.android.build.gradle.internal.tasks.factory.dependsOn
import com.didiglobal.booster.BOOSTER
import com.didiglobal.booster.gradle.dependencies
import com.didiglobal.booster.gradle.getAndroid
import com.didiglobal.booster.gradle.getTaskName
import com.didiglobal.booster.gradle.isAndroid
import com.didiglobal.booster.gradle.project
import com.didiglobal.booster.task.spi.VariantProcessor
import com.google.auto.service.AutoService
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.tasks.TaskProvider

private const val TASK_NAME = "listSharedLibraries"

@AutoService(VariantProcessor::class)
class ListSharedLibraryVariantProcessor : VariantProcessor {

    override fun process(variant: BaseVariant) {
        val tasks = variant.project.tasks
        val listSharedLibraries = try {
            tasks.named(TASK_NAME)
        } catch (e: UnknownTaskException) {
            tasks.register(TASK_NAME) {
                it.group = BOOSTER
                it.description = "List the shared libraries that current project depends on"
            }
        }
        val listSharedLibrary = tasks.register("list${variant.name.capitalize()}SharedLibraries", ListSharedLibrary::class.java) {
            it.group = BOOSTER
            it.description = "List the shared libraries that current project depends on for ${variant.name}"
            it.outputs.upToDateWhen { false }
            it.variant = variant
        }

        listSharedLibraries.dependsOn(listSharedLibrary)

        variant.project.afterEvaluate { project ->
            val prerequisites = variant.dependencies.asSequence().map {
                it.id.componentIdentifier
            }.filterIsInstance<ProjectComponentIdentifier>().mapNotNull { id ->
                project.rootProject.project(id.projectPath)
            }.filter(Project::isAndroid).map { p ->
                when (val android = p.getAndroid<BaseExtension>()) {
                    is LibraryExtension -> {
                        val variants = android.libraryVariants.filter {
                            it.name == variant.name
                        }.takeIf {
                            it.isNotEmpty()
                        } ?: android.libraryVariants.filter {
                            it.buildType.name == variant.buildType.name
                        }
                        variants.map {
                            p.tasks.named(it.getTaskName("createFullJar"))
                        }
                    }
                    else -> emptyList()
                }
            }.flatten().toList().toTypedArray()
            listSharedLibrary.dependsOn(*prerequisites)
        }
    }

}