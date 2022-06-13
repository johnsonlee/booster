package com.didiglobal.booster.task.so

import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.BaseVariant
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
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.component.ProjectComponentIdentifier

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
            variant.dependencies.asSequence().map {
                it.id.componentIdentifier
            }.filterIsInstance<ProjectComponentIdentifier>().mapNotNull {
                project.rootProject.findProject(it.projectPath)
            }.filter {
                it.path != variant.project.path
            }.filter(Project::isAndroid).map {
                when (val android = it.getAndroid<BaseExtension>()) {
                    is LibraryExtension -> android.libraryVariants
                    else -> emptyList<BaseVariant>()
                }
            }.flatten().toList().forEach {
                listSharedLibrary.dependsOn(it.getTaskName("createFullJar"))
            }
        }
    }

}