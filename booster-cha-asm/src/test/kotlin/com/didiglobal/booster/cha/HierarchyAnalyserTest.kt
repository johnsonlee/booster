package com.didiglobal.booster.cha

import com.didiglobal.booster.build.AndroidSdk
import com.didiglobal.booster.cha.asm.ClassNode
import com.didiglobal.booster.cha.asm.HierarchyAnalyser
import com.didiglobal.booster.cha.asm.from
import java.lang.management.ManagementFactory
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HierarchyAnalyserTest {

    private val threadMxBean = ManagementFactory.getThreadMXBean()

    @Test
    fun `lookup children class of Context`() {
        val androidSdk = ClassSet.from(AndroidSdk.getAndroidJar())
        val t0 = threadMxBean.currentThreadCpuTime
        val graph = HierarchyAnalyser().analyse(androidSdk)
        val t1 = threadMxBean.currentThreadCpuTime
        println("t1 - t0 : ${Duration.ofNanos(t1 - t0).toMillis()}")
        val reversed = graph.reverse()
        val t2 = threadMxBean.currentThreadCpuTime
        println("t2 - t1 : ${Duration.ofNanos(t2 - t1).toMillis()}")
        val context = ClassNode("android/content/Context")
        val childrenOfContext = reversed[context]
        val t3 = threadMxBean.currentThreadCpuTime
        println("t3 - t2 : ${Duration.ofNanos(t3 - t2).toMillis()}")
        assertNotNull(childrenOfContext)
        assertTrue("Children of $context not found") {
            childrenOfContext.isNotEmpty()
        }
    }

}