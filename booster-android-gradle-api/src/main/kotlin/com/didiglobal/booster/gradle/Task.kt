package com.didiglobal.booster.gradle

import org.gradle.api.Task

private val NOP: (String) -> Unit = {}

val Task.d: (String) -> Unit
    get() {
        val debug: (String) -> Unit = logger::debug
        return debug.takeIf { logger.isDebugEnabled } ?: NOP
    }

val Task.i: (String) -> Unit
    get() {
        val info: (String) -> Unit = logger::info
        return info.takeIf { logger.isInfoEnabled } ?: NOP
    }

val Task.w: (String) -> Unit
    get() {
        val warn: (String) -> Unit = logger::warn
        return warn.takeIf { logger.isWarnEnabled } ?: NOP
    }

val Task.e: (String) -> Unit
    get() {
        val error: (String) -> Unit = logger::error
        return error.takeIf { logger.isErrorEnabled } ?: NOP
    }
