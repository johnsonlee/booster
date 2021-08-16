package com.didiglobal.booster.gradle

import com.android.builder.model.Version
import com.android.repository.Revision
import java.util.ServiceLoader

internal val ANDROID_GRADLE_PLUGIN_VERSION: Revision = Revision.parseRevision(Version.ANDROID_GRADLE_PLUGIN_VERSION)
internal val MAJOR = ANDROID_GRADLE_PLUGIN_VERSION.major
internal val MINOR = ANDROID_GRADLE_PLUGIN_VERSION.minor

val GTE_V3_X = MAJOR >= 3
val GTE_V3_6 = MAJOR > 3 || (MAJOR == 3 && MINOR >= 6)
val GTE_V3_5 = MAJOR > 3 || (MAJOR == 3 && MINOR >= 5)
val GTE_V3_4 = MAJOR > 3 || (MAJOR == 3 && MINOR >= 4)
val GTE_V3_3 = MAJOR > 3 || (MAJOR == 3 && MINOR >= 3)
val GTE_V3_2 = MAJOR > 3 || (MAJOR == 3 && MINOR >= 2)
val GTE_V3_1 = MAJOR > 3 || (MAJOR == 3 && MINOR >= 1)

val GTE_V4_X = MAJOR >= 4
val GTE_V4_2 = MAJOR > 4 || (MAJOR == 4 && MINOR >= 2)
val GTE_V4_1 = MAJOR > 4 || (MAJOR == 4 && MINOR >= 1)

val GTE_V7_X = MAJOR >= 7

private val FACTORIES = ServiceLoader.load(AGPInterfaceFactory::class.java)
        .sortedByDescending(AGPInterfaceFactory::revision)
        .toList()

internal val AGP: AGPInterface by lazy {
    val factory = FACTORIES.first {
        it.revision.major == ANDROID_GRADLE_PLUGIN_VERSION.major
    } ?: FACTORIES.first()
    factory.newAGPInterface()
}

