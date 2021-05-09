package com.didiglobal.booster.task.analyser

private const val DOLLAR = '$'

/**
 * The following classes exclude from lint
 *
 * - `kotlin.**`
 * - `android.**`
 * - `androidx.**`
 * - `com.android.**`
 * - `com.google.android.**`
 * - `com.google.gson.**`
 * - `com.didiglobal.booster.instrument.**`
 * - `**.R`
 * - `**.R$*`
 * - `BuildConfig`
 */
internal val EXCLUDES = Regex("^(((kotlin)|(android[x]?)|(com/(((google/)?android)|(google/gson)|(didiglobal/booster/instrument))))/.+)|(.+/((R[2]?($[a-z]+)?)|(BuildConfig)))$")

internal val MAIN_THREAD_ANNOTATIONS = arrayOf(
        "androidx/annotation/MainThread",
        "android/support/annotation/MainThread",
        "android/annotation/MainThread"
)

internal val UI_THREAD_ANNOTATIONS = arrayOf(
        "androidx/annotation/UiThread",
        "android/support/annotation/UiThread",
        "android/annotation/UiThread"
)

internal val WORKER_THREAD_ANNOTATIONS = arrayOf(
        "androidx/annotation/WorkerThread",
        "android/support/annotation/WorkerThread",
        "android/annotation/WorkerThread"
)

internal val BINDER_THREAD_ANNOTATIONS = arrayOf(
        "androidx/annotation/BinderThread",
        "android/support/annotation/BinderThread",
        "android/annotation/BinderThread"
)

internal val ANY_THREAD_ANNOTATIONS = arrayOf(
        "androidx/annotation/AnyThread",
        "android/support/annotation/AnyThread",
        "android/annotation/AnyThread"
)