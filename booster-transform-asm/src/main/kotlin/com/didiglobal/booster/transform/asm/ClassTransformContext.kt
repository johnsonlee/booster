package com.didiglobal.booster.transform.asm

import com.didiglobal.booster.transform.TransformContext
import com.didiglobal.booster.transform.asm.cha.AsmClassHierarchy
import com.didiglobal.booster.transform.asm.cha.ClassSet
import com.didiglobal.booster.transform.asm.cha.ClassSetCache

internal class ClassTransformContext(private val delegate: TransformContext) : TransformContext {

    companion object {
        private val cache = ClassSetCache()
    }

    init {
        delegate.bootClasspath.forEach {
            cache.put(it).load()
        }
        delegate.compileClasspath.forEach {
            cache.put(it)
        }
        delegate.runtimeClasspath.forEach {
            cache.put(it)
        }
    }

    private val bootClassSet = ClassSet.of(delegate.bootClasspath.mapNotNull {
        cache[it]
    })

    private val compileClassSet = ClassSet.of(delegate.compileClasspath.mapNotNull {
        cache[it]
    })

    private val runtimeClassSet = ClassSet.of(delegate.runtimeClasspath.mapNotNull {
        cache[it]
    })

    override val name = delegate.name
    override val projectDir = delegate.projectDir
    override val buildDir = delegate.buildDir
    override val temporaryDir = delegate.temporaryDir
    override val reportsDir = delegate.reportsDir
    override val bootClasspath = delegate.bootClasspath
    override val compileClasspath = delegate.compileClasspath
    override val runtimeClasspath = delegate.runtimeClasspath
    override val artifacts = delegate.artifacts
    override val applicationId = delegate.applicationId
    override val originalApplicationId = delegate.originalApplicationId
    override val isDebuggable = delegate.isDebuggable
    override val isDataBindingEnabled = delegate.isDataBindingEnabled
    override val classHierarchy = AsmClassHierarchy(bootClassSet + compileClassSet)
    override fun hasProperty(name: String) = delegate.hasProperty(name)

}