package com.didiglobal.booster.gradle

import com.android.Version
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.Variant
import com.android.repository.Revision
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownDomainObjectException
import org.gradle.api.artifacts.ArtifactCollection
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.TaskProvider
import java.io.File
import java.util.ServiceLoader

interface AGPInterface {

    val revision: Revision
        get() = REVISION

    val Variant.project: Project



    fun Variant.getTaskName(prefix: String): String

    fun Variant.getTaskName(prefix: String, suffix: String): String

    val Variant.variantData: BaseVariantData

    @Deprecated(
            message = "Use BaseVariant.namespace instead",
            replaceWith = ReplaceWith(expression = "variant.namespace"),
    )
    val Variant.originalApplicationId: String

    val Variant.hasDynamicFeature: Boolean

    val Variant.rawAndroidResources: FileCollection

    val Variant.localAndroidResources: FileCollection

    val Variant.javaCompilerTaskProvider: TaskProvider<out Task>

    val Variant.preBuildTaskProvider: TaskProvider<out Task>

    val Variant.assembleTaskProvider: TaskProvider<out Task>

    val Variant.mergeAssetsTaskProvider: TaskProvider<out Task>

    val Variant.mergeResourcesTaskProvider: TaskProvider<out Task>

    val Variant.mergeNativeLibsTaskProvider: TaskProvider<out Task>

    val Variant.processJavaResourcesTaskProvider: TaskProvider<out Task>

    val Variant.allArtifacts: Map<String, FileCollection>

    val Variant.minSdkVersion: ApiVersion

    val Variant.targetSdkVersion: ApiVersion

    val Variant.isApplication: Boolean

    val Variant.isLibrary: Boolean

    val Variant.isDynamicFeature: Boolean

    val Variant.aar: FileCollection

    val Variant.apk: FileCollection

    val Variant.mergedManifests: FileCollection

    val Variant.mergedRes: FileCollection

    val Variant.mergedAssets: FileCollection

    val Variant.mergedNativeLibs: FileCollection

    val Variant.processedRes: FileCollection

    val Variant.symbolList: FileCollection

    val Variant.symbolListWithPackageName: FileCollection

    val Variant.dataBindingDependencyArtifacts: FileCollection

    val Variant.allClasses: FileCollection

    val Variant.buildTools: BuildToolInfo

    val Variant.isPrecompileDependenciesResourcesEnabled: Boolean

    fun Variant.getDependencies(
            transitive: Boolean = true,
            filter: (ComponentIdentifier) -> Boolean = { true }
    ): Collection<ResolvedArtifactResult>

}

inline fun <reified T : AndroidComponentsExtension<*, *, *>> Project.getAndroid(): T = extensions.getByType(AndroidComponentsExtension::class.java) as T

inline fun <reified T : AndroidComponentsExtension<*, *, *>> Project.getAndroidOrNull(): T? = try {
    extensions.getByType(AndroidComponentsExtension::class.java) as? T
} catch (e: UnknownDomainObjectException) {
    null
}

private val REVISION: Revision by lazy {
    Revision.parseRevision(Version.ANDROID_GRADLE_PLUGIN_VERSION)
}

private val FACTORIES: List<AGPInterfaceFactory> by lazy {
    ServiceLoader.load(AGPInterfaceFactory::class.java, AGPInterface::class.java.classLoader)
            .sortedByDescending(AGPInterfaceFactory::revision)
            .toList()
}

val AGP: AGPInterface by lazy {
    val factory = FACTORIES.firstOrNull {
        it.revision.major == REVISION.major && it.revision.minor == REVISION.minor
    } ?: FACTORIES.firstOrNull {
        it.revision.major == REVISION.major
    } ?: FACTORIES.first()
    factory.newAGPInterface()
}
