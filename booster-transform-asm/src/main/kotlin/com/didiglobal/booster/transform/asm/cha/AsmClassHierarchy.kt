package com.didiglobal.booster.transform.asm.cha

import com.didiglobal.booster.transform.ClassHierarchy
import com.didiglobal.booster.transform.asm.isFinal
import com.didiglobal.booster.transform.asm.isInterface
import org.objectweb.asm.tree.ClassNode
import java.util.Collections
import java.util.Objects
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents class hierarchy base on ASM
 *
 * @author johnsonlee
 */
@Suppress("MemberVisibilityCanBePrivate")
class AsmClassHierarchy(private val classSet: ClassSet) : ClassHierarchy {

    private val unresolved = ConcurrentHashMap.newKeySet<String>()

    val unresolvedClasses: Set<String>
        get() = Collections.unmodifiableSet(unresolved)

    operator fun get(name: String): ClassNode? {
        val clazz = classSet[name]
        if (null == clazz) {
            unresolved += name
        }
        return clazz
    }

    val classes: Iterable<ClassNode> = classSet

    fun isInheritFrom(child: ClassNode, parent: ClassNode) = when {
        child.name == parent.name -> true
        parent.isInterface -> isInheritFromInterface(child, parent)
        child.isInterface -> parent.name == JAVA_LANG_OBJECT
        parent.isFinal -> false
        else -> isInheritFromClass(child, parent)
    }

    override fun contains(name: String) = null != get(name)

    override fun isInheritFrom(child: String, parent: String): Boolean {
        val childClass = this[child] ?: return false
        val parentClass = this[parent] ?: return false
        return isInheritFrom(childClass, parentClass)
    }

    override fun isInheritFromInterface(child: String, parent: String): Boolean {
        val childClass = this[child] ?: return false
        val parentClass = this[parent] ?: return false
        return isInheritFromInterface(childClass, parentClass)
    }

    override fun isInheritFromClass(child: String, parent: String): Boolean {
        val childClass = this[child] ?: return false
        val parentClass = this[parent] ?: return false
        return isInheritFromClass(childClass, parentClass)
    }

    override fun getSuperClasses(clazz: String): Set<String> {
        val classNode = this[clazz] ?: return emptySet()
        return getSuperClasses(classNode).map(ClassNode::name).toSet()
    }

    override fun iterator(): Iterator<String> = object : Iterator<String> {
        val delegate = classSet.iterator()
        override fun hasNext() = delegate.hasNext()
        override fun next() = delegate.next().name
    }

    fun isInheritFrom(child: String, parent: ClassNode) = (!parent.isFinal) && this[child]?.let { childClass ->
        isInheritFrom(childClass, parent)
    } ?: false

    fun isInheritFrom(child: ClassNode, parent: String) = this[parent]?.let { parentClass ->
        isInheritFrom(child, parentClass)
    } ?: false

    fun isInheritFromInterface(child: ClassNode, parent: ClassNode): Boolean {
        if (parent.name in child.interfaces) {
            return true
        }

        return child.interfaces.any { itf ->
            this[itf]?.let {
                isInheritFromInterface(it, parent)
            } ?: false
        }
    }

    fun isInheritFromClass(child: ClassNode, parent: ClassNode): Boolean {
        if (Objects.equals(child.superName, parent.name)) {
            return true
        }

        if (null == child.superName
                || Objects.equals(child.superName, parent.superName)
                || Objects.equals(parent.superName, child.name)) {
            return false
        }

        return this[child.superName]?.let {
            isInheritFromClass(it, parent)
        } ?: false
    }

    fun getSuperClasses(clazz: ClassNode): Set<ClassNode> {
        if (clazz.superName == null) {
            return emptySet()
        }

        if (clazz.superName == JAVA_LANG_OBJECT) {
            return setOf(this[clazz.superName]!!)
        }

        val classes = mutableSetOf<ClassNode>()
        var parent = this[clazz.superName]

        while (null != parent) {
            classes += parent
            parent = parent.superName?.let {
                this[it]
            }
        }

        return classes
    }

}

const val JAVA_LANG_OBJECT = "java/lang/Object"
