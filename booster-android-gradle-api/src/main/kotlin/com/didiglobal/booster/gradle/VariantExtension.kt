package com.didiglobal.booster.gradle

import com.android.build.api.variant.Variant
import org.gradle.api.Task
import org.gradle.api.tasks.TaskProvider

fun Variant.createFullJarTaskProvider(): TaskProvider<out Task>? {
    return when (this) {
        is com.android.build.api.variant.LibraryVariant -> TODO()
        is com.android.build.api.variant.ApplicationVariant -> TODO()
        else -> throw IllegalArgumentException("Unsupported variant type: ${this::class.java.name}")
    }
}

fun Variant.bundleClassesTaskProvider(): TaskProvider<out Task>? {
    return when (this) {
        is com.android.build.api.variant.LibraryVariant -> TODO()
        is com.android.build.api.variant.ApplicationVariant -> TODO()
        else -> throw IllegalArgumentException("Unsupported variant type: ${this::class.java.name}")
    }
}
