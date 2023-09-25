package com.didiglobal.booster.task.compression.cwebp

import com.android.SdkConstants
import com.android.SdkConstants.FD_RES
import com.android.sdklib.BuildToolInfo
import com.didiglobal.booster.aapt2.Aapt2Container
import com.didiglobal.booster.aapt2.metadata
import com.didiglobal.booster.compression.CompressionResult
import com.didiglobal.booster.compression.task.Aapt2ActionData
import com.didiglobal.booster.gradle.*
import com.didiglobal.booster.kotlinx.CSI_RED
import com.didiglobal.booster.kotlinx.CSI_RESET
import com.didiglobal.booster.kotlinx.file
import com.didiglobal.booster.kotlinx.search
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputDirectory
import java.io.File
import java.util.stream.Collectors
import javax.xml.parsers.SAXParserFactory

/**
 * Represents a task for compiled image compression using cwebp
 *
 * @author johnsonlee
 */
@CacheableTask
internal abstract class CwebpCompressFlatImages : AbstractCwebpCompressImages() {

    @get:OutputDirectory
    val compressedRes: File
        get() = variant.project.buildDir.file("intermediates").file("compressed_${FD_RES}_cwebp", variant.name, this.name)

    override fun compress(filter: (File) -> Boolean) {
        val cwebp = this.compressor.canonicalPath
        val aapt2 = variant.buildTools.getPath(BuildToolInfo.PathId.AAPT2)
        val parser = SAXParserFactory.newInstance().newSAXParser()
        val icons = variant.mergedManifests.search {
            it.name == SdkConstants.ANDROID_MANIFEST_XML
        }.parallelStream().map { manifest ->
            LauncherIconHandler().let {
                parser.parse(manifest, it)
                it.icons
            }
        }.flatMap {
            it.parallelStream()
        }.collect(Collectors.toSet())

        // Google Play only accept APK with PNG format launcher icon
        // https://developer.android.com/topic/performance/reduce-apk-size#use-webp
        val isNotLauncherIcon: (Pair<File, Aapt2Container.Metadata>) -> Boolean = { (input, metadata) ->
            if (!icons.contains(metadata.resourceName)) true else false.also {
                ignore(metadata.resourceName, input, File(metadata.sourcePath))
            }
        }

        images().parallelStream().map {
            it to it.metadata
        }.filter(this::includes).filter(isNotLauncherIcon).filter {
            filter(File(it.second.sourcePath))
        }.map {
            val output = compressedRes.file("${it.second.resourcePath.substringBeforeLast('.')}.webp")
            Aapt2ActionData(it.first, it.second, output,
                    listOf(cwebp, "-mt", "-quiet", "-q", options.quality.toString(), it.second.sourcePath, "-o", output.canonicalPath),
                    listOf(aapt2, "compile", "-o", it.first.parent, output.canonicalPath))
        }.forEach {
            it.output.parentFile.mkdirs()
            val s0 = File(it.metadata.sourcePath).length()
            val rc = project.exec { spec ->
                spec.isIgnoreExitValue = true
                spec.commandLine = it.cmdline
            }
            when (rc.exitValue) {
                0 -> {
                    val s1 = it.output.length()
                    if (s1 > s0) {
                        results.add(CompressionResult(it.input, s0, s0, File(it.metadata.sourcePath)))
                        it.output.delete()
                    } else {
                        val rcAapt2 = project.exec { spec ->
                            spec.isIgnoreExitValue = true
                            spec.commandLine = it.aapt2
                        }
                        if (0 == rcAapt2.exitValue) {
                            results.add(CompressionResult(it.input, s0, s1, File(it.metadata.sourcePath)))
                            it.input.delete()
                        } else {
                            logger.error("${CSI_RED}Command `${it.aapt2.joinToString(" ")}` exited with non-zero value ${rc.exitValue}$CSI_RESET")
                            results.add(CompressionResult(it.input, s0, s0, File(it.metadata.sourcePath)))
                            rcAapt2.assertNormalExitValue()
                        }
                    }
                }
                else -> {
                    logger.error("${CSI_RED}Command `${it.cmdline.joinToString(" ")}` exited with non-zero value ${rc.exitValue}$CSI_RESET")
                    results.add(CompressionResult(it.input, s0, s0, File(it.metadata.sourcePath)))
                    it.output.delete()
                }
            }
        }
    }

}

