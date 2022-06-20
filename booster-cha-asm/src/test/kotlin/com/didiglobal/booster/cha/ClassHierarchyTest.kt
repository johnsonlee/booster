package com.didiglobal.booster.cha

import com.didiglobal.booster.build.AndroidSdk
import com.didiglobal.booster.cha.asm.from
import java.lang.management.ManagementFactory
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ClassHierarchyTest {

    private val threadMxBean = ManagementFactory.getThreadMXBean()

    @Test
    fun `lookup children class of Context`() {
        val androidSdk = ClassSet.from(AndroidSdk.getAndroidJar())

        // build class hierarchy
        val t1 = threadMxBean.currentThreadCpuTime
        val hierarchy = ClassHierarchy(androidSdk)
        println("t1: ${Duration.ofNanos(threadMxBean.currentThreadCpuTime - t1)}")

        // acquire subtypes of Context
        val t2 = threadMxBean.currentThreadCpuTime
        val childrenOfContext = hierarchy.getDerivedTypes("android/content/Context")
        println("t2 : ${Duration.ofNanos(threadMxBean.currentThreadCpuTime - t2)}")
        assertNotNull(childrenOfContext)
        assertTrue("Children of android/content/Context not found") {
            childrenOfContext.isNotEmpty()
        }
        println(childrenOfContext.joinToString(", ") { it.name })

        // acquire subtypes of Context
        val t3 = threadMxBean.currentThreadCpuTime
        val childrenOfThread = hierarchy.getDerivedTypes("java/lang/Thread")
        println("t3 : ${Duration.ofNanos(threadMxBean.currentThreadCpuTime - t3)}")
        println(childrenOfThread.joinToString(", ") { it.name })
    }

}