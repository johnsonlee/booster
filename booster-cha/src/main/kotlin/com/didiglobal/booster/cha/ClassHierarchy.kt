package com.didiglobal.booster.cha

import java.util.Objects

/**
 * @author johnsonlee
 */
@Suppress("MemberVisibilityCanBePrivate")
class ClassHierarchy<ClassFile, ClassParser : ClassFileParser<ClassFile>>(
        private val classSet: ClassSet<ClassFile, ClassParser>,
        private val onClassResolveFailed: OnClassResolveFailed? = null
) : ClassFileParser<ClassFile> by classSet.parser {

    val classes: Iterable<ClassFile> = classSet

    operator fun get(name: String?): ClassFile? {
        val qn = name ?: return null
        val clazz = classSet[qn]
        if (null == clazz) {
            onClassResolveFailed?.invoke(qn)
        }
        return clazz
    }

    fun isInheritFrom(child: ClassFile, parent: ClassFile) = when {
        getClassName(child) == getClassName(parent) -> true
        isInterface(parent) -> isInheritFromInterface(child, parent)
        isInterface(child) -> getClassName(parent) == JAVA_LANG_OBJECT
        isFinal(parent) -> false
        else -> isInheritFromClass(child, parent)
    }

    fun isInheritFrom(child: String, parent: String): Boolean {
        val childClass = this[child] ?: return false
        val parentClass = this[parent] ?: return false
        return isInheritFrom(childClass, parentClass)
    }

    fun isInheritFrom(child: String, parent: ClassFile) = (!isFinal(parent)) && this[child]?.let { childClass ->
        isInheritFrom(childClass, parent)
    } ?: false

    fun isInheritFrom(child: ClassFile, parent: String) = this[parent]?.let { parentClass ->
        isInheritFrom(child, parentClass)
    } ?: false

    fun isInheritFromInterface(child: ClassFile, parent: ClassFile): Boolean {
        val interfaces = getInterfaces(child)
        if (getClassName(parent) in interfaces) {
            return true
        }

        return interfaces.any { itf ->
            this[itf]?.let {
                isInheritFromInterface(it, parent)
            } ?: false
        }
    }

    fun isInheritFromClass(child: ClassFile, parent: ClassFile): Boolean {
        val childSuperName = getSuperName(child)
        val parentName = getClassName(parent)
        if (Objects.equals(childSuperName, parentName)) {
            return true
        }

        if (null == childSuperName
                || Objects.equals(childSuperName, getSuperName(parent))
                || Objects.equals(getSuperName(parent), getClassName(child))) {
            return false
        }

        return this[childSuperName]?.let {
            isInheritFromClass(it, parent)
        } ?: false
    }

    fun getSuperClasses(clazz: ClassFile): Set<ClassFile> {
        val superName = getSuperName(clazz) ?: return emptySet()
        var parent = this[superName]

        if (superName == JAVA_LANG_OBJECT) {
            return parent?.let(::setOf) ?: throw ClassNotFoundException(superName)
        }

        val classes = mutableSetOf<ClassFile>()

        while (null != parent) {
            classes += parent
            parent = getSuperName(parent)?.let(this::get)
        }

        return classes
    }

}

typealias OnClassResolveFailed = (String) -> Unit

const val JAVA_LANG_OBJECT = "java/lang/Object"
