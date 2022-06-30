package com.didiglobal.booster.task.analyser

import com.android.build.gradle.api.BaseVariant
import com.didiglobal.booster.BOOSTER
import org.gradle.api.DefaultTask
import org.gradle.api.reporting.ReportContainer
import org.gradle.api.reporting.Reporting
import org.gradle.api.reporting.SingleFileReport
import org.gradle.api.tasks.Internal

abstract class AnalysisTask<T : ReportContainer<out SingleFileReport>> : DefaultTask(), Reporting<T> {

    @get:Internal
    var variant: BaseVariant? = null

    @Internal
    final override fun getGroup(): String = BOOSTER

    @Internal
    abstract override fun getDescription(): String

    abstract fun analyse()

}