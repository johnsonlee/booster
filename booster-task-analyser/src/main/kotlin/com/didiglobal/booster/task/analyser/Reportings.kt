package com.didiglobal.booster.task.analyser

import com.android.build.gradle.api.BaseVariant
import org.gradle.api.reporting.ReportingExtension
import java.io.File

internal inline fun <reified T> T.configureReportConvention(variant: BaseVariant?)
    where T : AnalysisTask<*> {
    reports.all { report ->
        report.outputLocation.convention(project.layout.projectDirectory.file(project.provider<String> {
            val base = project.extensions.getByType(ReportingExtension::class.java).baseDir
            val path = listOfNotNull(Build.ARTIFACT, T::class.category, variant?.name, report.name, "index.${report.name}").joinToString(File.separator)
            File(base, path).absolutePath
        }))
    }
}